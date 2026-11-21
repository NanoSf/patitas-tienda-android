package co.patitas.tienda.ui.buyer

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.EpaycoService
import co.patitas.tienda.databinding.ActivityEpaycoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Checkout de ePayco dentro de la app.
 *
 * Carga en un WebView una página mínima con la librería oficial checkout-v2.js y abre el
 * checkout "onpage" con el sessionId. La página avisa a la app por medio de un puente
 * JavaScript (PatitasBridge) cuando la transacción termina (hook onResponse) o el usuario cierra
 * el checkout (onClosed). Como respaldo, también se intercepta la URL de respuesta a la que
 * ePayco redirige con el parámetro ref_payco.
 *
 * Con la referencia, la app consulta el estado real de la transacción en ePayco
 * (EpaycoService.validate) antes de devolver el resultado a PaymentActivity.
 */
class EpaycoCheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEpaycoBinding
    private var finished = false
    private var responded = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEpaycoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { confirmCancel() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmCancel()
        })

        val sessionId = intent.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val web = binding.webView
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.addJavascriptInterface(Bridge(), "PatitasBridge")
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                binding.progress.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                // URL de respuesta configurada en la sesión: trae ref_payco.
                if (url.toString().startsWith(EpaycoService.RESPONSE_URL)) {
                    val reference = url.getQueryParameter("ref_payco") ?: url.getQueryParameter("x_ref_payco")
                    if (!reference.isNullOrBlank()) validateAndFinish(reference, null)
                    return true
                }
                // Enlaces que no son web (por ejemplo, abrir la app del banco o de Nequi).
                if (url.scheme != "http" && url.scheme != "https") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (e: Exception) {
                        Log.w(TAG, "No se pudo abrir $url", e)
                    }
                    return true
                }
                return false
            }
        }
        web.loadDataWithBaseURL(BASE_URL, html(sessionId), "text/html", "UTF-8", null)
    }

    /** Página que carga checkout-v2.js y abre el checkout con los hooks hacia la app. */
    private fun html(sessionId: String) = """
        <!DOCTYPE html>
        <html lang="es"><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <script src="https://checkout.epayco.co/checkout-v2.js"></script>
        <style>body{font-family:sans-serif;background:#FAF6EE;color:#2B2622;margin:0;height:100vh;
        display:flex;align-items:center;justify-content:center;text-align:center;padding:24px}</style>
        </head><body><p id="msg">Abriendo el checkout seguro de ePayco…</p>
        <script>
          function send(fn, data){ try { PatitasBridge[fn](JSON.stringify(data || {})); } catch(e) {} }
          window.addEventListener('load', function () {
            if (typeof ePayco === 'undefined') { send('onError', {message: 'No se pudo cargar checkout-v2.js'}); return; }
            try {
              var checkout = ePayco.checkout.configure({ sessionId: '$sessionId', type: 'onpage', test: true });
              checkout.setHooks({
                onCreated: function (d) { send('onCreated', d); },
                onResponse: function (r) { send('onResponse', r); },
                onErrors: function (e) { send('onError', e); },
                onClosed: function (e) { send('onClosed', e); }
              });
              checkout.open();
            } catch (e) { send('onError', {message: String(e)}); }
          });
        </script></body></html>
    """.trimIndent()

    /** Métodos que la página llama desde JavaScript (se ejecutan en un hilo aparte). */
    private inner class Bridge {
        @JavascriptInterface
        fun onCreated(json: String) {
            Log.d(TAG, "onCreated $json")
        }

        @JavascriptInterface
        fun onResponse(json: String) {
            Log.d(TAG, "onResponse $json")
            responded = true
            runOnUiThread {
                val data = try {
                    JSONObject(json).let { it.optJSONObject("data") ?: it }
                } catch (e: Exception) {
                    JSONObject()
                }
                val parsed = EpaycoService.parse(data)
                if (parsed.reference.isNotBlank()) validateAndFinish(parsed.reference, parsed) else deliver(parsed)
            }
        }

        @JavascriptInterface
        fun onError(json: String) {
            Log.w(TAG, "onError $json")
            runOnUiThread {
                if (finished) return@runOnUiThread
                MaterialAlertDialogBuilder(this@EpaycoCheckoutActivity)
                    .setTitle(R.string.epayco_title)
                    .setMessage(getString(R.string.epayco_error, json))
                    .setPositiveButton(R.string.action_close) { _, _ -> cancel() }
                    .show()
            }
        }

        @JavascriptInterface
        fun onClosed(json: String) {
            Log.d(TAG, "onClosed $json")
            // onResponse puede llegar justo antes o después del cierre: se espera un momento.
            lifecycleScope.launch {
                delay(1500)
                if (!responded && !finished) cancel()
            }
        }
    }

    /** Consulta el estado oficial de la transacción y devuelve el resultado. */
    private fun validateAndFinish(reference: String, fallback: EpaycoService.Transaction?) {
        if (finished) return
        finished = true
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val transaction = try {
                EpaycoService.validate(reference)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo validar $reference", e)
                fallback ?: EpaycoService.Transaction(reference, 3, "Pendiente", "", "", true)
            }
            deliver(transaction)
        }
    }

    private fun deliver(transaction: EpaycoService.Transaction) {
        finished = true
        setResult(
            RESULT_OK, Intent()
                .putExtra(EXTRA_REFERENCE, transaction.reference)
                .putExtra(EXTRA_CODE, transaction.code)
                .putExtra(EXTRA_RESPONSE, transaction.response)
                .putExtra(EXTRA_FRANCHISE, transaction.franchise)
        )
        finish()
    }

    private fun confirmCancel() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.epayco_cancel_title)
            .setMessage(R.string.epayco_cancel_message)
            .setPositiveButton(R.string.epayco_cancel_yes) { _, _ -> cancel() }
            .setNegativeButton(R.string.action_continue, null)
            .show()
    }

    private fun cancel() {
        finished = true
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_REFERENCE = "extra_reference"
        const val EXTRA_CODE = "extra_code"
        const val EXTRA_RESPONSE = "extra_response"
        const val EXTRA_FRANCHISE = "extra_franchise"
        private const val BASE_URL = "https://patitas.app/"
        private const val TAG = "EpaycoCheckout"
    }
}
