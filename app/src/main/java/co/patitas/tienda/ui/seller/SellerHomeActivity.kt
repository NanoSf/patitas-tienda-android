package co.patitas.tienda.ui.seller

import android.content.Intent
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.ui.common.ProfileFragment
import co.patitas.tienda.ui.common.RoleHostActivity

/** Contenedor del rol Vendedor (tema terracota): Panel, Productos, Pedidos y Mi tienda. */
class SellerHomeActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.bottom_seller
    override val drawerMenu = R.menu.drawer_seller

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.tab_seller_dashboard -> SellerDashboardFragment()
        R.id.tab_seller_products -> SellerProductsFragment()
        R.id.tab_seller_orders -> SellerOrdersFragment()
        R.id.tab_seller_profile -> ProfileFragment()
        else -> null
    }

    override fun onRoleDrawerItem(itemId: Int): Boolean {
        if (itemId == R.id.nav_new_product) {
            startActivity(Intent(this, ProductFormActivity::class.java))
            return true
        }
        return false
    }
}
