package cl.driverlink.app.services.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val messageId: String? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
)

/** Reproductor de mensajes de radio. Un solo audio a la vez. */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun toggle(messageId: String, url: String) {
        val current = _state.value
        val existing = player
        when {
            current.messageId == messageId && existing != null && current.isPlaying -> {
                existing.pause()
                _state.value = current.copy(isPlaying = false)
            }
            current.messageId == messageId && existing != null && !current.isPreparing -> {
                existing.start()
                _state.value = current.copy(isPlaying = true)
            }
            else -> play(messageId, url)
        }
    }

    private fun play(messageId: String, url: String) {
        release()
        _state.value = PlaybackState(messageId = messageId, isPreparing = true)
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        try {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mediaPlayer.setDataSource(url)
            mediaPlayer.setOnPreparedListener {
                it.start()
                _state.value = PlaybackState(messageId = messageId, isPlaying = true)
            }
            mediaPlayer.setOnCompletionListener { _state.value = PlaybackState() }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                release()
                true
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            release()
        }
    }

    fun release() {
        player?.release()
        player = null
        _state.value = PlaybackState()
    }
}
