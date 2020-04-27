package com.woocommerce.android.ui.products

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.model.Product
import com.woocommerce.android.ui.products.ProductDetailViewModel.ProductExitEvent.ExitWPMediaPicker
import com.woocommerce.android.widgets.WCProductImageGalleryView.OnGalleryImageClickListener

class WPMediaPickerFragment : BaseProductFragment(), OnGalleryImageClickListener {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_wpmedia_picker, container, false)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        inflater.inflate(R.menu.menu_done, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_done -> {
                viewModel.onDoneButtonClicked(ExitWPMediaPicker(shouldShowDiscardDialog = false))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestAllowBackPress(): Boolean {
        return viewModel.onBackButtonClicked(ExitWPMediaPicker())
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    private fun setupObservers(viewModel: ProductDetailViewModel) {
        viewModel.event.observe(viewLifecycleOwner, Observer { event ->
            when (event) {
                is ExitWPMediaPicker -> findNavController().navigateUp()
                else -> event.isHandled = false
            }
        })
    }

    override fun getFragmentTitle() = getString(R.string.product_wpmedia_title)

    override fun onGalleryImageClicked(image: Product.Image, imageView: View) {
        val action = ProductImageViewerFragmentDirections.actionGlobalProductImageViewerFragment(
                image.id
        )
        findNavController().navigate(action)
    }
}
