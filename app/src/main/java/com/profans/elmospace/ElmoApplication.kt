package com.profans.elmospace

import android.app.Application

class ElmoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MobileDataUsageTracker.start(this)
    }
}
