package co.patitas.tienda.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.databinding.ActivityPaymentBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf

/**
 * Pasarela de pagos (C-05, RF-PAG-02). Muestra el formulario según el método elegido.
 * En esta entrega la aprobación se simula; en la integración los datos de la tarjeta
 * se tokenizan con el widget de Wompi y el estado llega por webhook (RF-PAG-04).
 */
class PaymentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPaymentBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaymentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        val repo = PatitasRepository
        binding.textTotal.text = Format.money(repo.cartTotal())
        binding.textDetail.text = getString(
            R.string.payment_detail, repo.cartCount(), repo.cartStores(), repo.selectedShipping.label
        )
        binding.buttonPay.text = getString(R.string.action_pay, Format.money(repo.cartTotal()))

        // Lista de bancos PSE desde res/values/arrays.xml
        binding.dropdownBank.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, resources.getStringArray(R.array.pse_banks))
        )

        binding.radioMethod.setOnCheckedChangeListener { _, checkedId -> showMethod(checkedId) }
        showMethod(binding.radioMethod.checkedRadioButtonId)
        binding.buttonPay.setOnClickListener { pay() }
    }

    private fun showMethod(checkedId: Int) {
        binding.groupCard.visibleIf(checkedId == R.id.radioCard)
        binding.groupPse.visibleIf(checkedId == R.id.radioPse)
        binding.groupNequi.visibleIf(checkedId == R.id.radioNequi)
        binding.textRedirect.visibleIf(checkedId == R.id.radioPse || checkedId == R.id.radioBancolombia)
    }

    private fun pay() {
        val methodLabel = when (binding.radioMethod.checkedRadioButtonId) {
            R.id.radioCard -> if (validateCard()) {
                getString(R.string.method_card_masked, binding.inputCardNumber.value.takeLast(4))
            } else null
            R.id.radioPse -> if (binding.inputBank.validate(binding.dropdownBank.text.isNotEmpty(), getString(R.string.error_bank))) {
                getString(R.string.method_pse)
            } else null
            R.id.radioNequi -> if (binding.inputNequiPhone.validate(binding.inputNequiPhone.value.length == 10, getString(R.string.error_phone))) {
                getString(R.string.method_nequi)
            } else null
            else -> getString(R.string.method_bancolombia)
        } ?: return

        val total = PatitasRepository.cartTotal()
        binding.progress.visibility = View.VISIBLE
        binding.buttonPay.isEnabled = false
        binding.buttonPay.setText(R.string.payment_processing)

        handler.postDelayed({
            val reference = PatitasRepository.placeOrder(methodLabel)
            startActivity(
                Intent(this, PaymentResultActivity::class.java)
                    .putExtra(PaymentResultActivity.EXTRA_REFERENCE, reference)
                    .putExtra(PaymentResultActivity.EXTRA_METHOD, methodLabel)
                    .putExtra(PaymentResultActivity.EXTRA_TOTAL, total)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }, PROCESSING_MS)
    }

    private fun validateCard(): Boolean {
        val number = binding.inputCardNumber.value.filter { it.isDigit() }
        var valid = binding.inputCardNumber.validate(number.length in 15..16, getString(R.string.error_card_number))
        valid = binding.inputCardName.validate(binding.inputCardName.value.length >= 3, getString(R.string.error_required)) && valid
        valid = binding.inputCardExpiry.validate(
            Regex("^(0[1-9]|1[0-2])/\\d{2}$").matches(binding.inputCardExpiry.value), getString(R.string.error_card_expiry)
        ) && valid
        valid = binding.inputCardCvv.validate(binding.inputCardCvv.value.length in 3..4, getString(R.string.error_card_cvv)) && valid
        return valid
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val PROCESSING_MS = 1600L
    }
}
