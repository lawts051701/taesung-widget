package com.taesung.widget

import android.app.Application

class TaesungApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.apply(this)
    }
}
