package com.example.readwisecar.data

import android.content.Context

class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("readwise_prefs", Context.MODE_PRIVATE)

    var apiToken: String
        get() = prefs.getString("api_token", "") ?: ""
        set(value) = prefs.edit().putString("api_token", value.trim()).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playback_speed", 1.25f)
        set(value) = prefs.edit().putFloat("playback_speed", value).apply()

    var autoAdvance: Boolean
        get() = prefs.getBoolean("auto_advance", true)
        set(value) = prefs.edit().putBoolean("auto_advance", value).apply()
}
