package co.patitas.tienda.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import co.patitas.tienda.R
import co.patitas.tienda.data.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Procesamiento de las fotos de productos.
 *
 * Como el proyecto usa el plan gratuito (Spark) de Firebase, las imágenes se guardan en
 * Firestore como texto Base64. Cada documento admite máximo 1 MiB, así que la foto se
 * reduce y se comprime en JPEG antes de guardarla:
 *  - Foto completa: lado mayor de 1024 px y máximo ~450 KB.
 *  - Miniatura: lado mayor de 320 px y máximo ~40 KB (va dentro del documento del producto).
 */
object ImageCodec {

    data class Encoded(val full: String, val thumbnail: String)

    private const val FULL_SIDE = 1024
    private const val FULL_MAX_BYTES = 450_000
    private const val THUMB_SIDE = 320
    private const val THUMB_MAX_BYTES = 40_000

    /** Caché en memoria de imágenes ya decodificadas (para no decodificar en cada scroll). */
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        /** Tamaño en bytes de cada imagen (límite de la caché). */
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Lee la foto de la cámara o la galería y genera la versión completa y la miniatura. */
    suspend fun encode(context: Context, uri: Uri): Encoded = withContext(Dispatchers.IO) {
        val bitmap = decodeUri(context, uri, FULL_SIDE) ?: throw IllegalArgumentException("No se pudo leer la imagen")
        val full = compress(scale(bitmap, FULL_SIDE), FULL_MAX_BYTES)
        val thumb = compress(scale(bitmap, THUMB_SIDE), THUMB_MAX_BYTES)
        Encoded(full, thumb)
    }

    /** Convierte el texto Base64 en Bitmap (con caché). */
    fun decode(base64: String): Bitmap? {
        if (base64.isEmpty()) return null
        val key = base64.length.toString() + ":" + base64.hashCode()
        cache.get(key)?.let { return it }
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { cache.put(key, it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** Decodifica reduciendo en potencias de 2 y corrige la rotación según los datos EXIF. */
    private fun decodeUri(context: Context, uri: Uri, targetSide: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Reduce el Bitmap para que su lado mayor no supere el indicado. */
    private fun scale(bitmap: Bitmap, side: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= side) return bitmap
        val ratio = side.toFloat() / largest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    /** Comprime en JPEG bajando la calidad hasta quedar por debajo del tamaño máximo. */
    private fun compress(bitmap: Bitmap, maxBytes: Int): String {
        var quality = 85
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bytes = out.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 25)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

/**
 * Pinta la imagen de un producto en una tarjeta.
 * Si el producto tiene foto la muestra recortada ocupando todo el recuadro; si no, muestra el
 * ícono de su categoría sobre el fondo de color, como en el diseño original.
 * El tamaño y el tinte originales del ícono se guardan en el tag de la vista porque las listas
 * (RecyclerView) reutilizan las mismas vistas para productos con y sin foto.
 */
fun ImageView.showProduct(frame: View, base64: String, categoryId: Int) {
    val original = (getTag(R.id.tag_original_icon) as? IntArray) ?: intArrayOf(
        layoutParams.width, layoutParams.height
    ).also { setTag(R.id.tag_original_icon, it) }
    val originalTint = (getTag(R.id.tag_original_tint) as? ColorStateList)
        ?: (imageTintList ?: ColorStateList.valueOf(ContextCompat.getColor(context, R.color.cream_background)))
            .also { setTag(R.id.tag_original_tint, it) }

    val category = Catalog.category(categoryId)
    frame.setBackgroundResource(category.tile)
    frame.clipToOutline = true
    val bitmap = ImageCodec.decode(base64)
    val params = layoutParams
    if (bitmap != null) {
        params.width = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams = params
        scaleType = ImageView.ScaleType.CENTER_CROP
        imageTintList = null
        setImageBitmap(bitmap)
    } else {
        params.width = original[0]
        params.height = original[1]
        layoutParams = params
        scaleType = ImageView.ScaleType.FIT_CENTER
        imageTintList = originalTint
        setImageResource(category.icon)
    }
}
