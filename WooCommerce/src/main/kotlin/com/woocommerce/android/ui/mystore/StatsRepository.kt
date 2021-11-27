package com.woocommerce.android.ui.mystore

import com.woocommerce.android.AppConstants
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.util.ContinuationWrapper
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Cancellation
import com.woocommerce.android.util.ContinuationWrapper.ContinuationResult.Success
import com.woocommerce.android.util.WooLog
import com.woocommerce.android.util.WooLog.T.DASHBOARD
import kotlinx.coroutines.withTimeoutOrNull
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.action.WCStatsAction.FETCH_REVENUE_STATS
import org.wordpress.android.fluxc.generated.WCStatsActionBuilder
import org.wordpress.android.fluxc.model.WCRevenueStatsModel
import org.wordpress.android.fluxc.model.leaderboards.WCTopPerformerProductModel
import org.wordpress.android.fluxc.store.WCLeaderboardsStore
import org.wordpress.android.fluxc.store.WCOrderStore
import org.wordpress.android.fluxc.store.WCStatsStore
import org.wordpress.android.fluxc.store.WCStatsStore.*
import javax.inject.Inject

class StatsRepository @Inject constructor(
    private val selectedSite: SelectedSite,
    private val dispatcher: Dispatcher,
    private val wcStatsStore: WCStatsStore,
    @Suppress("UnusedPrivateMember", "Required to ensure the WCOrderStore is initialized!")
    private val wcOrderStore: WCOrderStore,
    private val wcLeaderboardsStore: WCLeaderboardsStore
) {
    companion object {
        private val TAG = MyStorePresenter::class.java
    }

    private val continuationRevenueStats = ContinuationWrapper<Result<WCRevenueStatsModel?>>(DASHBOARD)
    private lateinit var lastRevenueStatsGranularity: StatsGranularity

    fun init() {
        dispatcher.register(this)
    }

    fun onCleanup() {
        dispatcher.unregister(this)
    }

    suspend fun fetchRevenueStats(granularity: StatsGranularity, forced: Boolean): Result<WCRevenueStatsModel?> {
        lastRevenueStatsGranularity = granularity
        val result = continuationRevenueStats.callAndWait {
            val statsPayload = FetchRevenueStatsPayload(selectedSite.get(), granularity, forced = forced)
            dispatcher.dispatch(WCStatsActionBuilder.newFetchRevenueStatsAction(statsPayload))
        }

        return when (result) {
            is Cancellation -> Result.failure(result.exception)
            is Success -> result.value
        }
    }

    suspend fun fetchVisitorStats(granularity: StatsGranularity, forced: Boolean): Result<Map<String, Int>> {
        val visitsPayload = FetchNewVisitorStatsPayload(selectedSite.get(), granularity, forced)
        val result = wcStatsStore.fetchNewVisitorStats(visitsPayload)

        return if (!result.isError) {
            val visitorStats = wcStatsStore.getNewVisitorStats(
                selectedSite.get(), result.granularity, result.quantity, result.date, result.isCustomField
            )
            Result.success(visitorStats)
        } else {
            val errorMessage = result?.error?.message ?: "Timeout"
            WooLog.e(
                DASHBOARD,
                "$TAG - Error fetching visitor stats: $errorMessage"
            )
            Result.failure(Exception(errorMessage))
        }
    }

    suspend fun fetchProductLeaderboards(granularity: StatsGranularity, quantity: Int, forced: Boolean):
        Result<List<WCTopPerformerProductModel>> {
        return when (forced) {
            true -> wcLeaderboardsStore.fetchProductLeaderboards(
                site = selectedSite.get(),
                unit = granularity,
                quantity = quantity
            )
            false -> wcLeaderboardsStore.fetchCachedProductLeaderboards(
                site = selectedSite.get(),
                unit = granularity
            )
        }.let { result ->
            val model = result.model
            if (result.isError || model == null) {
                Result.failure(Exception(result.error?.message.orEmpty()))
            } else {
                Result.success(model)
            }
        }
    }

    suspend fun checkIfStoreHasNoOrders(): Result<Boolean> {
        val result = withTimeoutOrNull(AppConstants.REQUEST_TIMEOUT) {
            wcOrderStore.fetchHasOrders(selectedSite.get(), status = null)
        }
        return if (result?.isError == false) {
            val hasNoOrders = result.rowsAffected == 0
            Result.success(hasNoOrders)
        } else {
            val errorMessage = result?.error?.message ?: "Timeout"
            WooLog.e(
                DASHBOARD,
                "$TAG - Error fetching whether orders exist: $errorMessage"
            )

            Result.failure(Exception(errorMessage))
        }
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onWCRevenueStatsChanged(event: OnWCRevenueStatsChanged) {
        if (event.causeOfChange == FETCH_REVENUE_STATS && event.granularity == lastRevenueStatsGranularity) {
            if (event.isError) {
                WooLog.e(DASHBOARD, "$TAG - Error fetching stats: ${event.error.message}")
                // display a different error snackbar if the error type is not "plugin not active", since
                // this error is already being handled by the activity class
                val exception = StatsException(
                    error = event.error
                )
                continuationRevenueStats.continueWith(Result.failure(exception))
            } else {
                val revenueStatsModel = wcStatsStore.getRawRevenueStats(
                    selectedSite.get(), event.granularity, event.startDate!!, event.endDate!!
                )
                continuationRevenueStats.continueWith(Result.success(revenueStatsModel))
            }
        }
    }

    data class StatsException(val error: OrderStatsError?) : Exception()
}
