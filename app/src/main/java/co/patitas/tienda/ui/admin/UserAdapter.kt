package co.patitas.tienda.ui.admin

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import co.patitas.tienda.R
import co.patitas.tienda.data.Role
import co.patitas.tienda.data.StatusStyle
import co.patitas.tienda.data.User
import co.patitas.tienda.databinding.ItemUserBinding
import co.patitas.tienda.util.applyStatus

/** Adaptador del listado de usuarios del Administrador (correos enmascarados, Ley 1581). */
class UserAdapter(
    private val onClick: (User) -> Unit,
    private val onMore: (User, View) -> Unit
) : RecyclerView.Adapter<UserAdapter.Holder>() {

    private var items: List<User> = emptyList()

    /** Reemplaza los elementos de la lista y la vuelve a pintar. */
    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<User>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemUserBinding) : RecyclerView.ViewHolder(binding.root)

    /** Crea la vista de una fila a partir de su layout XML. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemUserBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    /** Cantidad de filas que muestra la lista. */
    override fun getItemCount() = items.size

    /** Pinta los datos del elemento en la fila (las filas se reutilizan al desplazarse). */
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val user = items[position]
        val b = holder.binding
        b.textInitials.text = user.initials
        b.textName.text = if (user.role == Role.SELLER && user.storeName.isNotBlank()) user.storeName else user.name
        b.textEmail.text = user.maskedEmail
        b.textRole.applyStatus(roleStyle(user.role))
        b.textStatus.applyStatus(user.status.style)
        b.card.setOnClickListener { onClick(user) }
        b.buttonMore.setOnClickListener { onMore(user, it) }
    }

    /** Colores del badge de cada rol. */
    private fun roleStyle(role: Role) = when (role) {
        Role.BUYER -> StatusStyle(role.label, R.color.buyer_primary, R.color.buyer_container)
        Role.SELLER -> StatusStyle(role.label, R.color.seller_primary, R.color.seller_container)
        Role.ADMIN -> StatusStyle(role.label, R.color.admin_primary, R.color.admin_container)
    }
}
