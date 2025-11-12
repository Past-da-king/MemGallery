package com.example.memg

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MemGalleryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}