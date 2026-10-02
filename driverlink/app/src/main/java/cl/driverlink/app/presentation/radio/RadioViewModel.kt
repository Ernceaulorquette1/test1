package cl.driverlink.app.presentation.radio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.driverlink.app.R
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker
import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.ui.UiText
import cl.driverlink.app.core.ui.toUiText
import cl.driverlink.app.domain.model.AudioClip
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.MessageType
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.MessageRepository
import cl.driverlink.app.domain.usecase.SendAudioMessageUseCase
import cl.driverlink.app.navigation.Routes
import cl.driverlink.app.services.audio.AudioPlayer
import cl.driverlink.app.services.audio.AudioRecorder
import cl.driverlink.app.services.audio.PlaybackState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface RecordState {
    data object Idle : RecordState
    data class Recording(val startedAt: Long) : RecordState
    /** Grabación terminada: el usuario decide Enviar o Cancelar antes de subir. */
    data class Review(val clip: AudioClip) : RecordState
    data object Uploading : RecordState
}

data class RadioUiState(
    val channels: List<Channel> = emptyList(),
    val selected: Channel? = null,
    val messages: List<Message> = emptyList(),
    val loading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RadioViewModel(
    savedStateHandle: SavedStateHandle,
    channelRepository: ChannelRepository,
    private val messageRepository: MessageRepository,
    private val sendAudio: SendAudioMessageUseCase,
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
    configRepository: ConfigRepository,
    private val analytics: AnalyticsTracker,
    private val crashReporter: CrashReporter,
) : ViewModel() {

    private val companyId: String? = savedStateHandle[Routes.ARG_COMPANY_ID]
    private val selectedId = MutableStateFlow<String?>(savedStateHandle[Routes.ARG_CHANNEL_ID])
    val maxDurationSeconds: Int = configRepository.config.value.maxAudioDurationSeconds

    private val _record = MutableStateFlow<RecordState>(RecordState.Idle)
    val record: StateFlow<RecordState> = _record.asStateFlow()
    private val _message = MutableStateFlow<UiText?>(null)
    val message: StateFlow<UiText?> = _message.asStateFlow()
    val playback: StateFlow<PlaybackState> = player.state

    private val channels = if (companyId != null) {
        channelRepository.observeCompanyChannels(companyId, ChannelMode.RADIO)
    } else {
        channelRepository.observeCommunityChannels(ChannelMode.RADIO)
    }

    private val selectedChannel = combine(channels, selectedId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    val state: StateFlow<RadioUiState> = combine(channels, selectedChannel) { list, selected -> list to selected }
        .flatMapLatest { (list, selected) ->
            if (selected == null) flowOf(RadioUiState(channels = list, loading = false))
            else messageRepository.observeLatest(selected.key, LIMIT).map { messages ->
                RadioUiState(
                    channels = list,
                    selected = selected,
                    messages = messages.filter { it.type == MessageType.AUDIO && !it.isDeleted },
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RadioUiState())

    fun selectChannel(channel: Channel) {
        selectedId.value = channel.id
    }

    fun startRecording(startedAt: Long) {
        if (_record.value !is RecordState.Idle) return
        try {
            recorder.start(maxDurationSeconds * 1000) { stopRecording() }
            _record.value = RecordState.Recording(startedAt)
        } catch (e: Exception) {
            crashReporter.recordException(e)
            _record.value = RecordState.Idle
            _message.value = UiText.Res(R.string.radio_record_error)
        }
    }

    fun stopRecording() {
        if (_record.value !is RecordState.Recording) return
        val clip = recorder.stop()
        _record.value = if (clip != null && clip.durationMs >= SendAudioMessageUseCase.MIN_DURATION_MS) {
            RecordState.Review(clip)
        } else {
            clip?.let { File(it.localPath).delete() }
            _message.value = UiText.Res(R.string.radio_too_short)
            RecordState.Idle
        }
    }

    fun cancel() {
        when (val current = _record.value) {
            is RecordState.Recording -> recorder.cancel()
            is RecordState.Review -> File(current.clip.localPath).delete()
            else -> Unit
        }
        _record.value = RecordState.Idle
    }

    fun send() {
        val review = _record.value as? RecordState.Review ?: return
        val channel = state.value.selected ?: return
        _record.value = RecordState.Uploading
        viewModelScope.launch {
            when (val result = sendAudio(channel.key, review.clip)) {
                is AppResult.Success -> {
                    analytics.log(AnalyticsEvent.RADIO_MESSAGE_SENT, mapOf("seconds" to (review.clip.durationMs / 1000).toString()))
                    _record.value = RecordState.Idle
                }
                is AppResult.Failure -> {
                    // Se conserva el audio para reintentar: la acción quedó pendiente, no se perdió.
                    _record.value = review
                    _message.value = result.error.toUiText()
                }
            }
        }
    }

    fun togglePlay(message: Message) {
        val url = message.audioUrl ?: return
        player.toggle(message.id, url)
    }

    fun messageShown() { _message.value = null }

    override fun onCleared() {
        if (_record.value is RecordState.Recording) recorder.cancel()
        player.release()
    }

    private companion object { const val LIMIT = 30 }
}
