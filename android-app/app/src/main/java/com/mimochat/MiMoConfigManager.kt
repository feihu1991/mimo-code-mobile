package com.mimochat

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

object MiMoConfigManager {
    private const val PREFS_NAME = "MiMoChatPrefs"
    private const val KEY_CONFIG = "mimo_config"
    private val gson = Gson()
    
    fun getConfig(context: Context): MiMoConfig? {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_CONFIG, null)
        if (json != null) {
            return gson.fromJson(json, MiMoConfig::class.java)
        }
        return null
    }
    
    fun saveConfig(context: Context, config: MiMoConfig) {
        val prefs = getPrefs(context)
        val json = gson.toJson(config)
        prefs.edit().putString(KEY_CONFIG, json).apply()
    }
    
    fun hasConfig(context: Context): Boolean {
        return getConfig(context) != null
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
