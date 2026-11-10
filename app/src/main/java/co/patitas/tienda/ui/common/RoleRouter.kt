package co.patitas.tienda.ui.common

import android.app.Activity
import android.content.Intent
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.User
import co.patitas.tienda.ui.admin.AdminHomeActivity
import co.patitas.tienda.ui.buyer.BuyerHomeActivity
import co.patitas.tienda.ui.seller.SellerHomeActivity

/**
 * RoleRouter: después de autenticarse, abre la pantalla inicial según el rol del usuario
 * (RF-INI-04). El invitado (user = null) entra al inicio del comprador.
 */
object RoleRouter {
    fun open(activity: Activity, user: User?) {
        val destination = when (user?.role) {
            Role.SELLER -> SellerHomeActivity::class.java
            Role.ADMIN -> AdminHomeActivity::class.java
            else -> BuyerHomeActivity::class.java
        }
        val intent = Intent(activity, destination)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(intent)
        activity.finish()
    }
}
