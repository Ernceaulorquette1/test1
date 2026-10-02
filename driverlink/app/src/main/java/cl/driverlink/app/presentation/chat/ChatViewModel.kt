package cl.driverlink.app.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelKey
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.ReportDraft
import cl.driverlink.app.domain.model.ReportReason
import cl.driverlink.app.domain.model.ReportTarget
import cl.driverlink.app.domain.model.MessageType
import cl.driverlink.app.domain.repository.AuthRepository
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.MessageRepository
import cl.driverlink.app.domain.repository.ModerationRepository
import cl.driverlink.app.domain.usecase.SendMessageUseCase
import cl.driverlink.app.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val channel: Channel? = null,
    /** Mensajes del más nuevo al más antiguo (la lista se dibuja invertida). */
    val messages: List<Message> = emptyList(),
    val loading: Boolean = true,
    val loadingOlder: Boolean = false,
    val hasMore: Boolean = true,
    val error: UiText? = null,
)

class ChatViewModel(
    savedStateHandle: SavedStateHandle,
    channelRepository: ChannelRepository,
    private val messageRepository: MessageRepository,
    private val sendMessage: SendMessageUseCase,
    private val moderationRepository: ModerationRepository,
    private val authRepository: AuthRepository,
    configRepository: ConfigRepository,
    analytics: AnalyticsTracker,
) : ViewModel() {

    val key = ChannelKey(
        id = checkNotNull(savedStateHandle[Routes.ARG_CHANNEL_ID]),
        companyId = savedStateHandle[Routes.ARG_COMPANY_ID],
    )
    private val pageSize = configRepository.config.value.chatPageSize
    val currentUserId: String? get() = authRepository.currentUserId()

    private val older = MutableStateFlow<List<Message>>(emptyList())
    private val paging = MutableStateFlow(PagingState())

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()
    private val _replyTo = MutableStateFlow<Message?>(null)
    val replyTo: StateFlow<Message?> = _replyTo.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()

    private val blockedIds = moderationRepository.observeBlockedUsers()
        .map { list -> list.map { it.userId }.toSet() }
        .onStart { emit(emptySet()) }

    val state: StateFlow<ChatUiState> = combine(
        channelRepository.observeChannel(key),
        messageRepository.observeLatest(key, pageSize),
        older,
        paging,
        blockedIds,
    ) { channel, latest, olderMessages, paging, blocked ->
        val merged = (latest + olderMessages)
            .distinctBy { it.id }
            .filter { it.senderId !in blocked && it.type != MessageType.AUDIO }
            .sortedByDescending { it.createdAt }
        ChatUiState(
            channel = channel,
            messages = merged,
            loading = false,
            loadingOlder = paging.loading,
            hasMore = paging.hasMore && latest.size >= pageSize,
        )
    }
        .catch { emit(ChatUiState(loading = false, error = UiText.Res(R.string.error_load_messages))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    init {
        analytics.log(AnalyticsEvent.CHANNEL_OPENED, mapOf("type" to if (key.isCompany) "company" else "community"))
    }

    fun onInput(value: String) { _input.value = value.take(SendMessageUseCase.MAX_LENGTH) }

    fun setReply(message: Message?) { _replyTo.value = message }

    fun send() {
        val text = _input.value
        if (text.isBlank()) return
        val reply = _replyTo.value
        _input.value = ""
        _replyTo.value = null
        viewModelScope.launch {
            val result = sendMessage(key, text, reply)
            if (result is AppResult.Failure) {
                // Se devuelve el texto para que el usuario no lo pierda.
                _input.value = text
                _replyTo.value = reply
                _message.value = UiText.Res(R.string.chat_send_failed)
            }
        }
    }

    /** Paginación: carga la siguiente página de mensajes antiguos. */
    fun loadOlder() {
        val current = state.value
        if (paging.value.loading || !current.hasMore) return
        val oldest = current.messages.lastOrNull() ?: return
        paging.update { it.copy(loading = true) }
        viewModelScope.launch {
            when (val result = messageRepository.loadOlder(key, oldest.createdAt, pageSize)) {
                is AppResult.Success -> {
                    older.update { it + result.data }
                    paging.value = PagingState(loading = false, hasMore = result.data.size >= pageSize)
                }
                is AppResult.Failure -> {
                    paging.update { it.copy(loading = false) }
                    _message.value = result.error.toUiText()
                }
            }
        }
    }

    fun delete(message: Message) {
        viewModelScope.launch {
            val result = messageRepository.deleteOwn(key, message.id)
            if (result is AppResult.Failure) _message.value = result.error.toUiText()
        }
    }

    fun report(message: Message, reason: ReportReason, details: String) {
        viewModelScope.launch {
            val path = if (key.companyId != null) "companies/${key.companyId}/channels/${key.id}" else "channels/${key.id}"
            val result = moderationRepository.report(
                ReportDraft(ReportTarget.MESSAGE, message.id, message.senderId, reason, details, contextPath = path)
            )
            _message.value = if (result is AppResult.Success) UiText.Res(R.string.report_sent) else UiText.Res(R.string.error_unknown)
        }
    }

    fun block(message: Message) {
        viewModelScope.launch {
            val result = moderationRepository.blockUser(message.senderId, message.senderName)
            _message.value = if (result is AppResult.Success) UiText.Res(R.string.user_blocked, listOf(message.senderName))
            else UiText.Res(R.string.error_unknown)
        }
    }

    fun messageShown() { _message.value = null }

    private data class PagingState(val loading: Boolean = false, val hasMore: Boolean = true)
}
