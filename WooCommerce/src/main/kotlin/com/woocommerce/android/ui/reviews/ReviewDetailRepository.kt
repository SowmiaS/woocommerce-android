package com.woocommerce.android.ui.reviews

import com.woocommerce.android.AppConstants
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.analytics.AnalyticsTracker.Stat
import com.woocommerce.android.extensions.getCommentId
import com.woocommerce.android.model.ProductReview
import com.woocommerce.android.model.RequestResult
import com.woocommerce.android.model.RequestResult.ERROR
import com.woocommerce.android.model.RequestResult.SUCCESS
import com.woocommerce.android.model.toAppModel
import com.woocommerce.android.model.toProductReviewProductModel
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.ContinuationWrapper
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Cancellation
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Success
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T.REVIEWS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode.MAIN
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCProductAction.FETCH_SINGLE_PRODUCT
import org.wordpress.android.fluxc.generated.WCProductActionBuilder
import org.wordpress.android.fluxc.model.WCProductModel
import org.wordpress.android.fluxc.model.WCProductReviewModel
import org.wordpress.android.fluxc.model.notification.NotificationModel
import org.wordpress.android.fluxc.model.notification.NotificationModel.Subkind.STORE_REVIEW
import org.wordpress.android.fluxc.store.NotificationStore
import org.wordpress.android.fluxc.store.NotificationStore.MarkNotificationsReadPayload
import org.wordpress.android.fluxc.store.NotificationStore.OnNotificationChanged
import org.wordpress.android.fluxc.store.WCProductStore
import org.wordpress.android.fluxc.store.WCProductStore.OnProductChanged
import javax.inject.Inject

class ReviewDetailRepository @Inject constructor(
    private val dispatcher: Dispatcher,
    private val productStore: WCProductStore,
    private val notificationStore: NotificationStore,
    private val selectedSite: SelectedSite
) {
    companion object {
        private const val TAG = "ReviewDetailRepository"
    }

    private var remoteReviewId: Long = 0L
    private var remoteProductId: Long = 0L

    private var continuationProduct = ContinuationWrapper<Boolean>(REVIEWS)

    init {
        dispatcher.register(this)
    }

    fun onCleanup() {
        dispatcher.unregister(this)
    }

    suspend fun fetchProductReview(remoteId: Long): RequestResult {
        remoteReviewId = remoteId
        if (fetchProductReviewFromApi(remoteId)) {
            getProductReviewFromDb(remoteId)?.let {
                if (fetchProductByRemoteId(it.remoteProductId)) {
                    return SUCCESS
                }
            }
        }
        return ERROR
    }

    suspend fun getCachedProductReview(remoteId: Long): ProductReview? {
        return getProductReviewFromDb(remoteId)?.toAppModel()?.let { review ->
            getProductFromDb(review.remoteProductId)?.toProductReviewProductModel()?.let { product ->
                review.also { it.product = product }
            }
        }
    }

    suspend fun getCachedNotificationForReview(remoteReviewId: Long): NotificationModel? {
        return withContext(Dispatchers.IO) {
            notificationStore.getNotificationsForSite(
                site = selectedSite.get(),
                filterBySubtype = listOf(STORE_REVIEW.toString())
            ).firstOrNull { it.getCommentId() == remoteReviewId }
        }
    }

    suspend fun markNotificationAsRead(notification: NotificationModel) {
        if (!notification.read) {
            notification.read = true
            trackMarkNotificationAsReadStarted(notification)
            val result = notificationStore.markNotificationsRead(MarkNotificationsReadPayload(listOf(notification)))
            trackMarkNotificationReadResult(result)
        }
    }

    private fun trackMarkNotificationAsReadStarted(notification: NotificationModel) {
        AnalyticsTracker.track(
            Stat.REVIEW_MARK_READ,
            mapOf(
                AnalyticsTracker.KEY_ID to remoteReviewId,
                AnalyticsTracker.KEY_NOTE_ID to notification.remoteNoteId
            )
        )
    }

    private fun trackMarkNotificationReadResult(result: OnNotificationChanged) {
        if (result.isError) {
            AnalyticsTracker.track(
                Stat.REVIEW_MARK_READ_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                    AnalyticsTracker.KEY_ERROR_TYPE to result.error?.type?.toString(),
                    AnalyticsTracker.KEY_ERROR_DESC to result.error?.message
                )
            )

            WooLog.e(
                REVIEWS,
                "$TAG - Error marking review notification as read: " +
                    "${result.error?.type} - ${result.error?.message}"
            )
        } else {
            AnalyticsTracker.track(Stat.REVIEW_MARK_READ_SUCCESS)
        }
    }

    private suspend fun fetchProductByRemoteId(remoteProductId: Long): Boolean {
        this.remoteProductId = remoteProductId
        val result = continuationProduct.callAndWaitUntilTimeout(AppConstants.REQUEST_TIMEOUT) {
            val payload = WCProductStore.FetchSingleProductPayload(selectedSite.get(), remoteProductId)
            dispatcher.dispatch(WCProductActionBuilder.newFetchSingleProductAction(payload))
        }
        return when (result) {
            is Cancellation -> false
            is Success -> result.value
        }
    }

    private suspend fun fetchProductReviewFromApi(remoteId: Long): Boolean {
        val payload = WCProductStore.FetchSingleProductReviewPayload(selectedSite.get(), remoteId)
        val result = productStore.fetchSingleProductReview(payload)
        if (result.isError) {
            AnalyticsTracker.track(
                Stat.REVIEW_LOAD_FAILED,
                mapOf(
                    AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                    AnalyticsTracker.KEY_ERROR_TYPE to result.error?.type?.toString(),
                    AnalyticsTracker.KEY_ERROR_DESC to result.error?.message
                )
            )

            WooLog.e(
                REVIEWS,
                "Error fetching product review: " +
                    "${result.error?.type} - ${result.error?.message}"
            )
        } else {
            AnalyticsTracker.track(Stat.REVIEW_LOADED, mapOf(AnalyticsTracker.KEY_ID to remoteReviewId))
        }
        return !result.isError
    }

    private suspend fun getProductReviewFromDb(remoteId: Long): WCProductReviewModel? {
        return withContext(Dispatchers.IO) {
            productStore.getProductReviewByRemoteId(selectedSite.get().id, remoteId)
        }
    }

    private suspend fun getProductFromDb(remoteProductId: Long): WCProductModel? {
        return withContext(Dispatchers.IO) {
            productStore.getProductByRemoteId(selectedSite.get(), remoteProductId)
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = MAIN)
    fun onProductChanged(event: OnProductChanged) {
        if (event.causeOfChange == FETCH_SINGLE_PRODUCT && event.remoteProductId == remoteProductId) {
            if (continuationProduct.isWaiting) {
                if (event.isError) {
                    AnalyticsTracker.track(
                        Stat.REVIEW_PRODUCT_LOAD_FAILED,
                        mapOf(
                            AnalyticsTracker.KEY_ERROR_CONTEXT to this::class.java.simpleName,
                            AnalyticsTracker.KEY_ERROR_TYPE to event.error?.type?.toString(),
                            AnalyticsTracker.KEY_ERROR_DESC to event.error?.message
                        )
                    )

                    WooLog.e(
                        REVIEWS,
                        "Error fetching matching product for product review: " +
                            "${event.error?.type} - ${event.error?.message}"
                    )
                    continuationProduct.continueWith(false)
                } else {
                    AnalyticsTracker.track(
                        Stat.REVIEW_PRODUCT_LOADED,
                        mapOf(
                            AnalyticsTracker.KEY_ID to remoteProductId,
                            AnalyticsTracker.KEY_REVIEW_ID to remoteReviewId
                        )
                    )
                    continuationProduct.continueWith(true)
                }
            }
        }
    }
}
