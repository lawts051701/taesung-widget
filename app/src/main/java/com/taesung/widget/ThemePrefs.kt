package com.taesung.widget

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemePrefs {
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREF = "taesung_widget_theme"
    private const val KEY_MODE = "mode"

    fun mode(ctx: Context): String =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_LIGHT)
            .let { if (it == MODE_DARK) MODE_DARK else MODE_LIGHT }

    fun setMode(ctx: Context, mode: String) {
        val clean = when (mode) {
            MODE_DARK -> MODE_DARK
            else -> MODE_LIGHT
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, clean)
            .apply()
    }

    fun apply(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode(ctx)) {
                MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }

    fun isDark(ctx: Context): Boolean = mode(ctx) == MODE_DARK
}
