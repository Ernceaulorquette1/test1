package cl.driverlink.app.domain.usecase

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.AudioClip
import cl.driverlink.app.domain.model.ChannelKey
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.repository.ConfigRepository
import cl.driverlink.app.domain.repository.MessageRepository

class SendMessageUseCase(private val messageRepository: MessageRepository) {
    suspend operator fun invoke(key: ChannelKey, text: String, replyTo: Message?): AppResult<Unit> {
        val clean = text.trim()
        if (clean.isEmpty() || clean.length > MAX_LENGTH) return AppResult.Failure(AppError.Validation("TEXT"))
        return messageRepository.sendText(key, clean, replyTo)
    }

    companion object {
        const val MAX_LENGTH = 1000
    }
}

class SendAudioMessageUseCase(
    private val messageRepository: MessageRepository,
    private val configRepository: ConfigRepository,
) {
    suspend operator fun invoke(key: ChannelKey, clip: AudioClip): AppResult<Unit> {
        val maxMs = configRepository.config.value.maxAudioDurationSeconds * 1000L
        if (clip.durationMs < MIN_DURATION_MS) return AppResult.Failure(AppError.Validation("AUDIO_TOO_SHORT"))
        // Se tolera un pequeño margen por la latencia del grabador.
        if (clip.durationMs > maxMs + 500) return AppResult.Failure(AppError.Validation("AUDIO_TOO_LONG"))
        return messageRepository.sendAudio(key, clip)
    }

    companion object {
        const val MIN_DURATION_MS = 700L
    }
}
