package be.pascu.wristmap

import android.app.Application

class WristmapApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Navigator.init(this)
    }
}
