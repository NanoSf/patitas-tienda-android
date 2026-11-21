package co.patitas.tienda.data

import co.patitas.tienda.R

/**
 * Categorías del catálogo. Son fijas porque de ellas dependen reglas de negocio
 * (por ejemplo, alimento y snacks exigen registro ICA), así que viven en el código.
 */
object Catalog {
    val categories = listOf(
        Category(1, "Alimento", R.drawable.ic_food, R.drawable.bg_tile_brown, requiresIca = true),
        Category(2, "Snacks", R.drawable.ic_bone, R.drawable.bg_tile_green, requiresIca = true),
        Category(3, "Rascadores", R.drawable.ic_scratcher, R.drawable.bg_tile_yellow),
        Category(4, "Camas", R.drawable.ic_bed, R.drawable.bg_tile_blue),
        Category(5, "Ropa", R.drawable.ic_hanger, R.drawable.bg_tile_red),
        Category(6, "Juguetes", R.drawable.ic_ball, R.drawable.bg_tile_green),
        Category(7, "Higiene", R.drawable.ic_drop, R.drawable.bg_tile_blue)
    )

    fun category(id: Int) = categories.firstOrNull { it.id == id } ?: categories.first()

    /** Comisión de la plataforma sobre cada venta (8 %). */
    const val COMMISSION_RATE = 0.08
}
