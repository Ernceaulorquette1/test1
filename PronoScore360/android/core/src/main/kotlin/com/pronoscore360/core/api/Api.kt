package com.pronoscore360.core.api

import com.pronoscore360.core.model.*
import kotlinx.serialization.json.JsonElement
import retrofit2.http.*

/** Contrato REST de PronoScore360 (/api/v1). Sólo se habla con NUESTRO backend: nunca con API-SPORTS. */
interface PronoApi {
    @POST("auth/register") suspend fun register(@Body b: RegisterRequest): AuthResponse
    @POST("auth/login") suspend fun login(@Body b: LoginRequest): AuthResponse
    @POST("auth/logout") suspend fun logout(@Body b: RefreshRequest)
    @POST("auth/forgot-password") suspend fun forgot(@Body b: EmailRequest)

    @GET("me") suspend fun me(): User
    @PATCH("me") suspend fun patchMe(@Body b: ProfilePatch): User
    @HTTP(method = "DELETE", path = "me", hasBody = true) suspend fun deleteAccount(@Body b: DeleteAccountRequest)
    @GET("me/usage") suspend fun usage(): Usage
    @GET("me/history") suspend fun history(@Query("page") page: Int = 1): JsonElement
    @GET("me/comparison") suspend fun comparison(): JsonElement
    @POST("me/devices") suspend fun registerDevice(@Body b: DeviceRequest)

    @GET("status") suspend fun status(): StatusInfo
    @GET("fixtures") suspend fun fixtures(
        @Query("sport") sport: String? = null, @Query("date") date: String? = null, @Query("status") status: String? = null,
        @Query("team_id") teamId: Long? = null, @Query("q") q: String? = null, @Query("league_id") leagueId: Long? = null,
        @Query("page") page: Int = 1, @Query("page_size") pageSize: Int = 30,
    ): PageOf<Fixture>
    @GET("fixtures/{id}/stats") suspend fun fixtureStats(@Path("id") id: Long): JsonElement
    @GET("fixtures/{id}/analysis") suspend fun analysis(@Path("id") id: Long): Analysis
    @GET("teams") suspend fun teams(@Query("q") q: String? = null, @Query("page_size") pageSize: Int = 30): PageOf<Team>
    @GET("teams/{id}/stats") suspend fun teamStats(@Path("id") id: Long, @Query("last") last: Int = 10): JsonElement
    @GET("standings") suspend fun standings(@Query("league_id") leagueId: Long): JsonElement
    @GET("leagues") suspend fun leagues(@Query("sport") sport: String? = null): JsonElement

    @GET("predictions") suspend fun predictions(
        @Query("sport") sport: String? = null, @Query("market") market: String? = null,
        @Query("min_probability") minProbability: Double? = null, @Query("uncertainty") uncertainty: String? = null,
        @Query("category") category: String? = null, @Query("page") page: Int = 1, @Query("page_size") pageSize: Int = 30,
    ): JsonElement
    @GET("predictions/meta") suspend fun predictionsMeta(): JsonElement
    @GET("models/performance") suspend fun performance(): JsonElement
    @POST("saved-predictions/{id}") suspend fun savePrediction(@Path("id") id: Long): JsonElement

    @GET("favorites") suspend fun favorites(): JsonElement
    @POST("favorites") suspend fun addFavorite(@Body b: FavoriteRequest): JsonElement
    @DELETE("favorites/{id}") suspend fun removeFavorite(@Path("id") id: Long)
    @POST("selections/evaluate") suspend fun evaluate(@Body b: EvaluateRequest): EvaluateResponse
    @POST("selections") suspend fun createGroup(@Body b: GroupRequest): JsonElement
    @GET("selections") suspend fun groups(): JsonElement
    @DELETE("selections/{id}") suspend fun deleteGroup(@Path("id") id: Long)
    @GET("selections/{id}/share") suspend fun shareGroup(@Path("id") id: Long): JsonElement

    @GET("notifications") suspend fun notifications(): NotificationPage
    @POST("notifications/read-all") suspend fun readAll()

    @GET("plans") suspend fun plans(): List<Plan>
    @GET("subscriptions/me") suspend fun mySubscription(): JsonElement
    @GET("billing/account-id") suspend fun accountId(): AccountIdResponse
    @POST("subscriptions/google/verify") suspend fun verifyPurchase(@Body b: PurchaseRequest): SubscriptionResponse
    @POST("subscriptions/google/restore") suspend fun restorePurchases(@Body b: RestoreRequest): SubscriptionResponse
    @POST("subscriptions/demo/activate") suspend fun demoActivate(): SubscriptionResponse
}
