package com.taesung.widget

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

object ThemePrefs {
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREF = "taesung_widget_theme"
    private const val KEY_MODE = "mode"

    fun mode(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_SYSTEM)
            ?: MODE_SYSTEM

    fun setMode(ctx: Context, mode: String) {
        val clean = when (mode) {
            MODE_SYSTEM, MODE_LIGHT, MODE_DARK -> mode
            else -> MODE_SYSTEM
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, clean)
            .apply()
    }

    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode(ctx)) {
                MODE_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    fun isDark(ctx: Context): Boolean {
        return when (mode(ctx)) {
            MODE_DARK -> true
            MODE_LIGHT -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
    }
}
