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

/** Confirmación del pago aprobado (C-06, RF-PAG-05). */
class PaymentResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textReference.text = intent.getStringExtra(EXTRA_REFERENCE)
        binding.textMethod.text = intent.getStringExtra(EXTRA_METHOD)
        binding.textTotal.text = Format.money(intent.getLongExtra(EXTRA_TOTAL, 0))
        binding.textDate.text = SimpleDateFormat("dd MMM yyyy · h:mm a", Locale("es", "CO")).format(Date())

        binding.buttonOrders.setOnClickListener { goHome(R.id.tab_orders) }
        binding.buttonContinue.setOnClickListener { goHome(R.id.tab_home) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome(R.id.tab_home)
        })
    }

    private fun goHome(tabId: Int) {
        startActivity(BuyerHomeActivity.intent(this, tabId))
        finish()
    }

    companion object {
        const val EXTRA_REFERENCE = "extra_reference"
        const val EXTRA_METHOD = "extra_method"
        const val EXTRA_TOTAL = "extra_total"
    }
}
