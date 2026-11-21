package co.patitas.tienda.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import co.patitas.tienda.R
import co.patitas.tienda.data.CartItem
import co.patitas.tienda.data.CartRepository
import co.patitas.tienda.data.CheckoutState
import co.patitas.tienda.data.Session
import co.patitas.tienda.databinding.FragmentCartBinding
import co.patitas.tienda.ui.common.RoleHostActivity
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.errorMessage
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * Carrito de compras (C-03), CRUD sobre carts/{uid}/items en Firestore:
 * consultar (en tiempo real), actualizar cantidades, eliminar con opción de deshacer,
 * aplicar cupón y ver el resumen con envío y descuento antes de pagar.
 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CartAdapter
    private var items: List<CartItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CartAdapter(
            onQuantityChanged = { item, quantity -> execute { CartRepository.setQuantity(item, quantity) } },
            onDelete = { item ->
                execute { CartRepository.remove(item) }
                Snackbar.make(binding.root, getString(R.string.cart_removed, item.name), Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) { execute { CartRepository.restore(item) } }
                    .show()
            }
        )
        binding.recyclerCart.adapter = adapter

        binding.buttonApplyCoupon.setOnClickListener {
            val code = binding.inputCoupon.editText?.text?.toString().orEmpty()
            if (CheckoutState.applyCoupon(code)) {
                binding.inputCoupon.error = null
                binding.inputCoupon.helperText = getString(R.string.coupon_applied)
            } else {
                binding.inputCoupon.error = getString(R.string.coupon_invalid)
            }
            refreshSummary()
        }

        binding.buttonCheckout.setOnClickListener {
            if (Session.isGuest) {
                CartActions.askLogin(requireActivity())
            } else {
                startActivity(Intent(requireContext(), CheckoutActivity::class.java))
            }
        }

        binding.empty.imageEmpty.setImageResource(R.drawable.ic_cart)
        binding.empty.textEmptyTitle.setText(R.string.cart_empty_title)
        binding.empty.textEmptyMessage.setText(R.string.cart_empty_message)
        binding.empty.buttonEmptyAction.setText(R.string.action_explore_catalog)
        binding.empty.buttonEmptyAction.visibleIf(true)
        binding.empty.buttonEmptyAction.setOnClickListener {
            (requireActivity() as RoleHostActivity).selectTab(R.id.tab_catalog)
        }

        val live = CartRepository.live()
        if (live == null) {
            show(emptyList())
        } else {
            live.observe(viewLifecycleOwner) { list -> show(list) }
        }
    }

    /** Ejecuta una operación del carrito y muestra el error si Firestore la rechaza. */
    private fun execute(block: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _binding?.root?.snack(requireContext().errorMessage(e))
            }
        }
    }

    private fun show(list: List<CartItem>) {
        items = list
        adapter.submit(list)
        binding.groupContent.visibleIf(list.isNotEmpty())
        binding.empty.root.visibleIf(list.isEmpty())
        refreshSummary()
    }

    private fun refreshSummary() {
        val stores = CheckoutState.stores(items)
        binding.textSubtotal.text = Format.money(CheckoutState.subtotal(items))
        binding.textShippingLabel.text = resources.getQuantityString(R.plurals.summary_shipping, stores, stores)
        binding.textShipping.text = Format.money(CheckoutState.shippingCost(items))
        val discount = CheckoutState.discount
        binding.textDiscount.text = if (discount > 0) "-" + Format.money(discount) else Format.money(0)
        binding.textTotal.text = Format.money(CheckoutState.total(items))

        // Aviso de stock bajo (RF-CAR-05).
        val lowStock = items.firstOrNull { it.stock <= 5 }
        binding.textStockNotice.visibleIf(lowStock != null)
        lowStock?.let {
            binding.textStockNotice.text = getString(R.string.cart_stock_notice, it.stock, it.name)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
