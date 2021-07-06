package com.woocommerce.android.ui.prefs.cardreader.tutorial

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.viewpager.widget.ViewPager
import com.woocommerce.android.R
import com.woocommerce.android.analytics.AnalyticsTracker
import com.woocommerce.android.databinding.DialogCardReaderTutorialBinding
import com.woocommerce.android.widgets.WCViewPagerIndicator
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CardReaderTutorialDialogFragment : DialogFragment(R.layout.dialog_card_reader_tutorial) {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog!!.setCanceledOnTouchOutside(false)
        dialog!!.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = DialogCardReaderTutorialBinding.bind(view)
        binding.viewPager.initViewPager(childFragmentManager)
        binding.viewPager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                binding.viewPagerIndicator.setSelectedIndicator(position)
            }
        })

        val listener = object : WCViewPagerIndicator.OnIndicatorClickedListener {
            override fun onIndicatorClicked(index: Int) {
                binding.viewPager.currentItem = index
            }
        }
        binding.viewPagerIndicator.setupFromViewPager(binding.viewPager, listener)
    }

    override fun onResume() {
        super.onResume()
        AnalyticsTracker.trackViewShown(this)
    }
}
