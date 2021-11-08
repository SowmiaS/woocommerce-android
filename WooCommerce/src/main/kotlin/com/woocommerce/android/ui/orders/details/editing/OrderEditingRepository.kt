package com.woocommerce.android.ui.orders.details.editing

import com.woocommerce.android.tools.SelectedSite
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.Flow
import org.wordpress.android.fluxc.model.LocalOrRemoteId.LocalId
import org.wordpress.android.fluxc.model.order.OrderAddress
import org.wordpress.android.fluxc.store.OrderUpdateStore
import org.wordpress.android.fluxc.store.WCOrderStore
import javax.inject.Inject

@ViewModelScoped
@Suppress("UnusedPrivateMember")
class OrderEditingRepository @Inject constructor(
    private val orderUpdateStore: OrderUpdateStore,
    private val selectedSite: SelectedSite
) {
    suspend fun updateCustomerOrderNote(
        orderLocalId: LocalId,
        customerOrderNote: String
    ): Flow<WCOrderStore.UpdateOrderResult> {
        return orderUpdateStore.updateCustomerOrderNote(orderLocalId, selectedSite.get(), customerOrderNote)
    }

    suspend fun updateOrderAddress(
        orderLocalId: LocalId,
        orderAddress: OrderAddress
    ): Flow<WCOrderStore.UpdateOrderResult> {
        return orderUpdateStore.updateOrderAddress(orderLocalId, orderAddress)
    }

    suspend fun updateBothOrderAddresses(
        orderLocalId: LocalId,
        shippingAddress: OrderAddress.Shipping,
        billingAddress: OrderAddress.Billing
    ): Flow<WCOrderStore.UpdateOrderResult> {
        return orderUpdateStore.updateBothOrderAddresses(
            orderLocalId,
            shippingAddress,
            billingAddress
        )
    }
}
