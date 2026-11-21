package co.patitas.tienda.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.AuthRepository
import co.patitas.tienda.databinding.ActivitySplashBinding
import co.patitas.tienda.ui.common.RoleRouter
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Splash screen: muestra la marca mientras carga la app (RF-INI-01).
 * Al mismo tiempo revisa si Firebase Authentication tiene una sesión guardada: si la hay,
 * lee el perfil en Firestore y abre el inicio del rol; si no, va a la bienvenida.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animaciones declaradas en res/anim
        binding.imageLogo.startAnimation(AnimationUtils.loadAnimation(this, R.anim.splash_logo))
        val slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade)
        binding.textAppName.startAnimation(slideUp)
        binding.textTagline.startAnimation(slideUp)

        // La animación y la consulta de la sesión corren en paralelo.
        lifecycleScope.launch {
            val session = async { AuthRepository.restore() }
            delay(SPLASH_DURATION_MS)
            val user = session.await()
            if (user != null) {
                RoleRouter.open(this@SplashActivity, user)
            } else {
                startActivity(Intent(this@SplashActivity, WelcomeActivity::class.java))
                finish()
            }
        }
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1800L
    }
}
