package com.woocommerce.android.ui.orders.creation

import android.os.Bundle
import android.view.View
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.viewModels
import androidx.hilt.navigation.fragment.hiltNavGraphViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.textview.MaterialTextView
import com.woocommerce.android.R
import com.woocommerce.android.databinding.FragmentOrderCreationFormBinding
import com.woocommerce.android.extensions.handleDialogResult
import com.woocommerce.android.extensions.navigateSafely
import com.woocommerce.android.model.Order
import com.woocommerce.android.ui.base.BaseFragment
import com.woocommerce.android.ui.orders.OrderNavigationTarget.ViewOrderStatusSelector
import com.woocommerce.android.ui.orders.creation.views.OrderCreationSectionView
import com.woocommerce.android.ui.orders.creation.views.OrderCreationSectionView.AddButton
import com.woocommerce.android.ui.orders.details.OrderDetailViewModel.OrderStatusUpdateSource
import com.woocommerce.android.ui.orders.details.OrderStatusSelectorDialog.Companion.KEY_ORDER_STATUS_RESULT
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OrderCreationFormFragment : BaseFragment(R.layout.fragment_order_creation_form) {
    private val sharedViewModel by hiltNavGraphViewModels<OrderCreationViewModel>(R.id.nav_graph_order_creations)
    private val formViewModel by viewModels<OrderCreationFormViewModel>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        with(FragmentOrderCreationFormBinding.bind(view)) {
            setupObserversWith(this)
            setupHandleResults()
            initView()
        }
    }

    private fun FragmentOrderCreationFormBinding.initView() {
        orderStatusView.customizeViewBehavior(
            displayOrderNumber = false,
            editActionAsText = true,
            customEditClickListener = {
                sharedViewModel.orderStatusData.value?.let {
                    formViewModel.onEditOrderStatusClicked(it)
                }
            }
        )
        notesSection.setAddButtons(
            listOf(
                AddButton(
                    text = getString(R.string.order_creation_add_customer_note),
                    onClickListener = {
                        formViewModel.onCustomerNoteClicked()
                    }
                )
            )
        )
        notesSection.setOnEditButtonClicked {
            formViewModel.onCustomerNoteClicked()
        }
    }

    private fun setupObserversWith(binding: FragmentOrderCreationFormBinding) {
        sharedViewModel.orderDraftData.observe(viewLifecycleOwner) { _, newOrderData ->
            binding.orderStatusView.updateOrder(newOrderData)
            bindNotesSection(binding.notesSection, newOrderData.customerNote)
        }

        sharedViewModel.orderStatusData.observe(viewLifecycleOwner) {
            binding.orderStatusView.updateStatus(it)
        }

        formViewModel.event.observe(viewLifecycleOwner, ::handleViewModelEvents)
    }

    private fun bindNotesSection(notesSection: OrderCreationSectionView, customerNote: String) {
        customerNote.takeIf { it.isNotBlank() }
            ?.let {
                val textView = MaterialTextView(requireContext())
                TextViewCompat.setTextAppearance(textView, R.style.TextAppearance_Woo_Subtitle1)
                textView.text = it
                textView
            }
            .let {
                notesSection.updateContent(it)
            }
    }

    private fun setupHandleResults() {
        handleDialogResult<OrderStatusUpdateSource>(
            key = KEY_ORDER_STATUS_RESULT,
            entryId = R.id.orderCreationFragment
        ) { sharedViewModel.onOrderStatusChanged(Order.Status.fromValue(it.newStatus)) }
    }

    private fun handleViewModelEvents(event: Event) {
        when (event) {
            is OrderCreationNavigationTarget -> OrderCreationNavigator.navigate(this, event)
            is ViewOrderStatusSelector ->
                OrderCreationFormFragmentDirections
                    .actionOrderCreationFragmentToOrderStatusSelectorDialog(
                        currentStatus = event.currentStatus,
                        orderStatusList = event.orderStatusList
                    ).let { findNavController().navigateSafely(it) }
        }
    }

    override fun getFragmentTitle() = getString(R.string.order_creation_fragment_title)
}
