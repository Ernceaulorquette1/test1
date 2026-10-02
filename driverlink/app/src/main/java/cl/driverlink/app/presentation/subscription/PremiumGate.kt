package cl.driverlink.app.presentation.subscription

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.core.time.Clock
import cl.driverlink.app.domain.model.PremiumFeature
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.policy.FeatureAccessPolicy
import cl.driverlink.app.domain.repository.SubscriptionRepository
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.LoadingView
import cl.driverlink.app.ui.components.PremiumLockedView
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface GateState {
    data object Loading : GateState
    data object Allowed : GateState
    data class Locked(val expired: Boolean) : GateState
}

class PremiumGateViewModel(
    subscriptionRepository: SubscriptionRepository,
    feature: PremiumFeature,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<GateState> = subscriptionRepository.observeSubscription()
        .map { sub ->
            val now = clock.now()
            when {
                FeatureAccessPolicy.hasAccess(sub, feature, now) -> GateState.Allowed
                else -> GateState.Locked(expired = FeatureAccessPolicy.effectiveStatus(sub, now) == SubscriptionStatus.EXPIRED)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GateState.Loading)
}

/**
 * Protege una pantalla Premium. Aunque alguien navegue directo a la ruta, sin acceso
 * válido (emitido por el servidor) solo verá la vista de bloqueo. El servidor vuelve
 * a validar cada operación Premium.
 */
@Composable
fun PremiumGate(
    feature: PremiumFeature,
    lockedTitle: String,
    onSeePlans: () -> Unit,
    lockedExtra: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val viewModel = appViewModel(key = "gate_${feature.name}") { PremiumGateViewModel(it.subscriptionRepository, feature, it.clock) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        GateState.Loading -> LoadingView()
        GateState.Allowed -> content()
        is GateState.Locked -> PremiumLockedView(lockedTitle, onSeePlans, expired = s.expired, extraContent = lockedExtra)
    }
}
