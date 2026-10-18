package com.pronoscore360.core.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.pronoscore360.core.model.ErrorBody
import com.pronoscore360.core.model.LoginRequest
import com.pronoscore360.core.model.RefreshRequest
import com.pronoscore360.core.model.Tokens
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit

val ApiJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false; encodeDefaults = true }

/** Almacén de tokens (en Android: DataStore cifrado/EncryptedSharedPreferences). */
interface TokenStore {
    fun get(): Tokens?
    fun set(tokens: Tokens?)
}
class InMemoryTokenStore(private var t: Tokens? = null) : TokenStore {
    @Synchronized override fun get() = t
    @Synchronized override fun set(tokens: Tokens?) { t = tokens }
}

/** Error de negocio del backend (código estable + mensaje en español listo para mostrar). */
class ApiException(val status: Int, val code: String, override val message: String, val details: kotlinx.serialization.json.JsonElement? = null) : Exception(message)
class NetworkException(override val message: String = "No se pudo conectar con el servidor. Revisa tu conexión.") : Exception(message)

/** Convierte excepciones de Retrofit/OkHttp en ApiException/NetworkException con el mensaje del servidor. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val raw = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
    val parsed = raw?.let { try { ApiJson.decodeFromString(ErrorBody.serializer(), it).error } catch (_: Exception) { null } }
    throw ApiException(e.code(), parsed?.code ?: "http_${e.code()}", parsed?.message ?: "Error del servidor (${e.code()})", parsed?.details)
} catch (e: IOException) {
    throw NetworkException()
}

private class AuthInterceptor(private val store: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val t = store.get() ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer ${t.accessToken}").build())
    }
}

/** Renueva el access token ante 401 (rotación de refresh token). Una sola renovación a la vez. */
private class TokenAuthenticator(private val baseUrl: String, private val store: TokenStore, private val onLogout: () -> Unit) : Authenticator {
    private val plain = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    private val lock = Any()
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.contains("/auth/")) return null
        if (responseCount(response) >= 2) return null
        synchronized(lock) {
            val current = store.get() ?: return null
            val sent = response.request.header("Authorization")?.removePrefix("Bearer ")
            if (sent != null && sent != current.accessToken) {            // otro hilo ya renovó
                return response.request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
            }
            val body = ApiJson.encodeToString(RefreshRequest.serializer(), RefreshRequest(current.refreshToken))
                .toRequestBody("application/json".toMediaType())
            val req = Request.Builder().url(baseUrl + "auth/refresh").post(body).build()
            return try {
                plain.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) { store.set(null); onLogout(); return null }
                    val fresh = ApiJson.decodeFromString(Tokens.serializer(), r.body!!.string())
                    store.set(fresh)
                    response.request.newBuilder().header("Authorization", "Bearer ${fresh.accessToken}").build()
                }
            } catch (_: IOException) { null }
        }
    }
    private fun responseCount(r: Response): Int { var n = 1; var p = r.priorResponse; while (p != null) { n++; p = p.priorResponse }; return n }
}

object ApiClient {
    fun create(baseUrl: String, store: TokenStore, onLogout: () -> Unit = {}, debug: Boolean = false): PronoApi {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val ok = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(store))
            .authenticator(TokenAuthenticator(url, store, onLogout))
            .apply { if (debug) addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC; redactHeader("Authorization") }) }
            .build()
        return Retrofit.Builder().baseUrl(url).client(ok)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
            .build().create(PronoApi::class.java)
    }
}
