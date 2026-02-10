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
        private const val PBKDF2_KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val MAX_PASSWORD_ATTEMPTS = 5
        private const val LOCKOUT_DURATION_MS = 60_000L
    }

    private object Keys {
        val MASTER_PASSWORD_HASH = stringPreferencesKey("master_password_hash")
        val PASSWORD_FAILED_ATTEMPTS = longPreferencesKey("password_failed_attempts")
        val PASSWORD_LOCKOUT_UNTIL = longPreferencesKey("password_lockout_until")
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
        dataStore.edit { prefs ->
            prefs[Keys.MASTER_PASSWORD_HASH] = hashPassword(password)
            prefs[Keys.PASSWORD_FAILED_ATTEMPTS] = 0L
            prefs.remove(Keys.PASSWORD_LOCKOUT_UNTIL)
        }
    }

    suspend fun removePassword() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.MASTER_PASSWORD_HASH)
            prefs.remove(Keys.PASSWORD_FAILED_ATTEMPTS)
            prefs.remove(Keys.PASSWORD_LOCKOUT_UNTIL)
        }
    }

    /**
     * Returns true if password is correct, false if wrong, and false if locked out.
     * Use [isPasswordLocked] to distinguish between wrong password and lockout.
     */
    suspend fun verifyPassword(password: String): Boolean {
        val prefs = dataStore.data.first()
        val storedHash = prefs[Keys.MASTER_PASSWORD_HASH] ?: return false

        // Check lockout
        val lockoutUntil = prefs[Keys.PASSWORD_LOCKOUT_UNTIL] ?: 0L
        if (System.currentTimeMillis() < lockoutUntil) {
            return false
        }

        val isValid = verifyPbkdf2(password, storedHash)
        if (isValid) {
            dataStore.edit { p ->
                p[Keys.PASSWORD_FAILED_ATTEMPTS] = 0L
                p.remove(Keys.PASSWORD_LOCKOUT_UNTIL)
            }
        } else {
            dataStore.edit { p ->
                val attempts = (p[Keys.PASSWORD_FAILED_ATTEMPTS] ?: 0L) + 1
                p[Keys.PASSWORD_FAILED_ATTEMPTS] = attempts
                if (attempts >= MAX_PASSWORD_ATTEMPTS) {
                    p[Keys.PASSWORD_LOCKOUT_UNTIL] =
                        System.currentTimeMillis() + LOCKOUT_DURATION_MS
                    p[Keys.PASSWORD_FAILED_ATTEMPTS] = 0L
                }
            }
        }
        return isValid
    }

    suspend fun isPasswordLocked(): Boolean {
        val lockoutUntil = dataStore.data.first()[Keys.PASSWORD_LOCKOUT_UNTIL] ?: 0L
        return System.currentTimeMillis() < lockoutUntil
    }

    suspend fun getPasswordLockoutRemainingMs(): Long {
        val lockoutUntil = dataStore.data.first()[Keys.PASSWORD_LOCKOUT_UNTIL] ?: 0L
        return (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0L)
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

    /**
     * Hash password using PBKDF2 with a random salt.
     * Returns "salt_hex:hash_hex" format.
     */
    private fun hashPassword(password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(password, salt)
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hash.joinToString("") { "%02x".format(it) }
        return "$saltHex:$hashHex"
    }

    /**
     * Verify password against stored "salt_hex:hash_hex" format.
     * Also supports legacy SHA-256 hashes (no colon) for migration.
     */
    private fun verifyPbkdf2(password: String, stored: String): Boolean {
        if (!stored.contains(':')) {
            // Legacy SHA-256 hash migration: verify against old format
            return verifyLegacySha256(password, stored)
        }
        val parts = stored.split(':', limit = 2)
        if (parts.size != 2) return false
        val salt = hexToBytes(parts[0]) ?: return false
        val expectedHash = hexToBytes(parts[1]) ?: return false
        val actualHash = pbkdf2(password, salt)
        // Constant-time comparison to prevent timing attacks
        return constantTimeEquals(expectedHash, actualHash)
    }

    private fun verifyLegacySha256(password: String, storedHash: String): Boolean {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val salt = "FocusTime_v1_salt"
        val bytes = digest.digest("$salt$password".toByteArray())
        val computedHash = bytes.joinToString("") { "%02x".format(it) }
        return constantTimeEquals(computedHash.toByteArray(), storedHash.toByteArray())
    }

    private fun pbkdf2(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun hexToBytes(hex: String): ByteArray? {
        if (hex.length % 2 != 0) return null
        return try {
            ByteArray(hex.length / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }
}
