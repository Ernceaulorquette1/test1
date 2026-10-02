package cl.driverlink.app.presentation.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cl.driverlink.app.R
import cl.driverlink.app.presentation.alerts.AlertSummary
import cl.driverlink.app.presentation.alerts.AlertVoteButtons
import cl.driverlink.app.presentation.common.AppPermissions
import cl.driverlink.app.presentation.common.appContainer
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.common.rememberPermissionRequest
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.LoadingView
import cl.driverlink.app.ui.map.AlertMap

@Composable
fun MapScreen(onAlertDetail: (String) -> Unit) {
    val viewModel = appViewModel {
        MapViewModel(it.userRepository, it.alertRepository, it.voteAlertUseCase, it.locationProvider, it.analytics, it.clock)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentUserId = appContainer().authRepository.currentUserId()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()

    val requestLocation = rememberPermissionRequest(
        AppPermissions.LOCATION,
        stringResource(R.string.permission_location_map_rationale),
        viewModel::onLocationPermission,
    )
    LaunchedEffect(Unit) {
        if (AppPermissions.anyGranted(context, AppPermissions.LOCATION)) viewModel.onLocationPermission(true)
        else requestLocation()
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { DriverLinkTopBar(stringResource(R.string.map_title)) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.loading) {
                LoadingView()
            } else {
                AlertMap(
                    alerts = state.alerts,
                    center = state.center,
                    myLocationEnabled = state.myLocationEnabled,
                    onAlertClick = viewModel::select,
                )
            }
            state.selected?.let { alert ->
                Card(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Box(Modifier.fillMaxWidth()) {
                            AlertSummary(alert)
                            IconButton(onClick = { viewModel.select(null) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                            }
                        }
                        AlertVoteButtons(
                            myVote = null,
                            onVote = { vote -> viewModel.vote(alert, vote) },
                            isOwnAlert = alert.creatorId == currentUserId,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        TextButton(onClick = { onAlertDetail(alert.id) }) { Text(stringResource(R.string.alert_see_detail)) }
                    }
                }
            }
        }
    }
}
