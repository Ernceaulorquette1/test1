package cl.driverlink.app.presentation.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiState
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.model.ReportDraft
import cl.driverlink.app.domain.model.ReportReason
import cl.driverlink.app.domain.model.ReportTarget
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.ModerationRepository
import cl.driverlink.app.domain.usecase.VoteAlertUseCase
import cl.driverlink.app.navigation.Routes
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.ReportDialog
import cl.driverlink.app.ui.components.StateContent
import cl.driverlink.app.ui.components.formatClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val alertRepository: AlertRepository,
    private val voteAlert: VoteAlertUseCase,
    private val moderationRepository: ModerationRepository,
    private val analytics: AnalyticsTracker,
) : ViewModel() {

    private val alertId: String = checkNotNull(savedStateHandle[Routes.ARG_ALERT_ID])

    val alert: StateFlow<UiState<Alert>> = alertRepository.observeAlert(alertId)
        .map { if (it == null) UiState.Empty else UiState.Success(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    private val _myVote = MutableStateFlow<AlertVote?>(null)
    val myVote: StateFlow<AlertVote?> = _myVote.asStateFlow()

    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            (alertRepository.myVote(alertId) as? AppResult.Success)?.data?.let { _myVote.value = it }
        }
    }

    fun vote(vote: AlertVote) {
        viewModelScope.launch {
            when (val result = voteAlert(alertId, vote)) {
                is AppResult.Success -> {
                    _myVote.value = vote
                    if (vote == AlertVote.CONFIRM) analytics.log(AnalyticsEvent.ALERT_CONFIRMED)
                    _message.value = UiText.Res(R.string.alert_vote_thanks)
                }
                is AppResult.Failure -> _message.value = result.error.toUiText()
            }
        }
    }

    fun report(alert: Alert, reason: ReportReason, details: String) {
        viewModelScope.launch {
            val result = moderationRepository.report(
                ReportDraft(ReportTarget.ALERT, alert.id, alert.creatorId, reason, details, contextPath = "alerts/${alert.id}")
            )
            _message.value = when (result) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.CONTENT_REPORTED, mapOf("target" to "alert"))
                    UiText.Res(R.string.report_sent)
                }
                is AppResult.Failure -> result.error.toUiText()
            }
        }
    }

    fun messageShown() { _message.value = null }
}

@Composable
fun AlertDetailScreen(onBack: () -> Unit) {
    val viewModel = appViewModel {
        AlertDetailViewModel(createSavedStateHandle(), it.alertRepository, it.voteAlertUseCase, it.moderationRepository, it.analytics)
    }
    val alertState by viewModel.alert.collectAsStateWithLifecycle()
    val myVote by viewModel.myVote.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val currentUserId = cl.driverlink.app.presentation.common.appContainer().authRepository.currentUserId()
    val snackbar = remember { SnackbarHostState() }
    var reporting by remember { mutableStateOf(false) }
    val messageText = message?.asString()

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = {
            DriverLinkTopBar(stringResource(R.string.alert_detail_title), onBack = onBack) {
                IconButton(onClick = { reporting = true }) {
                    Icon(Icons.Filled.Flag, contentDescription = stringResource(R.string.action_report_content))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        StateContent(alertState, emptyMessage = stringResource(R.string.alert_not_found), modifier = Modifier.padding(padding)) { alert ->
            Column(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AlertSummary(alert)
                Text(
                    stringResource(R.string.alert_detail_meta, alert.commune.ifBlank { alert.city }, formatClock(alert.expiresAt)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.alert_detail_counts, alert.confirmationsCount, alert.endedCount, alert.incorrectCount),
                    style = MaterialTheme.typography.bodyMedium,
                )
                AlertVoteButtons(myVote = myVote, onVote = viewModel::vote, isOwnAlert = alert.creatorId == currentUserId)
            }
            if (reporting) {
                ReportDialog(
                    onDismiss = { reporting = false },
                    onSubmit = { reason, details -> reporting = false; viewModel.report(alert, reason, details) },
                )
            }
        }
    }
}
