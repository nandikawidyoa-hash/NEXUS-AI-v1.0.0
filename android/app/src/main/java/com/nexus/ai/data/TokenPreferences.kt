package com.nexus.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenPreferences(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "nexus_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("nexus_fallback_prefs", Context.MODE_PRIVATE)
    }

    var localEndpoint: String
        get() = prefs.getString("local_endpoint", "http://127.0.0.1:8765") ?: "http://127.0.0.1:8765"
        set(value) = prefs.edit().putString("local_endpoint", value.trimEnd('/')).apply()

    var apiToken: String
        get() = prefs.getString("api_token", "nexus-local-dev-token-2026") ?: "nexus-local-dev-token-2026"
        set(value) = prefs.edit().putString("api_token", value.trim()).apply()

    var cloudEndpoint: String
        get() = prefs.getString("cloud_endpoint", "") ?: ""
        set(value) = prefs.edit().putString("cloud_endpoint", value.trimEnd('/')).apply()

    var allowedSafUri: String
        get() = prefs.getString("allowed_saf_uri", "") ?: ""
        set(value) = prefs.edit().putString("allowed_saf_uri", value).apply()
}
