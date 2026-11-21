package co.patitas.tienda.ui.buyer

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.databinding.ActivityPaymentResultBinding
import co.patitas.tienda.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Confirmación del pago (C-06, RF-PAG-05): referencia de ePayco, medio de pago, estado de la
 * transacción y códigos de los pedidos creados en Firestore.
 */
class PaymentResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentResultBinding

    /** Infla el layout, conecta los eventos de la pantalla y carga sus datos. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textReference.text = getString(
            R.string.result_reference_orders,
            intent.getStringExtra(EXTRA_REFERENCE).orEmpty(),
            intent.getStringExtra(EXTRA_ORDERS).orEmpty()
        )
        binding.textMethod.text = getString(
            R.string.result_method_status,
            intent.getStringExtra(EXTRA_METHOD).orEmpty(),
            intent.getStringExtra(EXTRA_STATUS).orEmpty()
        )
        binding.textTotal.text = Format.money(intent.getLongExtra(EXTRA_TOTAL, 0))
        binding.textDate.text = SimpleDateFormat("dd MMM yyyy · h:mm a", Locale("es", "CO")).format(Date())

        binding.buttonOrders.setOnClickListener { goHome(R.id.tab_orders) }
        binding.buttonContinue.setOnClickListener { goHome(R.id.tab_home) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /** Manejo del botón Atrás del sistema. */
            override fun handleOnBackPressed() = goHome(R.id.tab_home)
        })
    }

    /** Vuelve al inicio del comprador en la pestaña indicada. */
    private fun goHome(tabId: Int) {
        startActivity(BuyerHomeActivity.intent(this, tabId))
        finish()
    }

    companion object {
        const val EXTRA_REFERENCE = "extra_reference"
        const val EXTRA_METHOD = "extra_method"
        const val EXTRA_TOTAL = "extra_total"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_PENDING = "extra_pending"
        const val EXTRA_ORDERS = "extra_orders"
    }
}
