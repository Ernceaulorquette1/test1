package com.pronoscore360.app.data

import com.pronoscore360.app.data.local.CacheDao
import com.pronoscore360.app.data.local.CachedJson
import com.pronoscore360.core.api.ApiJson
import com.pronoscore360.core.api.NetworkException
import com.pronoscore360.core.api.PronoApi
import com.pronoscore360.core.api.TokenStore
import com.pronoscore360.core.api.apiCall
import com.pronoscore360.core.model.*
import kotlinx.serialization.KSerializer

/** Única fuente de datos de la app: API propia + caché Room como respaldo sin conexión. */
class Repository(val api: PronoApi, private val tokens: TokenStore, private val cache: CacheDao) {
    val hasSession get() = tokens.get() != null

    suspend fun login(email: String, password: String): User {
        val r = apiCall { api.login(LoginRequest(email.trim(), password)) }
        tokens.set(r.tokens); return r.user
    }
    suspend fun register(req: RegisterRequest): User {
        val r = apiCall { api.register(req) }
        tokens.set(r.tokens); return r.user
    }
    suspend fun logout() {
        tokens.get()?.let { runCatching { apiCall { api.logout(RefreshRequest(it.refreshToken)) } } }
        tokens.set(null); cache.clear()
    }
    fun clearLocalSession() { tokens.set(null) }
    suspend fun me(): User = apiCall { api.me() }

    /** Intenta la red; si no hay conexión devuelve la última copia guardada (si existe). */
    suspend fun <T> cached(key: String, ser: KSerializer<T>, fetch: suspend () -> T): T {
        return try {
            val v = apiCall { fetch() }
            cache.put(CachedJson(key, ApiJson.encodeToString(ser, v), System.currentTimeMillis())); v
        } catch (e: NetworkException) {
            cache.get(key)?.let { ApiJson.decodeFromString(ser, it.json) } ?: throw e
        }
    }
    suspend fun fixtures(sport: String?, date: String?, page: Int = 1): PageOf<Fixture> =
        cached("fixtures:$sport:$date:$page", PageOf.serializer(Fixture.serializer())) { api.fixtures(sport = sport, date = date, page = page) }
    suspend fun plans(): List<Plan> = cached("plans", kotlinx.serialization.builtins.ListSerializer(Plan.serializer())) { api.plans() }
}
