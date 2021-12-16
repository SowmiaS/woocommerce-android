package com.woocommerce.android.ui.analytics

import com.woocommerce.android.extensions.formatToYYYYmmDD
import com.woocommerce.android.model.DeltaPercentage
import com.woocommerce.android.model.OrdersStat
import com.woocommerce.android.model.ProductItem
import com.woocommerce.android.model.ProductsStat
import com.woocommerce.android.model.RevenueStat
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.analytics.AnalyticsRepository.OrdersResult.OrdersError
import com.woocommerce.android.ui.analytics.AnalyticsRepository.RevenueResult.RevenueData
import com.woocommerce.android.ui.analytics.AnalyticsRepository.RevenueResult.RevenueError
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticTimePeriod
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticsDateRange
import com.woocommerce.android.ui.analytics.daterangeselector.AnalyticsDateRange.*
import com.woocommerce.android.ui.mystore.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.wordpress.android.fluxc.store.WCStatsStore.StatsGranularity
import org.wordpress.android.fluxc.store.WCStatsStore.StatsGranularity.*
import org.wordpress.android.fluxc.store.WooCommerceStore
import javax.inject.Inject
import kotlin.math.round

class AnalyticsRepository @Inject constructor(
    private val statsRepository: StatsRepository,
    private val selectedSite: SelectedSite,
    private val wooCommerceStore: WooCommerceStore,
) {
    suspend fun fetchRevenueData(
        dateRange: AnalyticsDateRange,
        selectedRange: AnalyticTimePeriod
    ): Flow<RevenueResult> =
        getGranularity(selectedRange).let {
            return getCurrentPeriodStats(dateRange, it)
                .combine(getPreviousPeriodStats(dateRange, it)) { currentPeriodRevenue, previousPeriodRevenue ->
                    if (currentPeriodRevenue.isFailure || currentPeriodRevenue.getOrNull() == null) {
                        return@combine RevenueError
                    }
                    if (previousPeriodRevenue.isFailure || previousPeriodRevenue.getOrNull() == null) {
                        return@combine RevenueError
                    }

                    val previousTotalSales = previousPeriodRevenue.getOrNull()!!.parseTotal()?.totalSales ?: 0.0
                    val previousNetRevenue = previousPeriodRevenue.getOrNull()!!.parseTotal()?.netRevenue ?: 0.0
                    val currentTotalSales = currentPeriodRevenue.getOrNull()!!.parseTotal()?.totalSales!!
                    val currentNetRevenue = currentPeriodRevenue.getOrNull()!!.parseTotal()?.netRevenue!!

                    return@combine RevenueData(
                        RevenueStat(
                            currentTotalSales,
                            calculateDeltaPercentage(previousTotalSales, currentTotalSales),
                            currentNetRevenue,
                            calculateDeltaPercentage(previousNetRevenue, currentNetRevenue),
                            getCurrencyCode()
                        )
                    )
                }
        }

    suspend fun fetchOrdersData(
        dateRange: AnalyticsDateRange,
        selectedRange: AnalyticTimePeriod
    ): Flow<OrdersResult> =
        getGranularity(selectedRange).let {
            return getCurrentPeriodStats(dateRange, it)
                .combine(getPreviousPeriodStats(dateRange, it)) { currentPeriodRevenue, previousPeriodRevenue ->
                    if (currentPeriodRevenue.isFailure || currentPeriodRevenue.getOrNull() == null) {
                        return@combine OrdersError
                    }

                    if (previousPeriodRevenue.isFailure || previousPeriodRevenue.getOrNull() == null) {
                        return@combine OrdersError
                    }

                    val previousOrdersCount = previousPeriodRevenue.getOrNull()!!.parseTotal()?.ordersCount ?: 0
                    val previousOrderValue = previousPeriodRevenue.getOrNull()!!.parseTotal()?.avgOrderValue ?: 0.0
                    val currentOrdersCount = currentPeriodRevenue.getOrNull()!!.parseTotal()?.ordersCount!!
                    val currentAvgOrderValue = currentPeriodRevenue.getOrNull()!!.parseTotal()?.avgOrderValue!!

                    return@combine OrdersResult.OrdersData(
                        OrdersStat(
                            currentOrdersCount,
                            calculateDeltaPercentage(previousOrdersCount.toDouble(), currentOrdersCount.toDouble()),
                            currentAvgOrderValue,
                            calculateDeltaPercentage(previousOrderValue, currentAvgOrderValue),
                            getCurrencyCode()
                        )
                    )
                }
        }

    suspend fun fetchProductsData(
        dateRange: AnalyticsDateRange,
        selectedRange: AnalyticTimePeriod
    ): Flow<ProductsResult> =
        getGranularity(selectedRange).let { statsGranularity: StatsGranularity ->
            return combine(
                getCurrentPeriodStats(dateRange, statsGranularity),
                getPreviousPeriodStats(dateRange, statsGranularity),
                getProductStats(dateRange, statsGranularity, TOP_PRODUCTS_LIST_SIZE)
            ) { currentRevenue, previousRevenue, products ->
                if (currentRevenue.isFailure || currentRevenue.getOrNull() == null) {
                    return@combine ProductsResult.ProductsError
                }
                if (previousRevenue.isFailure || previousRevenue.getOrNull() == null) {
                    return@combine ProductsResult.ProductsError
                }
                if (products.isFailure) {
                    return@combine ProductsResult.ProductsError
                }
                if (previousRevenue.getOrNull()!!.parseTotal()?.itemsSold == null ||
                    currentRevenue.getOrNull()!!.parseTotal()?.itemsSold == null
                ) {
                    return@combine ProductsResult.ProductsError
                }

                val previousItemsSold = previousRevenue.getOrNull()!!.parseTotal()?.itemsSold!!
                val currentItemsSold = currentRevenue.getOrNull()!!.parseTotal()?.itemsSold!!
                val productItems = products.getOrNull()?.map {
                    ProductItem(
                        it.product.name,
                        it.total,
                        it.product.getFirstImageUrl(),
                        it.quantity,
                        it.currency
                    )
                } ?: emptyList()

                return@combine ProductsResult.ProductsData(
                    ProductsStat(
                        currentItemsSold,
                        calculateDeltaPercentage(previousItemsSold.toDouble(), currentItemsSold.toDouble()),
                        productItems
                    )
                )
            }
        }

    fun getRevenueAdminPanelUrl() = getAdminPanelUrl() + ANALYTICS_REVENUE_PATH
    fun getOrdersAdminPanelUrl() = getAdminPanelUrl() + ANALYTICS_ORDERS_PATH
    fun getProductsAdminPanelUrl() = getAdminPanelUrl() + ANALYTICS_PRODUCTS_PATH

    private suspend fun getCurrentPeriodStats(dateRange: AnalyticsDateRange, granularity: StatsGranularity) =
        when (dateRange) {
            is SimpleDateRange ->
                fetchStats(dateRange.to.formatToYYYYmmDD(), dateRange.to.formatToYYYYmmDD(), granularity)
            is MultipleDateRange ->
                fetchStats(dateRange.to.from.formatToYYYYmmDD(), dateRange.to.to.formatToYYYYmmDD(), granularity)
        }

    private suspend fun getPreviousPeriodStats(dateRange: AnalyticsDateRange, granularity: StatsGranularity) =
        when (dateRange) {
            is SimpleDateRange ->
                fetchStats(dateRange.from.formatToYYYYmmDD(), dateRange.from.formatToYYYYmmDD(), granularity)
            is MultipleDateRange ->
                fetchStats(dateRange.from.from.formatToYYYYmmDD(), dateRange.from.to.formatToYYYYmmDD(), granularity)
        }

    private suspend fun getProductStats(dateRange: AnalyticsDateRange, granularity: StatsGranularity, quantity: Int) =
        when (dateRange) {
            is SimpleDateRange ->
                fetchProductLeaderboards(
                    dateRange.from.formatToYYYYmmDD(), dateRange.from.formatToYYYYmmDD(),
                    granularity, quantity
                )
            is MultipleDateRange ->
                fetchProductLeaderboards(
                    dateRange.from.from.formatToYYYYmmDD(), dateRange.from.to.formatToYYYYmmDD(),
                    granularity, quantity
                )
        }

    private fun getGranularity(selectedRange: AnalyticTimePeriod) =
        when (selectedRange) {
            AnalyticTimePeriod.TODAY, AnalyticTimePeriod.YESTERDAY -> DAYS
            AnalyticTimePeriod.LAST_WEEK, AnalyticTimePeriod.WEEK_TO_DATE -> WEEKS
            AnalyticTimePeriod.LAST_MONTH, AnalyticTimePeriod.MONTH_TO_DATE -> MONTHS
            AnalyticTimePeriod.LAST_QUARTER, AnalyticTimePeriod.QUARTER_TO_DATE -> MONTHS
            AnalyticTimePeriod.LAST_YEAR, AnalyticTimePeriod.YEAR_TO_DATE -> YEARS
        }

    private fun calculateDeltaPercentage(previousVal: Double, currentVal: Double): DeltaPercentage = when {
        previousVal <= ZERO_VALUE -> DeltaPercentage.NotExist
        currentVal <= ZERO_VALUE -> DeltaPercentage.Value((MINUS_ONE * ONE_H_PERCENT))
        else -> DeltaPercentage.Value((round((currentVal - previousVal) / previousVal) * ONE_H_PERCENT).toInt())
    }

    private suspend fun fetchStats(startDate: String, endDate: String, granularity: StatsGranularity) =
        withContext(Dispatchers.IO) {
            statsRepository.fetchRevenueStats(granularity, true, startDate, endDate)
        }

    private suspend fun fetchProductLeaderboards(
        startDate: String,
        endDate: String,
        granularity: StatsGranularity,
        quantity: Int
    ) = withContext(Dispatchers.IO) {
        statsRepository.fetchProductLeaderboards(true, granularity, quantity, startDate, endDate)
            .shareIn(
                CoroutineScope(Dispatchers.Default),
                replay = 1,
                started = SharingStarted.WhileSubscribed()
            )
    }

    private fun getCurrencyCode() = wooCommerceStore.getSiteSettings(selectedSite.get())?.currencyCode
    private fun getAdminPanelUrl() = selectedSite.getIfExists()?.adminUrl

    companion object {
        const val ANALYTICS_REVENUE_PATH = "admin.php?page=wc-admin&path=%2Fanalytics%2Frevenue"
        const val ANALYTICS_ORDERS_PATH = "admin.php?page=wc-admin&path=%2Fanalytics%2Forders"
        const val ANALYTICS_PRODUCTS_PATH = "admin.php?page=wc-admin&path=%2Fanalytics%2Fproducts"

        const val ZERO_VALUE = 0.0
        const val MINUS_ONE = -1
        const val ONE_H_PERCENT = 100

        const val TOP_PRODUCTS_LIST_SIZE = 5
    }

    sealed class RevenueResult {
        object RevenueError : RevenueResult()
        data class RevenueData(val revenueStat: RevenueStat) : RevenueResult()
    }

    sealed class OrdersResult {
        object OrdersError : OrdersResult()
        data class OrdersData(val ordersStat: OrdersStat) : OrdersResult()
    }

    sealed class ProductsResult {
        object ProductsError : ProductsResult()
        data class ProductsData(val productsStat: ProductsStat) : ProductsResult()
    }
}
