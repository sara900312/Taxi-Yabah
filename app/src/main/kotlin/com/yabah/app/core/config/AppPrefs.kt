package com.yabah.app.core.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Non-secret UI preferences. */
class AppPrefs(private val prefs: SharedPreferences?) {
    private val _language = MutableStateFlow(prefs?.getString(K_LANG, null))
    /** "ar", "en" or null (follow the system / per-app language setting). */
    val language: StateFlow<String?> = _language.asStateFlow()

    var onboardingSeen: Boolean
        get() = prefs?.getBoolean(K_ONB, false) ?: false
        set(v) { prefs?.edit()?.putBoolean(K_ONB, v)?.apply() }

    var demoMode: Boolean
        get() = prefs?.getBoolean(K_DEMO, false) ?: false
        set(v) { prefs?.edit()?.putBoolean(K_DEMO, v)?.apply() }

    fun setLanguage(tag: String?) {
        prefs?.edit()?.putString(K_LANG, tag)?.apply()
        _language.value = tag
    }

    companion object {
        private const val K_LANG = "language"
        private const val K_ONB = "onboarding_seen"
        private const val K_DEMO = "demo_mode"
        fun create(context: Context) = AppPrefs(context.getSharedPreferences("yabah_prefs", Context.MODE_PRIVATE))
    }
}
