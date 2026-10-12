package com.pronoscore360.core

import com.pronoscore360.core.api.*
import com.pronoscore360.core.model.*
import com.pronoscore360.core.util.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private const val USER_JSON = """{"id":1,"email":"a@b.co","display_name":"Ana","role":"USER","email_verified":true,"country":"CL","language":"es","timezone":"America/Santiago","theme":"system",
 "plan":{"code":"FREE","name":"Gratis","daily_analysis_limit":5,"favorites_limit":10,"history_days":7,"selections_enabled":false,"allowed_market_groups":["basic"],"features":{"alerts":false,"exact_score_top_n":1},"prices":{},"campo_nuevo":1},
 "subscription_status":null,"subscription_ends_at":null,"usage":{"plan":"FREE","date":"2026-01-01","timezone":"America/Santiago","limit":5,"used":2,"remaining":3,"resets_at":"2026-01-02T03:00:00+00:00"},"is_demo":false}"""
private const val TOKENS = """{"access_token":"A2","refresh_token":"R2","token_type":"bearer","expires_in":900}"""
private const val ANALYSIS = """{"fixture":{"id":5,"sport":"football","league":{"id":1,"name":"Liga Demo","country":null,"logo_url":null},"home_team":{"id":1,"name":"Demo FC Aurora","short_name":"AUR","logo_url":null,"is_demo":true},
 "away_team":{"id":2,"name":"Demo United Boreal","is_demo":true},"kickoff":"2026-03-01T15:00:00+00:00","kickoff_local":"2026-03-01T12:00:00-03:00","status":"scheduled","home_score":null,"away_score":null,"is_demo":true,"data_source":"DEMO"},
 "disclaimer":"d","usage":null,"analysis":{"id":9,"status":"ok","quality":"medium","n_home":18,"n_away":17,"generated_at":"2026-02-28T10:00:00+00:00","is_pre_match":true,"model":"football_poisson_dc","model_version":"1.0.0","is_demo":true,"category_criteria":{"high_probability":"x"}},
 "predictions":[{"id":1,"market":"1X2","selection":"home","key":"1X2:home","group":"basic","probability":0.5012,"probability_pct":50.1,"category":"medium_probability","uncertainty":"medium","data_points":17,"updated_at":"2026-02-28T10:00:00+00:00","is_pre_match":true,"result":null}],
 "locked_groups":["goals"],"locked_count":20,"exact_score":{"type":"football","top_scores":[{"home":1,"away":1,"probability":0.11}]},"drivers":null,"upgrade_hint":"Pro"}"""

class CoreTests {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemoryTokenStore
    private var loggedOut = 0
    private lateinit var api: PronoApi

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        store = InMemoryTokenStore(Tokens("A1", "R1"))
        loggedOut = 0
        api = ApiClient.create(server.url("/api/v1").toString(), store, onLogout = { loggedOut++ })
    }
    @After fun tearDown() { server.shutdown() }

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test fun `decodifica el usuario ignorando campos desconocidos y expone el plan`() = runBlocking {
        server.enqueue(json(200, USER_JSON))
        val u = apiCall { api.me() }
        assertEquals("FREE", u.plan.code); assertFalse(u.isPro); assertFalse(u.isAdmin)
        assertEquals(3, u.usage.remaining); assertEquals(listOf("basic"), u.plan.allowedMarketGroups)
        assertEquals("Bearer A1", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun `decodifica un analisis completo del backend`() = runBlocking {
        server.enqueue(json(200, ANALYSIS))
        val a = apiCall { api.analysis(5) }
        assertTrue(a.ok); assertEquals(0.5012, a.predictions[0].probability, 1e-9); assertEquals("1X2:home", a.predictions[0].key)
        assertEquals(listOf("goals"), a.lockedGroups); assertNotNull(a.exactScore); assertEquals("/api/v1/fixtures/5/analysis", server.takeRequest().path)
    }

    @Test fun `errores del servidor se convierten en ApiException con codigo y mensaje`() {
        server.enqueue(json(429, """{"error":{"code":"quota_exceeded","message":"Alcanzaste el límite de 5 partidos","request_id":"r1","details":{"usage":{"remaining":0}}}}"""))
        val e = assertThrows(ApiException::class.java) { runBlocking { apiCall { api.analysis(1) } } }
        assertEquals(429, e.status); assertEquals("quota_exceeded", e.code); assertTrue(e.message.contains("límite")); assertNotNull(e.details)
    }

    @Test fun `sin conexion se informa NetworkException`() {
        server.shutdown()
        assertThrows(NetworkException::class.java) { runBlocking { apiCall { api.status() } } }
    }

    @Test fun `ante 401 renueva el token una vez y reintenta`() = runBlocking {
        server.enqueue(json(401, """{"error":{"code":"token_expired","message":"El token expiró"}}"""))
        server.enqueue(json(200, TOKENS))
        server.enqueue(json(200, USER_JSON))
        val u = apiCall { api.me() }
        assertEquals("a@b.co", u.email)
        assertEquals("A2", store.get()?.accessToken); assertEquals("R2", store.get()?.refreshToken)
        val first = server.takeRequest(); val refresh = server.takeRequest(); val retry = server.takeRequest()
        assertEquals("Bearer A1", first.getHeader("Authorization"))
        assertEquals("/api/v1/auth/refresh", refresh.path); assertTrue(refresh.body.readUtf8().contains("R1"))
        assertEquals("Bearer A2", retry.getHeader("Authorization")); assertEquals(0, loggedOut)
    }

    @Test fun `si la renovacion falla limpia la sesion y avisa`() {
        server.enqueue(json(401, """{"error":{"code":"token_expired","message":"x"}}"""))
        server.enqueue(json(401, """{"error":{"code":"refresh_reuse","message":"Sesión revocada"}}"""))
        val e = assertThrows(ApiException::class.java) { runBlocking { apiCall { api.me() } } }
        assertEquals(401, e.status); assertNull(store.get()); assertEquals(1, loggedOut)
    }

    @Test fun `el login con credenciales malas no dispara renovacion`() {
        server.enqueue(json(401, """{"error":{"code":"invalid_credentials","message":"Correo o contraseña incorrectos"}}"""))
        val e = assertThrows(ApiException::class.java) { runBlocking { apiCall { api.login(LoginRequest("a@b.co", "mala")) } } }
        assertEquals("invalid_credentials", e.code); assertEquals(1, server.requestCount); assertEquals("A1", store.get()?.accessToken)
    }

    @Test fun `parametros de consulta de fixtures`() = runBlocking {
        server.enqueue(json(200, """{"items":[],"total":0,"page":1,"page_size":30}"""))
        apiCall { api.fixtures(sport = "basketball", date = "2026-03-01", status = "scheduled") }
        val p = server.takeRequest().requestUrl!!
        assertEquals("basketball", p.queryParameter("sport")); assertEquals("2026-03-01", p.queryParameter("date")); assertEquals("30", p.queryParameter("page_size"))
    }

    @Test fun `evaluacion conjunta no calculable se refleja`() = runBlocking {
        server.enqueue(json(200, """{"items":[],"joint":{"computable":false,"probability":null,"reason":"No se puede calcular con precisión la probabilidad total"}}"""))
        val r = apiCall { api.evaluate(EvaluateRequest(listOf(SelectionItemIn(1, "1X2:home")))) }
        assertFalse(r.joint.computable); assertNull(r.joint.probability); assertTrue(r.joint.reason!!.contains("No se puede calcular"))
        assertTrue(server.takeRequest().body.readUtf8().contains("\"fixture_id\":1"))
    }

    @Test fun `eliminar cuenta envia DELETE con cuerpo`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        apiCall { api.deleteAccount(DeleteAccountRequest("Clave#12345", true)) }
        val r = server.takeRequest()
        assertEquals("DELETE", r.method); assertEquals("/api/v1/me", r.path); assertTrue(r.body.readUtf8().contains("\"confirm\":true"))
    }

    @Test fun `formato de probabilidad no infla el valor`() {
        assertEquals("62.0 %", pct(0.62)); assertEquals("61.5 %", pct(0.6149)); assertEquals("61 %", pct(0.6149, 0)); assertEquals("0.1 %", pct(0.00149))
    }

    @Test fun `etiquetas de mercado`() {
        assertEquals("Victoria Aurora", marketLabel("1X2", "home", "Aurora", "Boreal"))
        assertEquals("Empate", marketLabel("1X2", "draw"))
        assertEquals("Goles totales: Más de 2.5", marketLabel("OU:2.5", "over"))
        assertEquals("LAL -3.5 (hándicap): cubre", marketLabel("SPREAD:home:-3.5", "cover", "LAL"))
        assertEquals("Puntos Rockets: Menos de 108.5", marketLabel("TEAM_TOTAL:away:108.5", "under", "Lakers", "Rockets"))
    }

    @Test fun `cesta de selecciones sin duplicados y con tope`() {
        val b = SelectionBasket(max = 2)
        assertTrue(b.add(BasketItem(1, "1X2:home", "a", "m", .5))); assertFalse(b.add(BasketItem(1, "1X2:home", "a", "m", .5)))
        assertTrue(b.add(BasketItem(1, "OU:2.5:over", "b", "m", .5))); assertFalse(b.add(BasketItem(2, "BTTS:yes", "c", "m", .5)))
        assertEquals(2, b.items.size); b.remove(1, "1X2:home"); assertFalse(b.has(1, "1X2:home"))
        assertEquals(listOf(SelectionItemIn(1, "OU:2.5:over")), b.toRequest())
    }
}
