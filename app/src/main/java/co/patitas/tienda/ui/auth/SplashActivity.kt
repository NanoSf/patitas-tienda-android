package co.patitas.tienda.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.databinding.ActivitySplashBinding
import co.patitas.tienda.ui.common.RoleRouter

/**
 * Splash screen: muestra la marca mientras carga la app (RF-INI-01).
 * Si hay una sesión activa en memoria va directo al inicio del rol; si no, a la bienvenida.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animaciones declaradas en res/anim
        binding.imageLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_logo))
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade)
        binding.textAppName.startAnimation(slideUp)
        binding.textTagline.startAnimation(slideUp)

        handler.postDelayed({ goNext() }, SPLASH_DURATION_MS)
    }

    private fun goNext() {
        val user = PatitasRepository.currentUser
        if (user != null) {
            RoleRouter.open(this, user)
        } else {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1800L
    }
}
