package com.mimochat.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object SessionManager {
    private const val PREFS_NAME = "MiMoChatPrefs"
    private const val KEY_SESSIONS = "sessions"
    private val gson = Gson()
    
    fun getSessions(context: Context): List<Session> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        val type = object : TypeToken<List<Session>>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun saveSessions(context: Context, sessions: List<Session>) {
        val prefs = getPrefs(context)
        val json = gson.toJson(sessions)
        prefs.edit().putString(KEY_SESSIONS, json).apply()
    }
    
    fun addSession(context: Context, session: Session) {
        val sessions = getSessions(context).toMutableList()
        sessions.add(0, session)
        saveSessions(context, sessions)
    }
    
    fun deleteSession(context: Context, sessionId: String) {
        val sessions = getSessions(context).toMutableList()
        sessions.removeAll { it.id == sessionId }
        saveSessions(context, sessions)
    }
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
