package cl.driverlink.app.presentation.subscription

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.AppConfig
import cl.driverlink.app.domain.model.ProOffer
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.policy.FeatureAccessPolicy
import cl.driverlink.app.domain.repository.BillingGateway
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.SubscriptionRepository
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.LoadingView
import cl.driverlink.app.ui.components.PrimaryButton
import cl.driverlink.app.ui.components.formatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val subscription: Subscription? = null,
    val effectiveStatus: SubscriptionStatus = SubscriptionStatus.FREE,
    val remainingDays: Int? = null,
    val canStartTrial: Boolean = false,
    val offer: ProOffer? = null,
    val working: Boolean = false,
    val message: UiText? = null,
)

class SubscriptionViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val billingGateway: BillingGateway,
    configRepository: ConfigRepository,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) : ViewModel() {

    private val local = MutableStateFlow(SubscriptionUiState())

    val state: StateFlow<SubscriptionUiState> = combine(
        local, subscriptionRepository.observeSubscription(), configRepository.config,
    ) { l, sub, config: AppConfig ->
        val now = clock.now()
        l.copy(
            subscription = sub,
            effectiveStatus = FeatureAccessPolicy.effectiveStatus(sub, now),
            remainingDays = FeatureAccessPolicy.remainingDays(sub, now),
            canStartTrial = FeatureAccessPolicy.canStartTrial(sub, now),
            offer = l.offer ?: ProOffer(config.premiumPriceDisplay, config.trialDays, billingAvailable = false),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionUiState())

    init {
        analytics.log(AnalyticsEvent.SUBSCRIPTION_SCREEN_OPENED)
        viewModelScope.launch {
            val offer = billingGateway.loadProOffer()
            if (offer is AppResult.Success) local.update { it.copy(offer = offer.data) }
        }
    }

    /** La activación la ejecuta el servidor; el cliente solo la solicita. */
    fun startTrial() {
        local.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            when (val result = subscriptionRepository.startTrial()) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.TRIAL_STARTED)
                    local.update { it.copy(working = false, message = UiText.Res(R.string.subscription_trial_started)) }
                }
                is AppResult.Failure -> local.update { it.copy(working = false, message = result.error.toUiText()) }
            }
        }
    }

    fun purchase() {
        local.update { it.copy(working = true, message = null) }
        viewModelScope.launch {
            val result = billingGateway.purchasePro()
            local.update {
                it.copy(
                    working = false,
                    message = if (result is AppResult.Failure) result.error.toUiText() else UiText.Res(R.string.subscription_purchase_pending),
                )
            }
        }
    }
}

fun SubscriptionStatus.labelRes(): Int = when (this) {
    SubscriptionStatus.FREE -> R.string.plan_free
    SubscriptionStatus.TRIAL -> R.string.plan_trial
    SubscriptionStatus.PRO -> R.string.plan_pro
    SubscriptionStatus.EXPIRED -> R.string.plan_expired
    SubscriptionStatus.COMPANY -> R.string.plan_company
}

@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val viewModel = appViewModel {
        SubscriptionViewModel(it.subscriptionRepository, it.billingGateway, it.configRepository, it.analytics, it.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { DriverLinkTopBar(stringResource(R.string.subscription_title), onBack = onBack) }) { padding ->
        if (state.subscription == null) {
            LoadingView(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.subscription_current_plan), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(state.effectiveStatus.labelRes()), style = MaterialTheme.typography.headlineMedium)
                    val expiresAt = state.subscription?.expiresAt
                    if (state.effectiveStatus == SubscriptionStatus.TRIAL || state.effectiveStatus == SubscriptionStatus.PRO) {
                        state.remainingDays?.let { days ->
                            Text(pluralStringResource(R.plurals.subscription_days_left, days, days))
                        }
                        if (expiresAt != null) Text(stringResource(R.string.subscription_until, formatDate(expiresAt)))
                    }
                }
            }
            if (state.effectiveStatus == SubscriptionStatus.EXPIRED) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(stringResource(R.string.premium_trial_ended), modifier = Modifier.padding(16.dp))
                }
            }

            PlanCard(
                title = stringResource(R.string.plan_community_title),
                price = stringResource(R.string.plan_community_price),
                features = listOf(
                    R.string.feature_profile, R.string.feature_chat, R.string.feature_radio, R.string.feature_map,
                    R.string.feature_alerts, R.string.feature_basic_notifications,
                ),
                highlighted = false,
            )
            PlanCard(
                title = stringResource(R.string.plan_pro_title),
                price = state.offer?.priceDisplay.orEmpty(),
                features = listOf(
                    R.string.feature_everything_free, R.string.feature_sos, R.string.feature_emergency_contacts,
                    R.string.feature_assistance,
                ),
                highlighted = true,
            )
            state.message?.let { Text(it.asString(), color = MaterialTheme.colorScheme.primary) }
            if (state.canStartTrial) {
                PrimaryButton(
                    text = pluralStringResource(R.plurals.subscription_start_trial, state.offer?.trialDays ?: 0, state.offer?.trialDays ?: 0),
                    onClick = viewModel::startTrial,
                    loading = state.working,
                )
            }
            if (state.effectiveStatus != SubscriptionStatus.PRO && state.effectiveStatus != SubscriptionStatus.COMPANY) {
                OutlinedButton(onClick = viewModel::purchase, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.subscription_buy, state.offer?.priceDisplay.orEmpty()))
                }
            }
            Text(stringResource(R.string.subscription_free_forever), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PlanCard(title: String, price: String, features: List<Int>, highlighted: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (highlighted) Icon(Icons.Filled.Star, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            Text(price, style = MaterialTheme.typography.titleMedium)
            features.forEach { feature ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(stringResource(feature))
                }
            }
        }
    }
}
