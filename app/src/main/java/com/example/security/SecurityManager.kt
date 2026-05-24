package com.example.security

import android.content.Context
import android.content.SharedPreferences

class SecurityManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vaultnote_secure_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MASTER_HASH = "master_hash"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"
        private const val KEY_AUTO_LOCK_TIME = "auto_lock_time" // in minutes: 1, 3, 5, 10, or -1 (disabled)
        private const val KEY_CLIPBOARD_CLEAR_TIME = "clipboard_clear_time" // in seconds: 15, 30, 60
        private const val KEY_BIOMETRICS_ENABLED = "biometrics_enabled"
        private const val KEY_RECOVERY_EMAIL = "recovery_email"
    }

    var isSetupComplete: Boolean
        get() = prefs.contains(KEY_MASTER_HASH)
        private set(_) {}

    fun setMasterPassword(password: String) {
        val hash = HashHelper.hashPassword(password)
        prefs.edit().putString(KEY_MASTER_HASH, hash).apply()
    }

    fun verifyMasterPassword(password: String): Boolean {
        val stored = prefs.getString(KEY_MASTER_HASH, null) ?: return false
        return HashHelper.verifyPassword(password, stored)
    }

    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        if (!verifyMasterPassword(oldPassword)) return false
        setMasterPassword(newPassword)
        return true
    }

    fun setPin(pin: String) {
        val hash = HashHelper.hashPassword(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val stored = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return HashHelper.verifyPassword(pin, stored)
    }

    var isScreenshotProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, true) // enabled by default
        set(value) = prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, value).apply()

    var autoLockTime: Int
        get() = prefs.getInt(KEY_AUTO_LOCK_TIME, 5) // defaults to 5 minutes
        set(value) = prefs.edit().putInt(KEY_AUTO_LOCK_TIME, value).apply()

    var clipboardClearTime: Int
        get() = prefs.getInt(KEY_CLIPBOARD_CLEAR_TIME, 30) // defaults to 30 seconds
        set(value) = prefs.edit().putInt(KEY_CLIPBOARD_CLEAR_TIME, value).apply()

    var isBiometricsEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRICS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRICS_ENABLED, value).apply()

    var recoveryEmail: String
        get() = prefs.getString(KEY_RECOVERY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RECOVERY_EMAIL, value).apply()

    fun clearAllData() {
        prefs.edit().clear().apply()
    }
}
