package com.woocommerce.android.ui.feedback

import com.woocommerce.android.AppUrls
import com.woocommerce.android.BuildConfig

@Suppress("MagicNumber")
enum class SurveyType(private val untaggedUrl: String, private val milestone: Int? = null) {
    PRODUCT(AppUrls.CROWDSIGNAL_PRODUCT_SURVEY, 4),
    SHIPPING_LABELS(AppUrls.CROWDSIGNAL_SHIPPING_LABELS_SURVEY, 4),
    QUICK_ORDER(AppUrls.QUICK_ORDER_SURVEY, 1),
    MAIN(AppUrls.CROWDSIGNAL_MAIN_SURVEY);

    val url
        get() = "$untaggedUrl?$platformTag$appVersionTag$milestoneTag"

    private val milestoneTag
        get() = when (this) {
            PRODUCT -> "&product-milestone=$milestone"
            SHIPPING_LABELS -> "&shipping_label_milestone=$milestone"
            else -> ""
        }

    private val appVersionTag = "&app-version=${BuildConfig.VERSION_NAME}"

    private val platformTag = "woo-mobile-platform=android"
}
