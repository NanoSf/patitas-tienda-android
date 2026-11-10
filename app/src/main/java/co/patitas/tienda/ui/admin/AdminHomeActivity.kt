package co.patitas.tienda.ui.admin

import android.content.Intent
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.ui.common.RoleHostActivity

/** Contenedor del rol Administrador (tema gris pizarra): Panel, Usuarios, Productos y Reportes. */
class AdminHomeActivity : RoleHostActivity() {

    override val bottomMenu = R.menu.bottom_admin
    override val drawerMenu = R.menu.drawer_admin

    override fun fragmentFor(itemId: Int): Fragment? = when (itemId) {
        R.id.tab_admin_dashboard -> AdminDashboardFragment()
        R.id.tab_admin_users -> AdminUsersFragment()
        R.id.tab_admin_products -> AdminProductsFragment()
        R.id.tab_admin_reports -> AdminReportsFragment()
        else -> null
    }

    override fun onRoleDrawerItem(itemId: Int): Boolean {
        if (itemId == R.id.nav_new_user) {
            startActivity(Intent(this, UserFormActivity::class.java))
            return true
        }
        return false
    }
}
