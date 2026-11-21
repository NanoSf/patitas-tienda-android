package co.patitas.tienda.util

import android.content.Context
import co.patitas.tienda.R
import co.patitas.tienda.data.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException

/** Traduce las excepciones de Firebase a mensajes claros en español. */
fun Context.errorMessage(error: Throwable): String = when (error) {
    is AuthRepository.AuthError -> error.message.orEmpty()
    is FirebaseAuthUserCollisionException -> getString(R.string.error_email_exists)
    is FirebaseAuthWeakPasswordException -> getString(R.string.error_password)
    is FirebaseAuthInvalidUserException,
    is FirebaseAuthInvalidCredentialsException -> getString(R.string.error_login)
    is FirebaseTooManyRequestsException -> getString(R.string.error_too_many)
    is FirebaseNetworkException -> getString(R.string.error_network)
    is FirebaseFirestoreException -> when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED -> getString(R.string.error_permission)
        FirebaseFirestoreException.Code.UNAVAILABLE -> getString(R.string.error_network)
        else -> getString(R.string.error_generic, error.code.name)
    }
    else -> error.message ?: getString(R.string.error_generic, error.javaClass.simpleName)
}
