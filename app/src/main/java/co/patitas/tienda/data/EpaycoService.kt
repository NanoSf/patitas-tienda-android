package co.patitas.tienda.data

import android.util.Base64
import co.patitas.tienda.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente de la pasarela de pagos ePayco (Smart Checkout, modo pruebas).
 *
 * Flujo:
 *  1. login(): POST https://apify.epayco.co/login con las llaves pública y privada (Basic Auth)
 *     → devuelve un token JWT.
 *  2. createSession(): POST https://apify.epayco.co/payment/session/create con el token,
 *     el valor y la descripción de la compra → devuelve un sessionId.
 *  3. La app abre el checkout de ePayco (checkout-v2.js) en un WebView con ese sessionId.
 *  4. Al terminar, validate() consulta el estado real de la transacción por su referencia
 *     (ref_payco), para no confiar solo en lo que diga la página.
 *
 * Nota de seguridad: en producción los pasos 1 y 2 los hace un servidor (por ejemplo, una
 * Cloud Function) para no incluir la llave privada en la app. Este proyecto usa el plan
 * gratuito de Firebase, que no permite Cloud Functions, y llaves de PRUEBAS que no mueven dinero.
 */
object EpaycoService {

    private const val APIFY = "https://apify.epayco.co"
    private const val VALIDATION = "https://secure.epayco.co/validation/v1/reference/"

    /** URL a la que ePayco redirige al terminar; la app la intercepta en el WebView. */
    const val RESPONSE_URL = "https://patitas.app/epayco/respuesta"

    val isConfigured: Boolean
        get() = BuildConfig.EPAYCO_PUBLIC_KEY.isNotBlank() && BuildConfig.EPAYCO_PRIVATE_KEY.isNotBlank()

    /** Resultado de la transacción tal como lo reporta ePayco. */
    data class Transaction(
        val reference: String,
        /** 1 Aceptada, 2 Rechazada, 3 Pendiente, 4 Fallida (códigos x_cod_response de ePayco). */
        val code: Int,
        val response: String,
        val franchise: String,
        val amount: String,
        val isTest: Boolean
    ) {
        val approved: Boolean get() = code == 1
        val pending: Boolean get() = code == 3
    }

    /** Pasos 1 y 2: obtiene el token y crea la sesión de pago. Devuelve el sessionId. */
    suspend fun createSession(amount: Long, description: String, invoice: String, buyer: User): String =
        withContext(Dispatchers.IO) {
            val credentials = BuildConfig.EPAYCO_PUBLIC_KEY + ":" + BuildConfig.EPAYCO_PRIVATE_KEY
            val basic = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
            val login = post("$APIFY/login", "Basic $basic", JSONObject())
            val token = login.optString("token").ifEmpty {
                throw IOException("ePayco no aceptó las llaves: " + login.optString("textResponse", login.toString()))
            }

            val body = JSONObject()
                .put("checkout_version", "2")
                .put("name", "Patitas")
                .put("description", description.take(120))
                .put("invoice", invoice)
                .put("currency", "COP")
                .put("amount", amount)
                .put("taxBase", 0)
                .put("tax", 0)
                .put("taxIco", 0)
                .put("country", "CO")
                .put("lang", "ES")
                .put("response", RESPONSE_URL)
                .put("method", "GET")
                .put("extras", JSONObject().put("extra1", buyer.id))
                .put(
                    "billing", JSONObject()
                        .put("email", buyer.email)
                        .put("name", buyer.name)
                        .put("mobilePhone", buyer.phone)
                )
            val session = post("$APIFY/payment/session/create", "Bearer $token", body)
            session.optJSONObject("data")?.optString("sessionId").orEmpty().ifEmpty {
                throw IOException("No se pudo crear la sesión de pago: " + session.optString("textResponse", session.toString()))
            }
        }

    /** Paso 4: consulta el estado de la transacción por su referencia ref_payco. */
    suspend fun validate(reference: String): Transaction = withContext(Dispatchers.IO) {
        val connection = URL(VALIDATION + reference).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        val data = json.optJSONObject("data") ?: throw IOException("ePayco no devolvió datos para $reference")
        parse(data, reference)
    }

    /**
     * Interpreta la respuesta del checkout. ePayco usa los campos x_* (x_ref_payco,
     * x_cod_response, x_response...); se aceptan también variantes sin prefijo.
     */
    fun parse(data: JSONObject, fallbackReference: String = ""): Transaction {
        /** Primer campo no vacío entre varios nombres posibles. */
        fun field(vararg names: String) = names.firstNotNullOfOrNull { name ->
            data.opt(name)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        }.orEmpty()

        val reference = field("x_ref_payco", "ref_payco", "refPayco", "referencePayco").ifEmpty { fallbackReference }
        val response = field("x_response", "x_transaction_state", "response", "estado", "status")
        val code = field("x_cod_response", "x_cod_transaction_state", "cod_response", "codResponse").toIntOrNull()
            ?: when {
                response.contains("acept", true) || response.contains("aprob", true) -> 1
                response.contains("pend", true) -> 3
                response.contains("rechaz", true) -> 2
                else -> 4
            }
        return Transaction(
            reference = reference,
            code = code,
            response = response.ifEmpty { if (code == 1) "Aceptada" else "Sin respuesta" },
            franchise = field("x_franchise", "franchise", "x_bank_name"),
            amount = field("x_amount", "amount"),
            isTest = field("x_test_request").equals("TRUE", true)
        )
    }

    /** Nombre legible del medio de pago a partir del código de franquicia de ePayco. */
    fun methodLabel(franchise: String): String = when (franchise.uppercase()) {
        "VS" -> "Tarjeta Visa"
        "MC" -> "Tarjeta Mastercard"
        "AM" -> "Tarjeta American Express"
        "DC" -> "Tarjeta Diners"
        "PSE" -> "PSE"
        "NQ", "NEQUI" -> "Nequi"
        "DP", "DAVIPLATA" -> "Daviplata"
        "EF", "GA", "BA", "RS", "PR" -> "Efectivo"
        "" -> "ePayco"
        else -> "ePayco · $franchise"
    }

    /** Petición POST con cuerpo JSON y encabezado Authorization; devuelve el JSON de respuesta. */
    private fun post(url: String, authorization: String, body: JSONObject): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", authorization)
        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("Respuesta inesperada de ePayco (${connection.responseCode})")
        }
    }

    private const val TIMEOUT_MS = 20_000
}
