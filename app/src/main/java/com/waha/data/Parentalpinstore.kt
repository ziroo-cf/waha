package com.waha.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

object ParentalPinStore {
    private const val PREFS_NAME = "waha_parental_pin_prefs"
    private const val KEY_PIN = "parental_pin"
    private const val KEY_ENABLED = "parental_pin_enabled"

    private val _isEnabled = mutableStateOf(false)
    val isEnabled: State<Boolean> = _isEnabled

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val sharedPrefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sharedPrefs
        _isEnabled.value = sharedPrefs.getBoolean(KEY_ENABLED, false)
    }

    fun isPinSet(): Boolean = !prefs?.getString(KEY_PIN, null).isNullOrBlank()

    fun setPin(pin: String) {
        prefs?.edit()?.putString(KEY_PIN, pin)?.putBoolean(KEY_ENABLED, true)?.apply()
        _isEnabled.value = true
    }

    fun verifyPin(pin: String): Boolean = pin == prefs?.getString(KEY_PIN, null)

    /** Returns true when the pin was cleared, false when the entered pin did not match. */
    fun disablePin(enteredPin: String): Boolean {
        if (!verifyPin(enteredPin)) return false
        prefs?.edit()?.clear()?.apply()
        _isEnabled.value = false
        return true
    }
}
