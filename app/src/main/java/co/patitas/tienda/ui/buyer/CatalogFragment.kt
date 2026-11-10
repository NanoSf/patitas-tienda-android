package co.patitas.tienda.ui.buyer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import co.patitas.tienda.R
import co.patitas.tienda.data.PatitasRepository
import co.patitas.tienda.data.Species
import co.patitas.tienda.databinding.FragmentCatalogBinding
import co.patitas.tienda.util.snack
import co.patitas.tienda.util.visibleIf
import com.google.android.material.chip.Chip

/** Catálogo con búsqueda por texto y filtros por especie y categoría (RF-INI-02, RF-INI-03). */
class CatalogFragment : Fragment() {

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: ProductGridAdapter
    private var selectedCategoryId: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = ProductGridAdapter(
            onClick = { product ->
                startActivity(Intent(requireContext(), ProductDetailActivity::class.java)
                    .putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, product.id))
            },
            onAdd = { product -> CartActions.add(requireActivity(), binding.root, product) }
        )
        binding.recyclerProducts.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.recyclerProducts.adapter = adapter

        // Una Chip por categoría, creada en tiempo de ejecución con el estilo del tema (chipStyle).
        selectedCategoryId = pendingCategoryId
        pendingCategoryId = null
        PatitasRepository.categories.forEach { category ->
            val chip = Chip(requireContext()).apply {
                id = View.generateViewId()
                text = category.name
                isCheckable = true
                isChecked = category.id == selectedCategoryId
                setOnClickListener {
                    selectedCategoryId = if (isChecked) category.id else null
                    refresh()
                }
            }
            binding.chipGroupCategories.addView(chip)
        }

        binding.chipGroupSpecies.setOnCheckedStateChangeListener { _, _ -> refresh() }
        binding.inputSearch.editText?.doAfterTextChanged { refresh() }
        binding.inputSearch.setEndIconOnClickListener { binding.root.snack(R.string.search_by_image) }

        binding.empty.textEmptyTitle.setText(R.string.catalog_empty_title)
        binding.empty.textEmptyMessage.setText(R.string.catalog_empty_message)
        binding.empty.imageEmpty.setImageResource(R.drawable.ic_search)
        refresh()
    }

    private fun refresh() {
        val species = when (binding.chipGroupSpecies.checkedChipId) {
            R.id.chipDog -> Species.DOG
            R.id.chipCat -> Species.CAT
            else -> null
        }
        val query = binding.inputSearch.editText?.text?.toString().orEmpty()
        val results = PatitasRepository.catalog(query, selectedCategoryId, species)
        adapter.submit(results)
        binding.textResults.text = resources.getQuantityString(R.plurals.catalog_results, results.size, results.size)
        binding.empty.root.visibleIf(results.isEmpty())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Categoría elegida en el inicio; el catálogo la aplica al abrirse. */
        var pendingCategoryId: Int? = null
    }
}
