package com.example.ts3

import android.app.Application
import com.example.ts3.manager.SessionManager

class Ts3Application : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionManager.initialize(this)
    }
}
