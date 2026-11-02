package com.example

import android.app.Application
import android.util.Log
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TomTomApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val apiKey = BuildConfig.TOMTOM_API_KEY.ifEmpty { "YOUR_TOMTOM_API_KEY" }
        Log.d("TomTomApp", "Initializing TomTomSdk with key length: ${apiKey.length}")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val sdkConfig = buildSdkConfiguration(
                    context = applicationContext,
                    apiKey = apiKey
                )
                TomTomSdk.initialize(applicationContext, sdkConfig)
                Log.d("TomTomApp", "TomTomSdk initialized successfully")
            } catch (e: Exception) {
                Log.e("TomTomApp", "Error initializing TomTomSdk", e)
            }
        }
    }
}
