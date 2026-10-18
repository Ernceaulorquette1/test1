package com.pronoscore360.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable data class Plan(
    val code: String, val name: String,
    @SerialName("daily_analysis_limit") val dailyAnalysisLimit: Int? = null,
    @SerialName("favorites_limit") val favoritesLimit: Int? = null,
    @SerialName("history_days") val historyDays: Int? = null,
    @SerialName("selections_enabled") val selectionsEnabled: Boolean = false,
    @SerialName("allowed_market_groups") val allowedMarketGroups: List<String> = emptyList(),
    val features: Map<String, JsonElement> = emptyMap(),
    val prices: Map<String, Price> = emptyMap(),
    @SerialName("google_product_id") val googleProductId: String? = null,
)
@Serializable data class Price(val amount: Double, val currency: String, val note: String? = null)

@Serializable data class Usage(
    val plan: String, val date: String, val timezone: String, val limit: Int? = null, val used: Int = 0,
    val remaining: Int? = null, @SerialName("resets_at") val resetsAt: String = "",
)

@Serializable data class User(
    val id: Long, val email: String, @SerialName("display_name") val displayName: String, val role: String,
    @SerialName("email_verified") val emailVerified: Boolean = false, val country: String = "CL", val language: String = "es",
    val timezone: String = "UTC", val theme: String = "system", val plan: Plan, @SerialName("subscription_status") val subscriptionStatus: String? = null,
    @SerialName("subscription_ends_at") val subscriptionEndsAt: String? = null, val usage: Usage, @SerialName("is_demo") val isDemo: Boolean = false,
) { val isPro get() = plan.code == "PRO"; val isAdmin get() = role == "ADMIN" }

@Serializable data class Tokens(
    @SerialName("access_token") val accessToken: String, @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "bearer", @SerialName("expires_in") val expiresIn: Int = 900,
)
@Serializable data class AuthResponse(val user: User, val tokens: Tokens)

@Serializable data class Team(val id: Long, val name: String, @SerialName("short_name") val shortName: String? = null, @SerialName("is_demo") val isDemo: Boolean = false)
@Serializable data class LeagueRef(val id: Long, val name: String, val country: String? = null)
@Serializable data class Fixture(
    val id: Long, val sport: String, val league: LeagueRef, @SerialName("home_team") val homeTeam: Team, @SerialName("away_team") val awayTeam: Team,
    val kickoff: String, @SerialName("kickoff_local") val kickoffLocal: String? = null, val status: String,
    @SerialName("home_score") val homeScore: Int? = null, @SerialName("away_score") val awayScore: Int? = null,
    @SerialName("is_demo") val isDemo: Boolean = false, @SerialName("data_source") val dataSource: String = "",
) { val finished get() = status == "finished" }

@Serializable data class PageOf<T>(val items: List<T>, val total: Int, val page: Int, @SerialName("page_size") val pageSize: Int)

@Serializable data class Prediction(
    val id: Long, val market: String, val selection: String, val key: String? = null, val group: String,
    val probability: Double, @SerialName("probability_pct") val probabilityPct: Double, val category: String, val uncertainty: String,
    @SerialName("data_points") val dataPoints: Int, @SerialName("updated_at") val updatedAt: String,
    @SerialName("is_pre_match") val isPreMatch: Boolean = true, val result: String? = null,
)
@Serializable data class AnalysisInfo(
    val id: Long, val status: String, val quality: String, @SerialName("n_home") val nHome: Int, @SerialName("n_away") val nAway: Int,
    @SerialName("generated_at") val generatedAt: String, @SerialName("is_pre_match") val isPreMatch: Boolean = true,
    val model: String? = null, @SerialName("model_version") val modelVersion: String? = null, @SerialName("is_demo") val isDemo: Boolean = false,
    @SerialName("category_criteria") val categoryCriteria: Map<String, String> = emptyMap(),
)
@Serializable data class Driver(val factor: String, val value: JsonElement? = null, val note: String? = null)
@Serializable data class Analysis(
    val fixture: Fixture, val disclaimer: String = "", val usage: Usage? = null, val analysis: AnalysisInfo,
    val predictions: List<Prediction> = emptyList(), @SerialName("locked_groups") val lockedGroups: List<String> = emptyList(),
    @SerialName("locked_count") val lockedCount: Int = 0, @SerialName("exact_score") val exactScore: JsonElement? = null,
    val drivers: List<Driver>? = null, @SerialName("upgrade_hint") val upgradeHint: String? = null,
    val message: String? = null, val reason: String? = null,
) { val ok get() = analysis.status == "ok" }

@Serializable data class JointResult(val computable: Boolean, val probability: Double? = null, val reason: String? = null, val notes: List<String> = emptyList())
@Serializable data class EvalItem(val key: String, val probability: Double? = null, val status: String = "ok")
@Serializable data class EvaluateResponse(val items: List<EvalItem> = emptyList(), val joint: JointResult)
@Serializable data class SelectionItemIn(@SerialName("fixture_id") val fixtureId: Long, val key: String)
@Serializable data class EvaluateRequest(val items: List<SelectionItemIn>)
@Serializable data class GroupRequest(val name: String, val items: List<SelectionItemIn>)

@Serializable data class RegisterRequest(
    val email: String, val password: String, @SerialName("display_name") val displayName: String = "", val country: String = "CL",
    val language: String = "es", val timezone: String = "UTC", @SerialName("accept_terms") val acceptTerms: Boolean,
)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RefreshRequest(@SerialName("refresh_token") val refreshToken: String)
@Serializable data class EmailRequest(val email: String)
@Serializable data class PurchaseRequest(@SerialName("purchase_token") val purchaseToken: String)
@Serializable data class RestoreRequest(@SerialName("purchase_tokens") val purchaseTokens: List<String>)
@Serializable data class AccountIdResponse(@SerialName("obfuscated_account_id") val obfuscatedAccountId: String)
@Serializable data class SubscriptionResponse(val effective: User)
@Serializable data class DeleteAccountRequest(val password: String? = null, val confirm: Boolean = true)
@Serializable data class DeviceRequest(val token: String, val platform: String = "android")
@Serializable data class FavoriteRequest(val kind: String, @SerialName("ref_id") val refId: Long)
@Serializable data class ProfilePatch(
    @SerialName("display_name") val displayName: String? = null, val language: String? = null, val theme: String? = null,
    val timezone: String? = null, val country: String? = null,
)
@Serializable data class NotificationItem(val id: Long, val kind: String, val title: String, val body: String, val read: Boolean, @SerialName("created_at") val createdAt: String)
@Serializable data class NotificationPage(val items: List<NotificationItem>, val total: Int, val unread: Int = 0)
@Serializable data class StatusInfo(
    val app: String, val version: String, val environment: String, @SerialName("provider_mode") val providerMode: String,
    @SerialName("is_demo_mode") val isDemoMode: Boolean,
)
@Serializable data class ErrorBody(val error: ErrorDetail)
@Serializable data class ErrorDetail(val code: String, val message: String, @SerialName("request_id") val requestId: String? = null, val details: JsonElement? = null)
