package cl.driverlink.app.domain.model

enum class ReportTarget { USER, MESSAGE, AUDIO, ALERT }

enum class ReportReason { SPAM, HARASSMENT, FALSE_INFORMATION, DANGEROUS_CONTENT, IMPERSONATION, OTHER }

data class ReportDraft(
    val targetType: ReportTarget,
    val targetId: String,
    val targetOwnerId: String?,
    val reason: ReportReason,
    val details: String,
    /** Contexto para que moderación ubique el contenido (ej. canal). */
    val contextPath: String? = null,
)

data class BlockedUser(val userId: String, val displayName: String, val blockedAt: Long)
