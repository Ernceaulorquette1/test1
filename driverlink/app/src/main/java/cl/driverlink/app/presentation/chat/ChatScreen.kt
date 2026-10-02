package cl.driverlink.app.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import cl.driverlink.app.R
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.MessageStatus
import cl.driverlink.app.domain.model.MessageType
import cl.driverlink.app.presentation.common.appViewModel
import cl.driverlink.app.ui.components.Avatar
import cl.driverlink.app.ui.components.DriverLinkTopBar
import cl.driverlink.app.ui.components.EmptyView
import cl.driverlink.app.ui.components.ErrorView
import cl.driverlink.app.ui.components.LoadingView
import cl.driverlink.app.ui.components.ReportDialog
import cl.driverlink.app.ui.components.formatClock
import cl.driverlink.app.ui.components.initialsOf

@Composable
fun ChatScreen(onBack: () -> Unit) {
    val viewModel = appViewModel {
        ChatViewModel(
            createSavedStateHandle(), it.channelRepository, it.messageRepository, it.sendMessageUseCase,
            it.moderationRepository, it.authRepository, it.configRepository, it.analytics,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val replyTo by viewModel.replyTo.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var reporting by remember { mutableStateOf<Message?>(null) }
    val messageText = message?.asString()

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }
    // Paginación: al acercarse al mensaje más antiguo se carga la página siguiente.
    val nearTop by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearTop, state.messages.size) { if (nearTop && state.messages.isNotEmpty()) viewModel.loadOlder() }

    Scaffold(
        topBar = { DriverLinkTopBar(state.channel?.name ?: stringResource(R.string.chat_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            Box(Modifier.weight(1f)) {
                when {
                    state.loading -> LoadingView()
                    state.error != null -> ErrorView(state.error!!.asString(), onRetry = null)
                    state.messages.isEmpty() -> EmptyView(stringResource(R.string.chat_empty))
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            MessageBubble(
                                message = msg,
                                isMine = msg.senderId == viewModel.currentUserId,
                                onReply = { viewModel.setReply(msg) },
                                onDelete = { viewModel.delete(msg) },
                                onReport = { reporting = msg },
                                onBlock = { viewModel.block(msg) },
                            )
                        }
                        if (state.loadingOlder) {
                            item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        }
                    }
                }
            }
            replyTo?.let { reply ->
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.chat_replying_to, reply.senderName, reply.text.orEmpty()),
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.setReply(null) }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = viewModel::onInput,
                    placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = viewModel::send, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.action_send))
                }
            }
        }
    }

    reporting?.let { target ->
        ReportDialog(
            onDismiss = { reporting = null },
            onSubmit = { reason, details -> reporting = null; viewModel.report(target, reason, details) },
        )
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    isMine: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isMine) Avatar(message.senderPhotoUrl, initialsOf(message.senderName), size = 36.dp)
        Surface(
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f, fill = false)) {
                        if (!isMine) Text(message.senderName, style = MaterialTheme.typography.labelLarge)
                        message.replyPreview?.let {
                            Text("↪ $it", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.chat_message_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            if (!message.isDeleted) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.chat_reply)) }, onClick = { menuOpen = false; onReply() })
                            }
                            if (isMine && !message.isDeleted) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuOpen = false; onDelete() })
                            }
                            if (!isMine) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.action_report_content)) }, onClick = { menuOpen = false; onReport() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.chat_block_user)) }, onClick = { menuOpen = false; onBlock() })
                            }
                        }
                    }
                }
                Text(
                    when {
                        message.isDeleted -> stringResource(R.string.chat_message_deleted)
                        message.type == MessageType.SYSTEM -> message.text.orEmpty()
                        else -> message.text.orEmpty()
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = 12.dp),
                )
                Row(Modifier.padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatClock(message.createdAt), style = MaterialTheme.typography.bodySmall)
                    if (isMine) MessageStatusIcon(message.status)
                }
            }
        }
    }
}

@Composable
private fun MessageStatusIcon(status: MessageStatus) {
    // El estado se expresa con ícono + descripción, no solo con color.
    when (status) {
        MessageStatus.PENDING -> Icon(
            Icons.Filled.Schedule, contentDescription = stringResource(R.string.message_status_pending),
            modifier = Modifier.padding(start = 4.dp).size(16.dp),
        )
        MessageStatus.FAILED -> Icon(
            Icons.Filled.ErrorOutline, contentDescription = stringResource(R.string.message_status_failed),
            tint = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 4.dp).size(16.dp),
        )
        MessageStatus.SENT -> Text(
            " · " + stringResource(R.string.message_status_sent),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
