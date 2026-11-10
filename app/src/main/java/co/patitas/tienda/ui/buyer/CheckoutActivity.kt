package co.patitas.tienda.ui.buyer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.ShippingType
import co.patitas.tienda.databinding.ActivityCheckoutBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value

/**
 * Dirección de entrega (C-04, RF-PAG-01). En esta entrega la ubicación GPS se simula;
 * en la integración se usa Fused Location Provider con el permiso solicitado en contexto.
 */
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.radioStandard.text = shippingText(ShippingType.STANDARD)
        binding.radioExpress.text = shippingText(ShippingType.EXPRESS)
        binding.radioShipping.check(
            if (PatitasRepository.selectedShipping == ShippingType.EXPRESS) R.id.radioExpress else R.id.radioStandard
        )
        binding.radioShipping.setOnCheckedChangeListener { _, checkedId ->
            PatitasRepository.selectedShipping =
                if (checkedId == R.id.radioExpress) ShippingType.EXPRESS else ShippingType.STANDARD
            updatePayButton()
        }

        binding.buttonGps.setOnClickListener { useCurrentLocation() }
        binding.buttonMyLocation.setOnClickListener { useCurrentLocation() }
        binding.buttonPay.setOnClickListener { continueToPayment() }
        updatePayButton()
    }

    private fun shippingText(type: ShippingType) =
        getString(R.string.shipping_option, type.label, Format.money(type.price), type.detail)

    private fun useCurrentLocation() {
        binding.inputAddress.editText?.setText(getString(R.string.demo_address))
        binding.inputCity.editText?.setText(getString(R.string.demo_city))
        binding.inputNeighborhood.editText?.setText(getString(R.string.demo_neighborhood))
        binding.root.snack(R.string.gps_located)
    }

    private fun updatePayButton() {
        binding.buttonPay.text = getString(R.string.action_go_pay, Format.money(PatitasRepository.cartTotal()))
    }

    private fun continueToPayment() {
        val required = getString(R.string.error_required)
        var valid = binding.inputAddress.validate(binding.inputAddress.value.length >= 5, required)
        valid = binding.inputCity.validate(binding.inputCity.value.isNotEmpty(), required) && valid
        valid = binding.inputNeighborhood.validate(binding.inputNeighborhood.value.isNotEmpty(), required) && valid
        if (!valid) return
        startActivity(Intent(this, PaymentActivity::class.java))
    }
}
