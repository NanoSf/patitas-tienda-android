package co.patitas.tienda.util

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import co.patitas.tienda.data.StatusStyle
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout

/** Pinta un badge de estado: texto, color del texto y color de fondo (tint sobre bg_badge). */
fun TextView.applyStatus(style: StatusStyle) {
    text = style.label
    setTextColor(ContextCompat.getColor(context, style.fg))
    backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, style.bg))
}

fun View.snack(message: CharSequence) {
    Snackbar.make(this, message, Snackbar.LENGTH_SHORT).show()
}

fun View.snack(messageRes: Int) {
    Snackbar.make(this, messageRes, Snackbar.LENGTH_SHORT).show()
}

/** Texto del campo sin espacios al inicio ni al final. */
val TextInputLayout.value: String
    get() = editText?.text?.toString()?.trim().orEmpty()

/** Muestra u oculta el error del campo y devuelve true si es válido. */
fun TextInputLayout.validate(valid: Boolean, errorText: String): Boolean {
    error = if (valid) null else errorText
    return valid
}

fun View.visibleIf(condition: Boolean) {
    visibility = if (condition) View.VISIBLE else View.GONE
}
