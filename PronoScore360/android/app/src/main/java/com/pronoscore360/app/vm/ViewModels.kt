package com.pronoscore360.app.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pronoscore360.app.billing.BillingEvent
import com.pronoscore360.app.billing.BillingManager
import com.pronoscore360.app.data.AuthEvents
import com.pronoscore360.app.data.Prefs
import com.pronoscore360.app.data.Repository
import com.pronoscore360.core.api.apiCall
import com.pronoscore360.core.model.*
import com.pronoscore360.core.util.BasketItem
import com.pronoscore360.core.util.SelectionBasket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject

sealed interface UiState<out T> {
    data object Idle : UiState<Nothing>
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val code: String? = null) : UiState<Nothing>
}

fun Throwable.toUi() = UiState.Error(message ?: "Error inesperado", (this as? com.pronoscore360.core.api.ApiException)?.code)

/** Ejecuta `block` publicando Loading → Success/Error en el flujo. */
fun <T> kotlinx.coroutines.CoroutineScope.load(state: MutableStateFlow<UiState<T>>, block: suspend () -> T) {
    state.value = UiState.Loading
    launch { state.value = try { UiState.Success(block()) } catch (e: Exception) { e.toUi() } }
}

@HiltViewModel
class SessionViewModel @Inject constructor(private val repo: Repository, private val prefs: Prefs) : ViewModel() {
    val user = MutableStateFlow<User?>(null)
    val booting = MutableStateFlow(true)
    val authError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val theme = prefs.theme.stateIn(viewModelScope, SharingStarted.Eagerly, "system")
    val demoMode = MutableStateFlow(false)

    init {
        viewModelScope.launch { AuthEvents.logout.collect { user.value = null } }
        viewModelScope.launch {
            if (repo.hasSession) user.value = try { repo.me() } catch (e: Exception) { null }
            demoMode.value = try { apiCall { repo.api.status() }.isDemoMode } catch (e: Exception) { false }
            booting.value = false
        }
    }
    private fun run(block: suspend () -> User) { busy.value = true; authError.value = null
        viewModelScope.launch { try { user.value = block() } catch (e: Exception) { authError.value = e.message } finally { busy.value = false } } }
    fun login(email: String, password: String) = run { repo.login(email, password) }
    fun register(email: String, password: String, name: String, country: String, tz: String) =
        run { repo.register(RegisterRequest(email.trim(), password, name, country, "es", tz, true)) }
    fun logout() { viewModelScope.launch { repo.logout(); user.value = null } }
    fun refresh() { viewModelScope.launch { runCatching { repo.me() }.onSuccess { user.value = it } } }
    fun setUser(u: User) { user.value = u }
    fun forgot(email: String, onDone: () -> Unit) { viewModelScope.launch { runCatching { apiCall { repo.api.forgot(EmailRequest(email.trim())) } }; onDone() } }
    fun setTheme(v: String) { viewModelScope.launch { prefs.setTheme(v); runCatching { apiCall { repo.api.patchMe(ProfilePatch(theme = v)) } } } }
    /** Eliminación real de cuenta y datos (requisito de Google Play: ruta dentro de la app). */
    fun deleteAccount(password: String, onError: (String) -> Unit) { viewModelScope.launch { try { apiCall { repo.api.deleteAccount(DeleteAccountRequest(password, true)) }; repo.clearLocalSession(); user.value = null } catch (e: Exception) { onError(e.message ?: "Error") } } }
    fun registerDevice(token: String) { viewModelScope.launch { runCatching { apiCall { repo.api.registerDevice(DeviceRequest(token)) } } } }
    fun clearError() { authError.value = null }
}

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repo: Repository) : ViewModel() {
    val fixtures = MutableStateFlow<UiState<PageOf<Fixture>>>(UiState.Idle)
    val teams = MutableStateFlow<UiState<PageOf<Team>>>(UiState.Idle)
    val teamStats = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val standings = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val analysis = MutableStateFlow<UiState<Analysis>>(UiState.Idle)
    val fixtureStats = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)

    fun loadFixtures(sport: String?, date: String?) = viewModelScope.load(fixtures) { repo.fixtures(sport, date) }
    fun searchTeams(q: String) = viewModelScope.load(teams) { apiCall { repo.api.teams(q.ifBlank { null }) } }
    fun loadTeamStats(id: Long, last: Int) = viewModelScope.load(teamStats) { apiCall { repo.api.teamStats(id, last) } }
    fun loadStandings(leagueId: Long) = viewModelScope.load(standings) { apiCall { repo.api.standings(leagueId) } }
    /** Consume UNA consulta diaria por partido distinto (el servidor decide). Sólo se llama al pulsar «Analizar». */
    fun analyze(id: Long, onDone: () -> Unit) {
        analysis.value = UiState.Loading
        viewModelScope.launch { analysis.value = try { UiState.Success(apiCall { repo.api.analysis(id) }) } catch (e: Exception) { e.toUi() }; onDone() }
    }
    fun resetAnalysis() { analysis.value = UiState.Idle }
    fun loadFixtureStats(id: Long) = viewModelScope.load(fixtureStats) { apiCall { repo.api.fixtureStats(id) } }
}

@HiltViewModel
class ListsViewModel @Inject constructor(private val repo: Repository) : ViewModel() {
    val predictions = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val favorites = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val history = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val comparison = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val notifications = MutableStateFlow<UiState<NotificationPage>>(UiState.Idle)
    val groups = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val evaluation = MutableStateFlow<UiState<EvaluateResponse>>(UiState.Idle)
    val message = MutableStateFlow<String?>(null)
    val basket = SelectionBasket(max = 12)
    val basketItems = MutableStateFlow<List<BasketItem>>(emptyList())

    fun loadPredictions(sport: String?, minProbability: Double, uncertainty: String?) =
        viewModelScope.load(predictions) { apiCall { repo.api.predictions(sport = sport, minProbability = minProbability, uncertainty = uncertainty) } }
    fun loadFavorites() = viewModelScope.load(favorites) { apiCall { repo.api.favorites() } }
    fun loadHistory() = viewModelScope.load(history) { apiCall { repo.api.history() } }
    fun loadComparison() = viewModelScope.load(comparison) { apiCall { repo.api.comparison() } }
    fun loadNotifications() = viewModelScope.load(notifications) { apiCall { repo.api.notifications() } }
    fun loadGroups() = viewModelScope.load(groups) { apiCall { repo.api.groups() } }
    fun addFavorite(kind: String, id: Long) { viewModelScope.launch { message.value = try { apiCall { repo.api.addFavorite(FavoriteRequest(kind, id)) }; "Añadido a favoritos" } catch (e: Exception) { e.message } } }
    fun removeFavorite(id: Long) { viewModelScope.launch { runCatching { apiCall { repo.api.removeFavorite(id) } }; loadFavorites() } }
    fun savePrediction(id: Long) { viewModelScope.launch { message.value = try { apiCall { repo.api.savePrediction(id) }; "Pronóstico guardado" } catch (e: Exception) { e.message } } }
    fun readAll() { viewModelScope.launch { runCatching { apiCall { repo.api.readAll() } }; loadNotifications() } }

    fun toggleBasket(i: BasketItem) { if (basket.has(i.fixtureId, i.key)) basket.remove(i.fixtureId, i.key) else if (!basket.add(i)) message.value = "No se pudo añadir (duplicado o límite alcanzado)"; basketItems.value = basket.items; evaluation.value = UiState.Idle }
    fun removeFromBasket(i: BasketItem) { basket.remove(i.fixtureId, i.key); basketItems.value = basket.items; evaluation.value = UiState.Idle }
    fun evaluate() = viewModelScope.load(evaluation) { apiCall { repo.api.evaluate(EvaluateRequest(basket.toRequest())) } }
    fun saveBasket(name: String) { viewModelScope.launch { message.value = try { apiCall { repo.api.createGroup(GroupRequest(name, basket.toRequest())) }; basket.clear(); basketItems.value = emptyList(); loadGroups(); "Selección guardada" } catch (e: Exception) { e.message } } }
    fun deleteGroup(id: Long) { viewModelScope.launch { runCatching { apiCall { repo.api.deleteGroup(id) } }; loadGroups() } }
    fun clearMessage() { message.value = null }
}

@HiltViewModel
class PlansViewModel @Inject constructor(private val repo: Repository, val billing: BillingManager) : ViewModel() {
    val plans = MutableStateFlow<UiState<List<Plan>>>(UiState.Idle)
    val subscription = MutableStateFlow<UiState<JsonElement>>(UiState.Idle)
    val event = MutableStateFlow<String?>(null)
    init { billing.attach(viewModelScope) }
    fun load() { viewModelScope.load(plans) { repo.plans() }; viewModelScope.load(subscription) { apiCall { repo.api.mySubscription() } } }
    fun loadProduct() { viewModelScope.launch { runCatching { billing.loadProduct() } } }
    fun restore() { viewModelScope.launch { billing.restore() } }
    fun demoActivate(onUser: (User) -> Unit) { viewModelScope.launch { try { onUser(apiCall { repo.api.demoActivate() }.effective); event.value = "Pro de PRUEBA activado (simulador local)" } catch (e: Exception) { event.value = e.message } } }
    suspend fun collectBilling(onUser: (User) -> Unit) {
        billing.events.collect { e -> when (e) {
            is BillingEvent.Verified -> { onUser(e.user); event.value = "Compra verificada por el servidor. ¡Plan ${e.user.plan.name} activo!" }
            is BillingEvent.Failed -> event.value = e.message
            BillingEvent.Pending -> event.value = "Pago pendiente: Pro se activará cuando Google Play lo confirme."
            BillingEvent.Canceled -> event.value = "Compra cancelada."
        } }
    }
    fun clearEvent() { event.value = null }
}
