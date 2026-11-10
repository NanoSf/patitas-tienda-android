package co.patitas.tienda.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.databinding.FragmentCartBinding
import co.patitas.tienda.ui.common.RoleHostActivity
import co.patitas.tienda.util.Format
import co.patitas.tienda.util.visibleIf
import com.google.android.material.snackbar.Snackbar

/**
 * Carrito de compras (C-03). Consultar, actualizar cantidades, eliminar con opción de deshacer,
 * aplicar cupón y ver el resumen con envío y descuento antes de pagar.
 */
class CartFragment : Fragment() {

    private var _binding: FragmentCartBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: CartAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = CartAdapter(
            onQuantityChanged = { refreshSummary() },
            onDelete = { item ->
                val cart = PatitasRepository.cart
                val index = cart.indexOf(item)
                cart.remove(item)
                refresh()
                Snackbar.make(binding.root, getString(R.string.cart_removed, item.product.name), Snackbar.LENGTH_LONG)
                    .setAction(R.string.action_undo) {
                        cart.add(index.coerceIn(0, cart.size), item)
                        refresh()
                    }
                    .show()
            }
        )
        binding.recyclerCart.adapter = adapter

        binding.buttonApplyCoupon.setOnClickListener {
            val code = binding.inputCoupon.editText?.text?.toString().orEmpty()
            if (PatitasRepository.applyCoupon(code)) {
                binding.inputCoupon.error = null
                binding.inputCoupon.helperText = getString(R.string.coupon_applied)
            } else {
                binding.inputCoupon.error = getString(R.string.coupon_invalid)
            }
            refreshSummary()
        }

        binding.buttonCheckout.setOnClickListener {
            if (PatitasRepository.currentUser == null) {
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
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val cart = PatitasRepository.cart
        adapter.submit(cart)
        binding.groupContent.visibleIf(cart.isNotEmpty())
        binding.empty.root.visibleIf(cart.isEmpty())
        refreshSummary()
    }

    private fun refreshSummary() {
        val repo = PatitasRepository
        val stores = repo.cartStores()
        binding.textSubtotal.text = Format.money(repo.cartSubtotal())
        binding.textShippingLabel.text = resources.getQuantityString(R.plurals.summary_shipping, stores, stores)
        binding.textShipping.text = Format.money(repo.shippingCost())
        binding.textDiscount.text = if (repo.discount > 0) "-" + Format.money(repo.discount) else Format.money(0)
        binding.textTotal.text = Format.money(repo.cartTotal())

        // Aviso de stock bajo: se reserva 15 minutos al iniciar el pago (RF-CAR-05).
        val lowStock = repo.cart.firstOrNull { it.product.stock <= 5 }
        binding.textStockNotice.visibleIf(lowStock != null)
        lowStock?.let {
            binding.textStockNotice.text = getString(R.string.cart_stock_notice, it.product.stock, it.product.name)
        }
        (activity as? BuyerHomeActivity)?.refreshCartBadge()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
