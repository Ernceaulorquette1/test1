package cl.driverlink.app.presentation.radio

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import cl.driverlink.app.R
import cl.driverlink.app.core.time.formatDuration
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.presentation.common.AppPermissions
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.presentation.common.rememberPermissionRequest
import cl.driverlink.app.services.audio.PlaybackState
import cl.driverlink.app.ui.components.Avatar
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.EmptyView
import cl.driverlink.app.ui.components.LoadingView
import cl.driverlink.app.ui.components.formatClock
import cl.driverlink.app.ui.components.initialsOf
import kotlinx.coroutines.delay

@Composable
fun RadioScreen() {
    val viewModel = appViewModel {
        RadioViewModel(
            createSavedStateHandle(), it.channelRepository, it.messageRepository, it.sendAudioMessageUseCase,
            it.audioRecorder, it.audioPlayer, it.configRepository, it.analytics, it.crashReporter,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val record by viewModel.record.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messageText = message?.asString()
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    Scaffold(
        topBar = { DriverLinkTopBar((state.selected?.name ?: stringResource(R.string.radio_title)).uppercase()) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.channels, key = { it.id }) { channel ->
                    FilterChip(
                        selected = channel.id == state.selected?.id,
                        onClick = { viewModel.selectChannel(channel) },
                        label = { Text(channel.name) },
                        enabled = record is RecordState.Idle,
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingView()
                    state.messages.isEmpty() -> EmptyView(stringResource(R.string.radio_empty))
                    else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.messages, key = { it.id }) { msg ->
                            AudioMessageRow(msg, playback, onToggle = { viewModel.togglePlay(msg) })
                        }
                    }
                }
            }
            RecordPanel(
                record = record,
                maxSeconds = viewModel.maxDurationSeconds,
                enabled = state.selected != null,
                onStart = viewModel::startRecording,
                onStop = viewModel::stopRecording,
                onSend = viewModel::send,
                onCancel = viewModel::cancel,
            )
        }
    }
}

@Composable
private fun AudioMessageRow(message: Message, playback: PlaybackState, onToggle: () -> Unit) {
    val isCurrent = playback.messageId == message.id
    val playing = isCurrent && playback.isPlaying
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { Avatar(message.senderPhotoUrl, initialsOf(message.senderName)) },
            headlineContent = { Text(message.senderName) },
            supportingContent = {
                Text("${formatClock(message.createdAt)} · ${formatDuration(message.audioDurationMs ?: 0)}")
            },
            trailingContent = {
                if (isCurrent && playback.isPreparing) {
                    CircularProgressIndicator()
                } else {
                    IconButton(onClick = onToggle) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = stringResource(if (playing) R.string.radio_pause else R.string.radio_play),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun RecordPanel(
    record: RecordState,
    maxSeconds: Int,
    enabled: Boolean,
    onStart: (Long) -> Unit,
    onStop: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val currentRecord by rememberUpdatedState(record)
    val requestMic = rememberPermissionRequest(
        AppPermissions.MICROPHONE,
        stringResource(R.string.permission_microphone_rationale),
    ) { }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (record) {
            is RecordState.Review -> {
                Text(stringResource(R.string.radio_review, formatDuration(record.clip.durationMs)), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    Button(onClick = onSend, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Text(stringResource(R.string.action_send))
                    }
                }
            }
            RecordState.Uploading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.radio_uploading))
            }
            else -> {
                val recording = record as? RecordState.Recording
                var elapsed by remember { mutableLongStateOf(0L) }
                LaunchedEffect(recording) {
                    elapsed = 0
                    while (recording != null) {
                        elapsed = SystemClock.elapsedRealtime() - recording.startedAt
                        delay(200)
                    }
                }
                if (recording != null) {
                    Text(
                        stringResource(R.string.radio_recording, formatDuration(elapsed), formatDuration(maxSeconds * 1000L)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                val label = stringResource(if (recording != null) R.string.radio_release_to_finish else R.string.radio_hold_to_talk)
                val stateLabel = stringResource(if (recording != null) R.string.radio_state_recording else R.string.radio_state_idle)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .clip(RoundedCornerShape(48.dp))
                        .background(
                            if (!enabled) MaterialTheme.colorScheme.surfaceVariant
                            else if (recording != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        .semantics {
                            contentDescription = label
                            stateDescription = stateLabel
                        }
                        .pointerInput(enabled) {
                            if (!enabled) return@pointerInput
                            awaitEachGesture {
                                awaitFirstDown()
                                if (!AppPermissions.anyGranted(context, AppPermissions.MICROPHONE)) {
                                    requestMic()
                                    waitForUpOrCancellation()
                                    return@awaitEachGesture
                                }
                                if (currentRecord is RecordState.Idle) onStart(SystemClock.elapsedRealtime())
                                waitForUpOrCancellation()
                                onStop()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Text(label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
                if (recording != null) {
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.radio_cancel_recording)) }
                }
            }
        }
    }
}
