package com.pronoscore360.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.pronoscore360.core.api.TokenStore
import com.pronoscore360.core.model.Tokens

/** Tokens cifrados con Android Keystore (EncryptedSharedPreferences). */
class TokenStoreImpl(context: Context) : TokenStore {
    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(context, "ps360_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    } catch (e: Exception) {
        context.getSharedPreferences("ps360_fallback", Context.MODE_PRIVATE)   // Keystore corrupto: se pedirá iniciar sesión de nuevo
    }
    @Synchronized override fun get(): Tokens? {
        val a = prefs.getString("access", null) ?: return null
        val r = prefs.getString("refresh", null) ?: return null
        return Tokens(a, r)
    }
    @Synchronized override fun set(tokens: Tokens?) {
        prefs.edit().apply { if (tokens == null) { remove("access"); remove("refresh") } else { putString("access", tokens.accessToken); putString("refresh", tokens.refreshToken) } }.apply()
    }
}
