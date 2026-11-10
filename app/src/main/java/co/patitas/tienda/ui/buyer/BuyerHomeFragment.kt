package co.patitas.tienda.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.databinding.FragmentBuyerHomeBinding
import co.patitas.tienda.ui.common.RoleHostActivity
import co.patitas.tienda.util.snack

/** Inicio del Comprador (C-01): banner, categorías y productos cercanos ordenados por distancia. */
class BuyerHomeFragment : Fragment() {

    private var _binding: FragmentBuyerHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBuyerHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val user = PatitasRepository.currentUser
        binding.textGreeting.text = if (user != null) {
            getString(R.string.home_greeting, user.name.substringBefore(" "))
        } else {
            getString(R.string.home_greeting_guest)
        }

        val host = requireActivity() as RoleHostActivity
        binding.boxSearch.setOnClickListener { host.selectTab(R.id.tab_catalog) }
        binding.buttonSeeAll.setOnClickListener { host.selectTab(R.id.tab_catalog) }
        binding.banner.setOnClickListener { binding.root.snack(R.string.banner_snack) }

        binding.recyclerCategories.adapter = CategoryAdapter(PatitasRepository.categories) { category ->
            CatalogFragment.pendingCategoryId = category.id
            host.selectTab(R.id.tab_catalog)
        }

        val adapter = ProductGridAdapter(
            onClick = { product ->
                startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                    .putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.id))
            },
            onAdd = { product -> CartActions.add(requireActivity(), binding.root, product) }
        )
        binding.recyclerNearby.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerNearby.adapter = adapter
        adapter.submit(PatitasRepository.catalog().take(6))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
