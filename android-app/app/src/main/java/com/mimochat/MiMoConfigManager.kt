package com.mimochat

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson

object MiMoConfigManager {
    private const val PREFS_NAME = "MiMoChatEncryptedPrefs"
    private const val KEY_CONFIG = "mimo_config"
    private val gson = Gson()

    fun getConfig(context: Context): MiMoConfig? {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_CONFIG, null)
        if (json != null) {
            return try {
                gson.fromJson(json, MiMoConfig::class.java)
            } catch (e: Exception) {
                prefs.edit().remove(KEY_CONFIG).apply()
                null
            }
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

    fun clearConfig(context: Context) {
        getPrefs(context).edit().remove(KEY_CONFIG).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
