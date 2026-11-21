package co.patitas.tienda.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import co.patitas.tienda.R
import co.patitas.tienda.data.Catalog
import co.patitas.tienda.data.Order
import co.patitas.tienda.data.OrderStatus
import co.patitas.tienda.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/** Texto del detalle de un pedido, compartido por las vistas del comprador, el vendedor y el administrador. */
object OrderDetails {

    private val time = SimpleDateFormat("dd MMM · h:mm a", Locale("es", "CO"))

    fun text(context: Context, order: Order, forSeller: Boolean): String = buildString {
        appendLine(if (forSeller) context.getString(R.string.order_detail_buyer, order.buyerName) else order.sellerName)
        appendLine()
        order.items.forEach { item ->
            appendLine("• ${item.quantity} × ${item.name} (${item.variant}) — ${Format.money(item.price * item.quantity)}")
        }
        appendLine()
        appendLine(context.getString(R.string.order_detail_subtotal, Format.money(order.subtotal)))
        appendLine(context.getString(R.string.order_detail_shipping, order.shippingType, Format.money(order.shipping)))
        if (order.discount > 0) appendLine(context.getString(R.string.order_detail_discount, Format.money(order.discount)))
        appendLine(context.getString(R.string.order_detail_total, Format.money(order.total)))
        if (forSeller) {
            val commission = (order.total * Catalog.COMMISSION_RATE).roundToLong()
            appendLine(context.getString(R.string.order_detail_commission, Format.money(commission), Format.money(order.total - commission)))
        }
        appendLine()
        appendLine(context.getString(R.string.order_detail_payment, order.paymentMethod, order.paymentStatus, order.paymentRef))
        appendLine(context.getString(R.string.order_detail_address, order.address, order.neighborhood, order.city))
        if (order.hasLocation) {
            appendLine(context.getString(R.string.order_detail_gps, order.latitude, order.longitude))
        }
        appendLine()
        appendLine(context.getString(R.string.order_detail_history))
        val steps = listOf(OrderStatus.NEW, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED)
        if (order.status == OrderStatus.CANCELLED) {
            order.history.forEach { event ->
                appendLine("● ${event.status.style.label} · ${time.format(Date(event.at))}")
            }
        } else {
            steps.forEach { step ->
                val event = order.history.lastOrNull { it.status == step }
                val mark = if (event != null || steps.indexOf(step) <= steps.indexOf(order.status)) "●" else "○"
                append("$mark ${step.style.label}")
                if (event != null) append(" · ${time.format(Date(event.at))}")
                appendLine()
            }
        }
    }.trim()

    /** Abre la ubicación de entrega en Google Maps (o en la app de mapas instalada). */
    fun openMap(context: Context, order: Order) {
        val lat = order.latitude ?: return
        val lng = order.longitude ?: return
        val label = Uri.encode(order.address)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)"))
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")))
        }
    }
}
