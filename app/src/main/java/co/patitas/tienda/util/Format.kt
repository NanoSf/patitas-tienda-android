package co.patitas.tienda.util

import java.text.NumberFormat
import java.util.Locale

/** Formatos de presentación para Colombia (es-CO). */
object Format {
    private val number: NumberFormat = NumberFormat.getIntegerInstance(Locale("es", "CO"))

    /** 142900 -> "$142.900" */
    fun money(value: Long): String = "$" + number.format(value)

    /** 41300000 -> "$41,3 M" para indicadores compactos. */
    fun moneyShort(value: Long): String = when {
        value >= 1_000_000 -> "$" + String.format(Locale("es", "CO"), "%.1f M", value / 1_000_000.0)
        value >= 1_000 -> "$" + (value / 1_000) + " mil"
        else -> money(value)
    }

    fun number(value: Int): String = number.format(value)

    fun rating(value: Double): String = String.format(Locale("es", "CO"), "%.1f", value)

    fun distance(km: Double): String = String.format(Locale("es", "CO"), "a %.1f km", km)
}
