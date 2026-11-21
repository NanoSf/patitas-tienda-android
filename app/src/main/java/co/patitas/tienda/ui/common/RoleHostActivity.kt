package co.patitas.tienda.ui.common

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.AuthRepository
import co.patitas.tienda.data.Session
import co.patitas.tienda.data.UserRepository
import co.patitas.tienda.data.UserStatus
import co.patitas.tienda.databinding.ActivityRoleHostBinding
import co.patitas.tienda.databinding.NavHeaderBinding
import co.patitas.tienda.ui.auth.WelcomeActivity
import co.patitas.tienda.util.snack
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Activity base de los tres roles. Arma la navegación común:
 *  - Bottom Navigation: cada pestaña reemplaza el Fragment del contenedor.
 *  - Drawer (NavigationView): accesos a las pestañas, ayuda, privacidad y cerrar sesión.
 * Cada rol (Comprador, Vendedor, Administrador) solo define sus menús y sus Fragments.
 * Además escucha en tiempo real el perfil del usuario: si un administrador lo suspende o lo
 * elimina mientras usa la app, se cierra la sesión.
 */
abstract class RoleHostActivity : AppCompatActivity() {

    protected lateinit var binding: ActivityRoleHostBinding

    @get:MenuRes
    protected abstract val bottomMenu: Int

    @get:MenuRes
    protected abstract val drawerMenu: Int

    /** Fragment de la pestaña seleccionada. */
    protected abstract fun fragmentFor(itemId: Int): Fragment?

    /** Opciones del Drawer propias del rol (por ejemplo, "Nuevo producto"). */
    protected open fun onRoleDrawerItem(itemId: Int): Boolean = false

    /** Infla el layout, conecta los eventos de la pantalla y carga sus datos. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoleHostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.inflateMenu(bottomMenu)
        binding.navView.inflateMenu(drawerMenu)
        bindDrawerHeader()

        binding.toolbar.setNavigationOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_notifications) binding.root.snack(R.string.notifications_empty)
            true
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item)
            true
        }
        binding.navView.setNavigationItemSelectedListener { item ->
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            onDrawerItem(item)
            true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /** Manejo del botón Atrás del sistema. */
            override fun handleOnBackPressed() {
                val firstTab = binding.bottomNav.menu.getItem(0).itemId
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    binding.bottomNav.selectedItemId != firstTab -> selectTab(firstTab)
                    else -> finish()
                }
            }
        })

        watchOwnProfile()

        val initialTab = intent.getIntExtra(EXTRA_TAB, binding.bottomNav.menu.getItem(0).itemId)
        if (savedInstanceState == null) {
            // selectedItemId solo dispara el listener si cambia la pestaña; la primera se muestra a mano.
            if (binding.bottomNav.selectedItemId == initialTab) {
                binding.bottomNav.menu.findItem(initialTab)?.let { showTab(it) }
            } else {
                selectTab(initialTab)
            }
        } else {
            binding.bottomNav.menu.findItem(binding.bottomNav.selectedItemId)?.let { binding.toolbar.title = it.title }
        }
    }

    /** Permite a los Fragments cambiar de pestaña (por ejemplo, "Ver todo" o "Ir al carrito"). */
    fun selectTab(itemId: Int) {
        binding.bottomNav.selectedItemId = itemId
    }

    /** Reemplaza el Fragment del contenedor por el de la pestaña elegida. */
    private fun showTab(item: MenuItem) {
        val fragment = fragmentFor(item.itemId) ?: return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        binding.toolbar.title = item.title
        binding.navView.setCheckedItem(item.itemId)
    }

    /** Acciones del menú lateral comunes a los tres roles. */
    private fun onDrawerItem(item: MenuItem) {
        when (item.itemId) {
            R.id.nav_logout -> requestLogout()
            R.id.nav_help -> showInfo(R.string.drawer_help, R.string.help_message)
            R.id.nav_privacy -> showInfo(R.string.drawer_privacy, R.string.privacy_message)
            else -> {
                if (binding.bottomNav.menu.findItem(item.itemId) != null) {
                    selectTab(item.itemId)
                } else {
                    onRoleDrawerItem(item.itemId)
                }
            }
        }
    }

    /** Observa el documento users/{uid}; cambios de rol o estado se aplican al instante. */
    private fun watchOwnProfile() {
        val uid = Session.user?.id ?: return
        UserRepository.watch(uid).observe(this) { profile ->
            when {
                profile == null || profile.status == UserStatus.SUSPENDED -> forceLogout()
                else -> {
                    Session.update(profile)
                    bindDrawerHeader()
                }
            }
        }
    }

    private var forcedOut = false

    /** Cierra la sesión cuando el perfil fue suspendido o eliminado. */
    private fun forceLogout() {
        if (forcedOut || Session.user == null) return
        forcedOut = true
        AuthRepository.logout()
        MaterialAlertDialogBuilder(this)
            .setIcon(R.drawable.ic_block)
            .setTitle(R.string.session_revoked_title)
            .setMessage(R.string.session_revoked_message)
            .setPositiveButton(R.string.action_understood) { _, _ -> goToWelcome() }
            .setCancelable(false)
            .show()
    }

    /** Encabezado del menú lateral con las iniciales, el nombre y el rol. */
    private fun bindDrawerHeader() {
        val header = NavHeaderBinding.bind(binding.navView.getHeaderView(0))
        val user = Session.user
        header.textInitials.text = user?.initials ?: "?"
        header.textName.text = user?.displayName ?: getString(R.string.guest_name)
        header.textEmail.text = user?.email ?: getString(R.string.guest_email)
        header.textRole.text = user?.role?.label ?: getString(R.string.guest_role)
        if (user == null) {
            binding.navView.menu.findItem(R.id.nav_logout)?.setTitle(R.string.action_login)
        }
    }

    /** Diálogo informativo (ayuda y privacidad). */
    private fun showInfo(titleRes: Int, messageRes: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setPositiveButton(R.string.action_understood, null)
            .show()
    }

    /** Cerrar sesión con confirmación; lo usan el Drawer y el botón del perfil. */
    fun requestLogout() {
        if (Session.user == null) {
            goToWelcome()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_title)
            .setMessage(R.string.logout_message)
            .setPositiveButton(R.string.drawer_logout) { _, _ ->
                AuthRepository.logout()
                goToWelcome()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    /** Vuelve a la bienvenida limpiando la pila de pantallas. */
    private fun goToWelcome() {
        startActivity(
            Intent(this, WelcomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    companion object {
        /** Pestaña inicial al abrir la Activity (por ejemplo, Pedidos después de pagar). */
        const val EXTRA_TAB = "extra_tab"
    }
}
