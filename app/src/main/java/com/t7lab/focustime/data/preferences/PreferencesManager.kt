package com.t7lab.focustime.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focustime_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private object Keys {
        val MASTER_PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        val SESSION_ACTIVE = booleanPreferencesKey("session_active")
        val SESSION_END_TIME = longPreferencesKey("session_end_time")
        val SESSION_ID = longPreferencesKey("session_id")
    }

    val hasPassword: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.MASTER_PASSWORD_HASH] != null
    }

    val isSessionActive: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SESSION_ACTIVE] == true
    }

    val sessionEndTime: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.SESSION_END_TIME] ?: 0L
    }

    val sessionId: Flow<Long> = dataStore.data.map { prefs ->
        prefs[Keys.SESSION_ID] ?: 0L
    }

    suspend fun isSessionActiveOnce(): Boolean {
        return dataStore.data.first()[Keys.SESSION_ACTIVE] == true
    }

    suspend fun getSessionEndTimeOnce(): Long {
        return dataStore.data.first()[Keys.SESSION_END_TIME] ?: 0L
    }

    suspend fun setPassword(password: String) {
        dataStore.edit { prefs ->
            prefs[Keys.MASTER_PASSWORD_HASH] = hashPassword(password)
        }
    }

    suspend fun removePassword() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.MASTER_PASSWORD_HASH)
        }
    }

    suspend fun verifyPassword(password: String): Boolean {
        val storedHash = dataStore.data.first()[Keys.MASTER_PASSWORD_HASH] ?: return false
        return hashPassword(password) == storedHash
    }

    suspend fun hasPasswordSet(): Boolean {
        return dataStore.data.first()[Keys.MASTER_PASSWORD_HASH] != null
    }

    suspend fun startSession(sessionId: Long, endTime: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.SESSION_ACTIVE] = true
            prefs[Keys.SESSION_END_TIME] = endTime
            prefs[Keys.SESSION_ID] = sessionId
        }
    }

    suspend fun endSession() {
        dataStore.edit { prefs ->
            prefs[Keys.SESSION_ACTIVE] = false
            prefs.remove(Keys.SESSION_END_TIME)
            prefs.remove(Keys.SESSION_ID)
        }
    }

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salt = "FocusTime_v1_salt" // static salt - sufficient for local-only password
        val bytes = digest.digest("$salt$password".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
