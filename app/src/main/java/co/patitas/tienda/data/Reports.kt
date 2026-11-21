package co.patitas.tienda.data

import java.util.Calendar
import java.util.Locale

/**
 * Cálculos del reporte de ventas a partir de los pedidos reales de Firestore.
 * No cuenta los pedidos cancelados.
 */
object Reports {

    const val WEEK = 0
    const val MONTH = 1
    const val YEAR = 2

    private val locale = Locale("es", "CO")

    /** Pedidos que cuentan como venta (se excluyen los cancelados). */
    private fun valid(orders: List<Order>) = orders.filter { it.status != OrderStatus.CANCELLED }

    /** Inicio de cada intervalo del periodo (7 días, 6 meses o 6 años), del más antiguo al actual. */
    private fun buckets(period: Int): List<Pair<String, Long>> {
        val result = mutableListOf<Pair<String, Long>>()
        val count = if (period == WEEK) 7 else 6
        for (i in count - 1 downTo 0) {
            val c = Calendar.getInstance(locale)
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            val label = when (period) {
                WEEK -> {
                    c.add(Calendar.DAY_OF_YEAR, -i)
                    c.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, locale).orEmpty()
                }
                MONTH -> {
                    c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, -i)
                    c.getDisplayName(Calendar.MONTH, Calendar.SHORT, locale).orEmpty()
                }
                else -> {
                    c.set(Calendar.DAY_OF_YEAR, 1); c.add(Calendar.YEAR, -i)
                    c.get(Calendar.YEAR).toString()
                }
            }
            result += label.replace(".", "").replaceFirstChar { it.uppercase() } to c.timeInMillis
        }
        return result
    }

    /** Ventas por día, mes o año según el periodo. */
    fun salesByPeriod(orders: List<Order>, period: Int): List<SalesPoint> {
        val list = valid(orders)
        val starts = buckets(period)
        return starts.mapIndexed { index, (label, start) ->
            val end = starts.getOrNull(index + 1)?.second ?: Long.MAX_VALUE
            SalesPoint(label, list.filter { it.createdAt in start until end }.sumOf { it.total })
        }
    }

    /** Número de pedidos dentro del periodo. */
    fun ordersInPeriod(orders: List<Order>, period: Int): List<Order> {
        val start = buckets(period).first().second
        return valid(orders).filter { it.createdAt >= start }
    }

    /** Ventas por categoría (suma de las líneas de cada pedido). */
    fun salesByCategory(orders: List<Order>): List<SalesPoint> =
        valid(orders).flatMap { it.items }
            .groupBy { Catalog.category(it.categoryId).name }
            .map { (name, items) -> SalesPoint(name, items.sumOf { it.price * it.quantity }) }
            .sortedByDescending { it.value }
            .take(6)

    /** Tiendas con más ventas. */
    fun topSellers(orders: List<Order>): List<SalesPoint> =
        valid(orders).groupBy { it.sellerName }
            .map { (name, list) -> SalesPoint(name, list.sumOf { it.total }) }
            .sortedByDescending { it.value }
            .take(5)

    /** Ventas del mes en curso (indicador del panel). */
    fun salesThisMonth(orders: List<Order>): Long = salesByPeriod(orders, MONTH).last().value
}
