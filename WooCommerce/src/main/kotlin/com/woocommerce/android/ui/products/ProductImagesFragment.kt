package com.woocommerce.android.ui.products

import android.Manifest.permission
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.woocommerce.android.R
import com.woocommerce.android.R.style
import com.woocommerce.android.RequestCodes
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.analytics.AnalyticsTracker.Stat.PRODUCT_DETAIL_IMAGE_TAPPED
import com.woocommerce.android.extensions.takeIfNotEqualTo
import com.woocommerce.android.media.ProductImagesUtils
import com.woocommerce.android.model.Product
import com.woocommerce.android.ui.imageviewer.ImageViewerActivity
import com.woocommerce.android.ui.products.ProductDetailViewModel.ProductExitEvent.ExitImages
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T
import com.woocommerce.android.util.WooPermissionUtils
import com.woocommerce.android.widgets.WCProductImageGalleryView.OnGalleryImageClickListener
import dagger.android.support.AndroidSupportInjection
import kotlinx.android.synthetic.main.fragment_product_images.*

class ProductImagesFragment : BaseProductFragment(), OnGalleryImageClickListener {
    private var imageSourceDialog: AlertDialog? = null

    private val navArgs: ProductImagesFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        setHasOptionsMenu(true)
        return inflater.inflate(R.layout.fragment_product_images, container, false)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_done -> {
                viewModel.onDoneButtonClicked(ExitImages(shouldShowDiscardDialog = false))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestAllowBackPress(): Boolean {
        return true
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onPause() {
        super.onPause()
        imageSourceDialog?.dismiss()
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupObservers(viewModel)
        addImageButton.setOnClickListener {
            showImageSourceDialog()
        }
    }

    private fun setupObservers(viewModel: ProductDetailViewModel) {
        viewModel.event.observe(viewLifecycleOwner, Observer { event ->
            when (event) {
                is ExitImages -> findNavController().navigateUp()
                else -> event.isHandled = false
            }
        })

        viewModel.productDetailViewStateData.observe(viewLifecycleOwner) { old, new ->
            new.product?.takeIfNotEqualTo(old?.product) {
                imageGallery.showProductImages(new.product, this)
            }
            new.isProductUpdated?.takeIfNotEqualTo(old?.isProductUpdated) {
                showUpdateProductAction(it)
            }
            new.uploadingImageUris?.takeIfNotEqualTo(old?.uploadingImageUris) {
                imageGallery.setPlaceholderImageUris(it)
            }
        }

        viewModel.chooseProductImage.observe(viewLifecycleOwner, Observer {
            chooseProductImage()
        })

        viewModel.captureProductImage.observe(viewLifecycleOwner, Observer {
            captureProductImage()
        })
    }

    override fun getFragmentTitle() = getString(R.string.product_images_title)

    override fun onGalleryImageClicked(image: Product.Image, imageView: View) {
        AnalyticsTracker.track(PRODUCT_DETAIL_IMAGE_TAPPED)
        viewModel.getProduct().product?.let { product ->
            ImageViewerActivity.showProductImages(
                    this,
                    product,
                    image,
                    sharedElement = imageView,
                    enableRemoveImage = true
            )
        }
    }

    private fun showImageSourceDialog() {
        val inflater = requireActivity().layoutInflater
        val contentView = inflater.inflate(R.layout.dialog_product_image_source, imageGallery, false)
                .also {
                    it.findViewById<View>(R.id.textChooser)?.setOnClickListener {
                        viewModel.onChooseImageClicked()
                    }
                    it.findViewById<View>(R.id.textCamera)?.setOnClickListener {
                        viewModel.onCaptureImageClicked()
                    }
                }

        imageSourceDialog = AlertDialog.Builder(ContextThemeWrapper(activity, style.Woo_Dialog))
                .setView(contentView)
                .show()
    }

    private fun chooseProductImage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).also {
            it.type = "image/*"
            it.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        val chooser = Intent.createChooser(intent, null)
        activity?.startActivityFromFragment(this, chooser, RequestCodes.CHOOSE_PHOTO)
    }

    private fun captureProductImage() {
        if (requestCameraPermission()) {
            val intent = ProductImagesUtils.createCaptureImageIntent(requireActivity())
            if (intent == null) {
                uiMessageResolver.showSnack(R.string.product_images_camera_error)
                return
            }
            capturedPhotoUri = intent.getParcelableExtra(android.provider.MediaStore.EXTRA_OUTPUT)
            requireActivity().startActivityFromFragment(this, intent, RequestCodes.CAPTURE_PHOTO)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                RequestCodes.CHOOSE_PHOTO -> data?.let {
                    val uriList = ArrayList<Uri>()
                    val clipData = it.clipData
                    if (clipData != null) {
                        // handle multiple images
                        for (i in 0 until clipData.itemCount) {
                            val uri = clipData.getItemAt(i).uri
                            uriList.add(uri)
                        }
                    } else {
                        // handle single image
                        it.data?.let { uri ->
                            uriList.add(uri)
                        }
                    }
                    if (uriList.isEmpty()) {
                        WooLog.w(T.MEDIA, "Photo chooser returned empty list")
                        return
                    }
                    AnalyticsTracker.track(
                            Stat.PRODUCT_IMAGE_ADDED,
                            mapOf(AnalyticsTracker.KEY_IMAGE_SOURCE to AnalyticsTracker.IMAGE_SOURCE_DEVICE)
                    )
                    viewModel.uploadProductImages(navArgs.remoteProductId, uriList)
                }
                RequestCodes.CAPTURE_PHOTO -> capturedPhotoUri?.let { imageUri ->
                    AnalyticsTracker.track(
                            Stat.PRODUCT_IMAGE_ADDED,
                            mapOf(AnalyticsTracker.KEY_IMAGE_SOURCE to AnalyticsTracker.IMAGE_SOURCE_CAMERA)
                    )
                    val uriList = ArrayList<Uri>().also { it.add(imageUri) }
                    viewModel.uploadProductImages(navArgs.remoteProductId, uriList)
                }
                RequestCodes.PRODUCT_IMAGE_VIEWER -> data?.let { bundle ->
                    if (bundle.getBooleanExtra(ImageViewerActivity.KEY_DID_REMOVE_IMAGE, false)) {
                        viewModel.loadProduct()
                    }
                }
            }
        }
    }

    /**
     * Requests camera permissions, returns true only if camera permission is already available
     */
    private fun requestCameraPermission(): Boolean {
        if (isAdded) {
            if (WooPermissionUtils.hasCameraPermission(requireActivity())) {
                return true
            }
            requestPermissions(arrayOf(permission.CAMERA), RequestCodes.CAMERA_PERMISSION)
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (!isAdded) {
            return
        }

        val allGranted = WooPermissionUtils.setPermissionListAsked(
                requireActivity(), requestCode, permissions, grantResults, checkForAlwaysDenied = true
        )

        if (allGranted) {
            when (requestCode) {
                RequestCodes.CAMERA_PERMISSION -> {
                    captureProductImage()
                }
            }
        }
    }
}
