package co.patitas.tienda.ui.buyer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.CartItem
import co.patitas.tienda.data.CartRepository
import co.patitas.tienda.data.CheckoutState
import co.patitas.tienda.data.EpaycoService
import co.patitas.tienda.data.OrderRepository
import co.patitas.tienda.data.Session
import co.patitas.tienda.databinding.ActivityPaymentBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.errorMessage
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Pasarela de pagos (C-05, RF-PAG-02) con ePayco.
 *
 * 1. Muestra el resumen de la compra y la dirección de entrega.
 * 2. Crea la sesión de pago en ePayco (EpaycoService.createSession).
 * 3. Abre EpaycoCheckoutActivity, donde el comprador elige el medio de pago.
 * 4. Si ePayco confirma la transacción (aceptada o pendiente), registra los pedidos en
 *    Firestore con la referencia del pago y abre la confirmación.
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private var items: List<CartItem> = emptyList()
    private var invoice = ""

    /** Recibe el resultado del checkout de ePayco. */
    private val checkout = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            setLoading(false)
            return@registerForActivityResult
        }
        val transaction = EpaycoService.Transaction(
            reference = data.getStringExtra(EpaycoCheckoutActivity.EXTRA_REFERENCE).orEmpty(),
            code = data.getIntExtra(EpaycoCheckoutActivity.EXTRA_CODE, 4),
            response = data.getStringExtra(EpaycoCheckoutActivity.EXTRA_RESPONSE).orEmpty(),
            franchise = data.getStringExtra(EpaycoCheckoutActivity.EXTRA_FRANCHISE).orEmpty(),
            amount = "",
            isTest = true
        )
        onTransaction(transaction)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        CheckoutState.address?.let { address ->
            binding.textAddress.text = getString(
                R.string.payment_address, address.address, address.neighborhood, address.city
            ) + if (address.latitude != null) "\n" + getString(R.string.gps_coordinates, address.latitude, address.longitude) else ""
        }
        binding.buttonPay.setOnClickListener { startPayment() }

        CartRepository.live()?.observe(this) { list ->
            items = list
            bindSummary()
        }
    }

    private fun bindSummary() {
        val count = CheckoutState.count(items)
        val stores = CheckoutState.stores(items)
        val total = CheckoutState.total(items)
        binding.textTotal.text = Format.money(total)
        binding.textDetail.text = getString(
            R.string.payment_detail,
            resources.getQuantityString(R.plurals.payment_products, count, count),
            resources.getQuantityString(R.plurals.payment_stores, stores, stores),
            CheckoutState.shipping.label
        )
        binding.textItems.text = buildString {
            items.forEach { appendLine("${it.quantity} × ${it.name} · ${Format.money(it.lineTotal)}") }
            appendLine(getString(R.string.order_detail_shipping, CheckoutState.shipping.label, Format.money(CheckoutState.shippingCost(items))))
            if (CheckoutState.discount > 0) append(getString(R.string.order_detail_discount, Format.money(CheckoutState.discount)))
        }.trim()
        binding.buttonPay.text = getString(R.string.action_pay_epayco, Format.money(total))
        binding.buttonPay.isEnabled = items.isNotEmpty()
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        binding.buttonPay.isEnabled = !loading && items.isNotEmpty()
        if (loading) binding.buttonPay.setText(R.string.payment_processing) else bindSummary()
    }

    /** Crea la sesión en ePayco y abre el checkout. */
    private fun startPayment() {
        val buyer = Session.user ?: return
        if (!EpaycoService.isConfigured) {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_info)
                .setTitle(R.string.epayco_not_configured_title)
                .setMessage(R.string.epayco_not_configured_message)
                .setPositiveButton(R.string.action_understood, null)
                .show()
            return
        }
        val total = CheckoutState.total(items)
        invoice = OrderRepository.newInvoice()
        val description = items.joinToString(", ") { "${it.quantity} ${it.name}" }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val sessionId = EpaycoService.createSession(total, description, invoice, buyer)
                checkout.launch(
                    Intent(this@PaymentActivity, EpaycoCheckoutActivity::class.java)
                        .putExtra(EpaycoCheckoutActivity.EXTRA_SESSION_ID, sessionId)
                )
            } catch (e: Exception) {
                setLoading(false)
                showError(getString(R.string.epayco_session_error, e.localizedMessage ?: ""))
            }
        }
    }

    /** Registra los pedidos si el pago fue aceptado o quedó pendiente. */
    private fun onTransaction(transaction: EpaycoService.Transaction) {
        if (!transaction.approved && !transaction.pending) {
            setLoading(false)
            showError(getString(R.string.epayco_rejected, transaction.response, transaction.reference))
            return
        }
        val buyer = Session.user ?: return
        val address = CheckoutState.address ?: return
        setLoading(true)
        lifecycleScope.launch {
            try {
                // Se lee el carrito del servidor para registrar exactamente lo que se pagó.
                val cart = CartRepository.fetch().ifEmpty { items }
                val total = CheckoutState.total(cart)
                val method = EpaycoService.methodLabel(transaction.franchise)
                val orders = OrderRepository.placeOrders(
                    buyer, cart, address,
                    OrderRepository.Payment(transaction.reference, method, transaction.response)
                )
                startActivity(
                    Intent(this@PaymentActivity, PaymentResultActivity::class.java)
                        .putExtra(PaymentResultActivity.EXTRA_REFERENCE, transaction.reference)
                        .putExtra(PaymentResultActivity.EXTRA_METHOD, method)
                        .putExtra(PaymentResultActivity.EXTRA_STATUS, transaction.response)
                        .putExtra(PaymentResultActivity.EXTRA_PENDING, transaction.pending)
                        .putExtra(PaymentResultActivity.EXTRA_ORDERS, orders.joinToString(", ") { it.code })
                        .putExtra(PaymentResultActivity.EXTRA_TOTAL, total)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                finish()
            } catch (e: Exception) {
                setLoading(false)
                showError(getString(R.string.order_save_error, transaction.reference, errorMessage(e)))
            }
        }
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_info)
            .setTitle(R.string.payment_title)
            .setMessage(message)
            .setPositiveButton(R.string.action_understood, null)
            .show()
    }
}
