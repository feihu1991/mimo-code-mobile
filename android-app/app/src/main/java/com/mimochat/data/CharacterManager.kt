package com.mimochat.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

object CharacterManager {
    private const val PREFS_NAME = "MiMoChatPrefs"
    private const val KEY_SELECTED_CHARACTER = "selected_character"
    private val gson = Gson()
    
    fun getSelectedCharacter(context: Context): Character {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_SELECTED_CHARACTER, null)
        if (json != null) {
            return gson.fromJson(json, Character::class.java)
        }
        return CharacterPresets.characters[0]
    }
    
    fun setSelectedCharacter(context: Context, character: Character) {
        val prefs = getPrefs(context)
        val json = gson.toJson(character)
        prefs.edit().putString(KEY_SELECTED_CHARACTER, json).apply()
    }
    
    fun getAllCharacters(): List<Character> {
        return CharacterPresets.characters
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
