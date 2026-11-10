package co.patitas.tienda.ui.buyer

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.ui.common.ProfileFragment
import co.patitas.tienda.ui.common.RoleHostActivity

/** Contenedor del rol Comprador: Inicio, Catálogo, Carrito, Pedidos y Perfil. */
class BuyerHomeActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.bottom_buyer
    override val drawerMenu = R.menu.drawer_buyer

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.tab_home -> BuyerHomeFragment()
        R.id.tab_catalog -> CatalogFragment()
        R.id.tab_cart -> CartFragment()
        R.id.tab_orders -> BuyerOrdersFragment()
        R.id.tab_profile -> ProfileFragment()
        else -> null
    }

    override fun onResume() {
        super.onResume()
        refreshCartBadge()
    }

    /** Badge con la cantidad de productos sobre la pestaña Carrito. */
    fun refreshCartBadge() {
        val count = PatitasRepository.cartCount()
        val badge = binding.bottomNav.getOrCreateBadge(R.id.tab_cart)
        badge.backgroundColor = ContextCompat.getColor(this, R.color.accent_amber)
        badge.badgeTextColor = ContextCompat.getColor(this, R.color.ink_900)
        badge.number = count
        badge.isVisible = count > 0
    }

    companion object {
        fun intent(context: Context, tabId: Int): Intent =
            Intent(context, BuyerHomeActivity::class.java)
                .putExtra(EXTRA_TAB, tabId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
