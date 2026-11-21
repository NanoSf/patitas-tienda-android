package co.patitas.tienda.ui.buyer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.CartItem
import co.patitas.tienda.data.CartRepository
import co.patitas.tienda.data.CheckoutState
import co.patitas.tienda.ui.common.ProfileFragment
import co.patitas.tienda.ui.common.RoleHostActivity

/**
 * Contenedor del rol Comprador: Inicio, Catálogo, Carrito, Pedidos y Perfil.
 * Observa el carrito en Firestore para mantener actualizado el badge de la pestaña Carrito.
 */
class BuyerHomeActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.bottom_buyer
    override val drawerMenu = R.menu.drawer_buyer

    /** Fragment que corresponde a cada pestaña del Bottom Navigation de este rol. */
    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.tab_home -> BuyerHomeFragment()
        R.id.tab_catalog -> CatalogFragment()
        R.id.tab_cart -> CartFragment()
        R.id.tab_orders -> BuyerOrdersFragment()
        R.id.tab_profile -> ProfileFragment()
        else -> null
    }

    /** Infla el layout, conecta los eventos de la pantalla y carga sus datos. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CartRepository.live()?.observe(this) { items -> refreshCartBadge(items) }
    }

    /** Badge con la cantidad de productos sobre la pestaña Carrito. */
    private fun refreshCartBadge(items: List<CartItem>) {
        val count = CheckoutState.count(items)
        val badge = binding.bottomNav.getOrCreateBadge(R.id.tab_cart)
        badge.backgroundColor = ContextCompat.getColor(this, R.color.accent_amber)
        badge.badgeTextColor = ContextCompat.getColor(this, R.color.ink_900)
        badge.number = count
        badge.isVisible = count > 0
    }

    companion object {
        /** Intent para abrir el inicio del comprador en una pestaña específica. */
        fun intent(context: Context, tabId: Int): Intent =
            Intent(context, BuyerHomeActivity::class.java)
                .putExtra(EXTRA_TAB, tabId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
