package com.nexus.ai.data
import android.content.Context
import android.content.SharedPreferences
class TokenPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nexus_prefs_v2", Context.MODE_PRIVATE)
    var localEndpoint: String get() = prefs.getString("local_endpoint", "http://127.0.0.1:8765") ?: "http://127.0.0.1:8765"
        set(v) = prefs.edit().putString("local_endpoint", v.trim().trimEnd('/')).apply()
    var apiToken: String get() = prefs.getString("api_token", "nexus-local-dev-token-2026") ?: "nexus-local-dev-token-2026"
        set(v) = prefs.edit().putString("api_token", v.trim()).apply()
    var activeProviderId: String get() = prefs.getString("prov_id", "prov-gemini") ?: "prov-gemini"
        set(v) = prefs.edit().putString("prov_id", v.trim()).apply()
    var activeProviderName: String get() = prefs.getString("prov_name", "Google Gemini") ?: "Google Gemini"
        set(v) = prefs.edit().putString("prov_name", v.trim()).apply()
    var customRouterUrl: String get() = prefs.getString("router_url", "https://generativelanguage.googleapis.com/v1beta") ?: "https://generativelanguage.googleapis.com/v1beta"
        set(v) = prefs.edit().putString("router_url", v.trim().trimEnd('/')).apply()
    var activeModelName: String get() = prefs.getString("model_name", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(v) = prefs.edit().putString("model_name", v.trim()).apply()
    var providerApiKey: String get() = prefs.getString("prov_key", "") ?: ""
        set(v) = prefs.edit().putString("prov_key", v.trim()).apply()
    var cloudEndpoint: String get() = prefs.getString("cloud_endpoint", "") ?: ""
        set(v) = prefs.edit().putString("cloud_endpoint", v.trimEnd('/')).apply()
    var allowedSafUri: String get() = prefs.getString("allowed_saf_uri", "") ?: ""
        set(v) = prefs.edit().putString("allowed_saf_uri", v).apply()
    fun maskedApiKey(): String {
        val k = providerApiKey.trim()
        return if (k.isEmpty()) "Belum diatur" else if (k.length <= 8) "****" + k.takeLast(2) else k.take(4) + "..." + k.takeLast(4)
    }
}
