package com.example.agent

import android.app.Application
import com.example.agent.core.ai.local.OnDeviceModelManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpideyApplication : Application() {

    @Inject lateinit var onDeviceModels: OnDeviceModelManager

    override fun onCreate() {
        // Hilt injects the fields in super.onCreate().
        super.onCreate()
        // First launch: fetch the on-device model in the background, unless on mobile data.
        onDeviceModels.startIfAllowed()
    }
}
