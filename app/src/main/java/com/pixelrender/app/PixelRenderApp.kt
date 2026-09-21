package com.pixelrender.app

import android.app.Application
import com.pixelrender.app.logging.Logger

class PixelRenderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logger.i("PixelRender ${BuildConfig.VERSION_NAME} started")
    }
}
