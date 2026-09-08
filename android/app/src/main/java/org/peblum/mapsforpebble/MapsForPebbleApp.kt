package org.peblum.mapsforpebble

import android.app.Application

class MapsForPebbleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Navigator.init(this)
    }
}
