package com.woocommerce.android.util

import android.content.Context
import android.util.Log
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import javax.inject.Inject

class DynamicFeature @Inject constructor(private val appContext: Context) {
    fun installModule(moduleName: String) {
        appContext.applicationContext?.let { context ->
            val splitInstallManager = SplitInstallManagerFactory.create(
                context
//                ,
//                context.getExternalFilesDir("local_testing")
            )
            val request = SplitInstallRequest.newBuilder()
                .addModule(moduleName)
                .build()

            var sessionId: Int = 0
            val listener = SplitInstallStateUpdatedListener { splitInstallSessionState ->
                if (splitInstallSessionState.sessionId() == sessionId) {
                    Log.d("Dynamic module status", splitInstallSessionState.status().toString())
                }
            }

            splitInstallManager.registerListener(listener)

            splitInstallManager.startInstall(request)
                .addOnSuccessListener {
                    Log.d("WooCommerceApp", it.toString())
                    sessionId = it
                }
                .addOnFailureListener {
                    Log.e("WooCommerceApp", it.toString())
                }
        }
    }
}
