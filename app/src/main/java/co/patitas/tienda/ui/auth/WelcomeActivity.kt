package co.patitas.tienda.ui.auth

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.databinding.ActivityWelcomeBinding
import co.patitas.tienda.ui.common.RoleRouter

/** Bienvenida con los tres accesos: iniciar sesión, crear cuenta y explorar como invitado. */
class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.buttonLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.buttonRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        // El invitado solo puede explorar el catálogo; se le pide sesión al agregar al carrito.
        binding.buttonGuest.setOnClickListener { RoleRouter.open(this, null) }
    }
}
