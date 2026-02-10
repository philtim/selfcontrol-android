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
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focustime_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_BYTES = 16
    }

    private object Keys {
        val MASTER_PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        val MASTER_PASSWORD_SALT = stringPreferencesKey("master_password_salt")
        val SESSION_ACTIVE = booleanPreferencesKey("session_active")
        val SESSION_END_TIME = longPreferencesKey("session_end_time")
        val SESSION_ID = longPreferencesKey("session_id")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLORS = booleanPreferencesKey("dynamic_colors")
        val LAST_COMPLETED_DURATION_MS = longPreferencesKey("last_completed_duration_ms")
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

    val themeMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE] ?: "system"
    }

    val dynamicColors: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLORS] ?: true
    }

    suspend fun isSessionActiveOnce(): Boolean {
        return dataStore.data.first()[Keys.SESSION_ACTIVE] == true
    }

    suspend fun getSessionEndTimeOnce(): Long {
        return dataStore.data.first()[Keys.SESSION_END_TIME] ?: 0L
    }

    suspend fun isOnboardingComplete(): Boolean {
        return dataStore.data.first()[Keys.ONBOARDING_COMPLETE] == true
    }

    suspend fun completeOnboarding() {
        dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun setThemeMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode
        }
    }

    suspend fun setDynamicColors(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.DYNAMIC_COLORS] = enabled
        }
    }

    suspend fun setLastCompletedDuration(durationMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_COMPLETED_DURATION_MS] = durationMs
        }
    }

    suspend fun getLastCompletedDuration(): Long {
        return dataStore.data.first()[Keys.LAST_COMPLETED_DURATION_MS] ?: 0L
    }

    suspend fun clearLastCompletedDuration() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.LAST_COMPLETED_DURATION_MS)
        }
    }

    suspend fun setPassword(password: String) {
        val salt = generateSalt()
        val hash = hashPassword(password, salt)
        dataStore.edit { prefs ->
            prefs[Keys.MASTER_PASSWORD_SALT] = salt.toHex()
            prefs[Keys.MASTER_PASSWORD_HASH] = hash
        }
    }

    suspend fun removePassword() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.MASTER_PASSWORD_HASH)
            prefs.remove(Keys.MASTER_PASSWORD_SALT)
        }
    }

    suspend fun verifyPassword(password: String): Boolean {
        val prefs = dataStore.data.first()
        val storedHash = prefs[Keys.MASTER_PASSWORD_HASH] ?: return false
        val storedSaltHex = prefs[Keys.MASTER_PASSWORD_SALT]

        return if (storedSaltHex != null) {
            // PBKDF2 path (new)
            val salt = storedSaltHex.hexToBytes()
            hashPassword(password, salt) == storedHash
        } else {
            // Legacy SHA-256 path — verify then migrate
            val legacyHash = legacyHashPassword(password)
            if (legacyHash == storedHash) {
                // Migrate to PBKDF2 on successful verify
                setPassword(password)
                true
            } else {
                false
            }
        }
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

    private fun hashPassword(password: String, salt: ByteArray): String {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.toHex()
    }

    /** Legacy SHA-256 hash for migration of existing passwords. */
    private fun legacyHashPassword(password: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val salt = "FocusTime_v1_salt"
        val bytes = digest.digest("$salt$password".toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
