package com.mircode.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mircode.app.data.model.AppSettings
import com.mircode.app.data.model.ConnectionProfile

class DevicePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("mircode_preferences", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getSavedProfiles(): List<ConnectionProfile> {
        val json = prefs.getString(KEY_SAVED_PROFILES, null) ?: return emptyList()
        val type = object : TypeToken<List<ConnectionProfile>>() {}.type
        return runCatching { gson.fromJson<List<ConnectionProfile>>(json, type) ?: emptyList() }
            .getOrDefault(emptyList())
            .sortedByDescending { it.lastConnected }
    }

    fun getLastProfile(): ConnectionProfile? {
        val list = getSavedProfiles()
        return list.firstOrNull()
    }

    fun saveProfile(profile: ConnectionProfile) {
        val list = getSavedProfiles().toMutableList()
        val index = list.indexOfFirst {
            it.id == profile.id || (it.pairCode != null && it.pairCode == profile.pairCode) ||
                    (it.host == profile.host && it.port == profile.port && it.token == profile.token)
        }
        val updated = profile.copy(lastConnected = System.currentTimeMillis())
        if (index >= 0) {
            list[index] = updated
        } else {
            list.add(0, updated)
        }
        prefs.edit().putString(KEY_SAVED_PROFILES, gson.toJson(list)).apply()
    }

    fun removeProfile(id: String) {
        val list = getSavedProfiles().filterNot { it.id == id }
        prefs.edit().putString(KEY_SAVED_PROFILES, gson.toJson(list)).apply()
    }

    fun getSettings(): AppSettings {
        val json = prefs.getString(KEY_APP_SETTINGS, null) ?: return AppSettings()
        return runCatching { gson.fromJson(json, AppSettings::class.java) ?: AppSettings() }
            .getOrDefault(AppSettings())
    }

    fun saveSettings(settings: AppSettings) {
        prefs.edit().putString(KEY_APP_SETTINGS, gson.toJson(settings)).apply()
    }

    companion object {
        private const val KEY_SAVED_PROFILES = "saved_profiles"
        private const val KEY_APP_SETTINGS = "app_settings"
    }
}
