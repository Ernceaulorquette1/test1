package cl.driverlink.app.domain.model

enum class MessageType { TEXT, AUDIO, IMAGE_FUTURE, SYSTEM }

/** Estado de envío visible para el autor. */
enum class MessageStatus { PENDING, SENT, FAILED }

data class Message(
    val id: String,
    val channelId: String,
    val companyId: String?,
    val senderId: String,
    val senderName: String,
    val senderPhotoUrl: String?,
    val type: MessageType,
    val text: String?,
    val audioUrl: String?,
    val audioDurationMs: Long?,
    val replyToId: String?,
    val replyPreview: String?,
    val createdAt: Long,
    val deletedAt: Long?,
    val status: MessageStatus,
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/** Grabación local lista para publicarse en un canal de radio. */
data class AudioClip(val localPath: String, val durationMs: Long)
