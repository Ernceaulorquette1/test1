package com.pronoscore360.core

import com.pronoscore360.core.api.*
import com.pronoscore360.core.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*
import java.net.HttpURLConnection
import java.net.URL

/** Integración Android-core ↔ backend real (modo DEMO). Se omite si el backend no está en API_URL. */
class LiveBackendTest {
    private val base = System.getenv("API_URL") ?: "http://localhost:8000/api/v1"
    private fun up() = try { (URL("$base/health").openConnection() as HttpURLConnection).apply { connectTimeout = 1500 }.responseCode == 200 } catch (_: Exception) { false }

    @Test fun `login demo, calendario, analisis y renovacion de token contra el backend real`() = runBlocking {
        assumeTrue("Backend no disponible en $base: prueba omitida", up())
        val store = InMemoryTokenStore()
        val api = ApiClient.create(base, store)
        val r = apiCall { api.login(LoginRequest("free@pronoscore-demo.example.com", "Demo#Free2026")) }
        store.set(r.tokens)
        assertEquals("FREE", r.user.plan.code)
        val fx = apiCall { api.fixtures(sport = "football", status = "scheduled", pageSize = 3) }
        assertTrue(fx.items.isNotEmpty())
        val a = apiCall { api.analysis(fx.items[0].id) }
        assertTrue(a.ok); assertTrue(a.predictions.all { it.probability in 0.0..1.0 })
        assertEquals(setOf("basic"), a.predictions.map { it.group }.toSet())          // el plan gratis sólo ve mercados básicos
        // access token inválido => el Authenticator renueva con el refresh token real
        store.set(store.get()!!.copy(accessToken = "invalido"))
        assertEquals("free@pronoscore-demo.example.com", apiCall { api.me() }.email)
        assertNotEquals("invalido", store.get()!!.accessToken)
        val e = runCatching { apiCall { api.predictions() } }.exceptionOrNull() as ApiException
        assertEquals(402, e.status)                                                    // Pro-only aplicado en servidor
    }
}
