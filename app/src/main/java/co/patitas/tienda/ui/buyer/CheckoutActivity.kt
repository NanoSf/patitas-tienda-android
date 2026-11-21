package co.patitas.tienda.ui.buyer

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.CartItem
import co.patitas.tienda.data.CartRepository
import co.patitas.tienda.data.CheckoutState
import co.patitas.tienda.data.DeliveryAddress
import co.patitas.tienda.data.ShippingType
import co.patitas.tienda.databinding.ActivityCheckoutBinding
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.validate
import co.patitas.tienda.util.value
import co.patitas.tienda.util.visibleIf
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Dirección de entrega (C-04, RF-PAG-01) con geolocalización.
 *
 * 1. Pide el permiso de ubicación en contexto (solo cuando el usuario toca el botón).
 * 2. Obtiene la posición actual con el Fused Location Provider de Google Play Services.
 * 3. Convierte las coordenadas en dirección, barrio y ciudad con Geocoder (geocodificación inversa).
 * 4. Guarda dirección y coordenadas en CheckoutState para asociarlas al pedido.
 */
class CheckoutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCheckoutBinding
    private var items: List<CartItem> = emptyList()
    private var latitude: Double? = null
    private var longitude: Double? = null
    private val cancellation = CancellationTokenSource()

    /** Solicitud del permiso de ubicación precisa o aproximada. */
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) locate() else binding.root.snack(R.string.gps_permission_denied)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.radioStandard.text = shippingText(ShippingType.STANDARD)
        binding.radioExpress.text = shippingText(ShippingType.EXPRESS)
        binding.radioShipping.check(
            if (CheckoutState.shipping == ShippingType.EXPRESS) R.id.radioExpress else R.id.radioStandard
        )
        binding.radioShipping.setOnCheckedChangeListener { _, checkedId ->
            CheckoutState.shipping = if (checkedId == R.id.radioExpress) ShippingType.EXPRESS else ShippingType.STANDARD
            updatePayButton()
        }

        binding.buttonGps.setOnClickListener { useCurrentLocation() }
        binding.buttonMyLocation.setOnClickListener { useCurrentLocation() }
        binding.buttonPay.setOnClickListener { continueToPayment() }

        // Si el comprador vuelve del pago, se conserva la dirección que ya había escrito.
        CheckoutState.address?.let { address ->
            binding.inputAddress.editText?.setText(address.address)
            binding.inputCity.editText?.setText(address.city)
            binding.inputNeighborhood.editText?.setText(address.neighborhood)
            binding.inputNotes.editText?.setText(address.notes)
            if (address.latitude != null && address.longitude != null) showCoordinates(address.latitude, address.longitude)
        }

        CartRepository.live()?.observe(this) { list ->
            items = list
            updatePayButton()
        }
    }

    private fun shippingText(type: ShippingType) =
        getString(R.string.shipping_option, type.label, Format.money(type.price), type.detail)

    private fun useCurrentLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            locate()
        } else {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    /** Lee la posición actual (alta precisión) y la convierte en dirección. */
    @SuppressLint("MissingPermission")
    private fun locate() {
        binding.buttonGps.isEnabled = false
        binding.buttonGps.setText(R.string.gps_searching)
        lifecycleScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(this@CheckoutActivity)
                val location: Location? = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
                    ?: client.lastLocation.await()
                if (location == null) {
                    binding.root.snack(R.string.gps_unavailable)
                    return@launch
                }
                showCoordinates(location.latitude, location.longitude)
                val address = reverseGeocode(location.latitude, location.longitude)
                if (address != null) {
                    // thoroughfare = calle, subThoroughfare = número; si faltan se usa la línea completa.
                    val street = listOfNotNull(address.thoroughfare, address.subThoroughfare).joinToString(" # ")
                    binding.inputAddress.editText?.setText(street.ifBlank { address.getAddressLine(0).orEmpty() })
                    binding.inputCity.editText?.setText(address.locality ?: address.subAdminArea ?: address.adminArea.orEmpty())
                    binding.inputNeighborhood.editText?.setText(address.subLocality ?: address.featureName.orEmpty())
                    binding.root.snack(R.string.gps_located)
                } else {
                    binding.root.snack(R.string.gps_no_address)
                }
            } catch (e: Exception) {
                binding.root.snack(getString(R.string.gps_error, e.localizedMessage ?: ""))
            } finally {
                binding.buttonGps.isEnabled = true
                binding.buttonGps.setText(R.string.action_use_gps)
            }
        }
    }

    private fun showCoordinates(lat: Double, lng: Double) {
        latitude = lat
        longitude = lng
        binding.textCoordinates.text = getString(R.string.gps_coordinates, lat, lng)
        binding.textCoordinates.visibleIf(true)
    }

    /** Geocodificación inversa: coordenadas → dirección (API asíncrona desde Android 13). */
    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lng: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(this, Locale("es", "CO"))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                try {
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun updatePayButton() {
        binding.buttonPay.text = getString(R.string.action_go_pay, Format.money(CheckoutState.total(items)))
    }

    private fun continueToPayment() {
        val required = getString(R.string.error_required)
        var valid = binding.inputAddress.validate(binding.inputAddress.value.length >= 5, required)
        valid = binding.inputCity.validate(binding.inputCity.value.isNotEmpty(), required) && valid
        valid = binding.inputNeighborhood.validate(binding.inputNeighborhood.value.isNotEmpty(), required) && valid
        if (!valid) return
        if (items.isEmpty()) {
            binding.root.snack(R.string.cart_empty_title)
            return
        }
        CheckoutState.address = DeliveryAddress(
            address = binding.inputAddress.value,
            city = binding.inputCity.value,
            neighborhood = binding.inputNeighborhood.value,
            notes = binding.inputNotes.value,
            latitude = latitude,
            longitude = longitude
        )
        startActivity(Intent(this, PaymentActivity::class.java))
    }

    override fun onDestroy() {
        cancellation.cancel()
        super.onDestroy()
    }
}
