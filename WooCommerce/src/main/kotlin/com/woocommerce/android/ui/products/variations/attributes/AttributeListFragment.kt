package com.woocommerce.android.ui.products.variations.attributes

import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.FragmentAttributeListBinding
import com.woocommerce.android.extensions.colorizeTitle
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.extensions.takeIfNotEqualTo
import com.woocommerce.android.model.ProductAttribute
import com.woocommerce.android.ui.products.BaseProductFragment
import com.woocommerce.android.ui.products.ProductDetailViewModel.ProductExitEvent.ExitProductAttributeList
import com.woocommerce.android.widgets.CustomProgressDialog

class AttributeListFragment : BaseProductFragment(R.layout.fragment_attribute_list) {
    companion object {
        const val TAG: String = "AttributeListFragment"
        private const val LIST_STATE_KEY = "list_state"
        private const val ID_ATTRIBUTE_LIST = 1
    }

    private var layoutManager: LayoutManager? = null
    private var progressDialog: CustomProgressDialog? = null
    private var doneMenuItem: MenuItem? = null

    private val navArgs: AttributeListFragmentArgs by navArgs()

    private var _binding: FragmentAttributeListBinding? = null
    private val binding get() = _binding!!

    private val isGeneratingVariation
        get() = navArgs.isVariationCreation and viewModel.productDraftAttributes.isNotEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        _binding = FragmentAttributeListBinding.bind(view)

        setHasOptionsMenu(true)
        initializeViews(savedInstanceState)
        setupObservers()
    }

    override fun onResume() {
        super.onResume()
        doneMenuItem?.isVisible = isGeneratingVariation
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        layoutManager?.let {
            outState.putParcelable(LIST_STATE_KEY, it.onSaveInstanceState())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        if (navArgs.isVariationCreation) {
            doneMenuItem = menu.add(Menu.FIRST, ID_ATTRIBUTE_LIST, Menu.FIRST, R.string.done).apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                isVisible = isGeneratingVariation
            }
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        doneMenuItem?.colorizeTitle(context, R.color.woo_pink_50)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            ID_ATTRIBUTE_LIST -> {
                viewModel.onAttributeListDoneButtonClicked()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun initializeViews(savedInstanceState: Bundle?) {
        val layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        this.layoutManager = layoutManager

        savedInstanceState?.getParcelable<Parcelable>(LIST_STATE_KEY)?.let {
            layoutManager.onRestoreInstanceState(it)
        }

        binding.attributeList.layoutManager = layoutManager
        binding.attributeList.itemAnimator = null

        binding.addAttributeButton.setOnClickListener {
            if (navArgs.isVariationCreation) {
                findNavController().navigateUp()
            } else {
                viewModel.onAddAttributeButtonClick()
            }
        }
    }

    private fun setupObservers() {
        viewModel.event.observe(viewLifecycleOwner, Observer { event ->
            when (event) {
                is ExitProductAttributeList -> onExitProductAttributeList(event.variationCreated)
                else -> event.isHandled = false
            }
        })

        viewModel.attributeList.observe(viewLifecycleOwner, Observer {
            showAttributes(it)
        })

        viewModel.attributeListViewStateData.observe(viewLifecycleOwner) { old, new ->
            new.isCreatingVariationDialogShown?.takeIfNotEqualTo(old?.isCreatingVariationDialogShown) {
                showProgressDialog(it)
            }
        }

        viewModel.loadProductDraftAttributes()
    }

    override fun getFragmentTitle() = getString(R.string.product_variation_attributes)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onRequestAllowBackPress(): Boolean {
        viewModel.onBackButtonClicked(ExitProductAttributeList())
        return false
    }

    private fun onExitProductAttributeList(variationCreated: Boolean) {
        if (variationCreated) {
            AttributeListFragmentDirections.actionAttributeListFragmentToProductDetailFragment()
                .apply { findNavController().navigateSafely(this) }
        } else {
            findNavController().navigateUp()
        }
    }

    private fun showAttributes(attributes: List<ProductAttribute>) {
        val adapter: AttributeListAdapter
        if (binding.attributeList.adapter == null) {
            adapter = AttributeListAdapter { attributeId, attributeName ->
                viewModel.onAttributeListItemClick(attributeId, attributeName, navArgs.isVariationCreation)
            }
            binding.attributeList.adapter = adapter
        } else {
            adapter = binding.attributeList.adapter as AttributeListAdapter
        }

        adapter.refreshAttributeList(attributes)
    }

    private fun showProgressDialog(show: Boolean) {
        if (show) {
            hideProgressDialog()
            progressDialog = CustomProgressDialog.show(
                getString(R.string.variation_create_dialog_title),
                getString(R.string.product_update_dialog_message)
            ).also { it.show(parentFragmentManager, CustomProgressDialog.TAG) }
            progressDialog?.isCancelable = false
        } else {
            hideProgressDialog()
        }
    }

    private fun hideProgressDialog() {
        progressDialog?.dismiss()
        progressDialog = null
    }
}
