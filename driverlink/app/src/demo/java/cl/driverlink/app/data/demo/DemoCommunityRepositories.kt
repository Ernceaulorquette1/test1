package cl.driverlink.app.data.demo

import cl.driverlink.app.core.result.AppError
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertDraft
import cl.driverlink.app.domain.model.AlertStatus
import cl.driverlink.app.domain.model.AlertVote
import cl.driverlink.app.domain.model.AudioClip
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelKey
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.ChannelType
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.MessageStatus
import cl.driverlink.app.domain.model.MessageType
import cl.driverlink.app.domain.policy.CompanyPermission
import cl.driverlink.app.domain.policy.RolePolicy
import cl.driverlink.app.domain.repository.AlertRepository
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.File

class DemoChannelRepository(private val b: DemoBackend) : ChannelRepository {

    override fun observeCommunityChannels(mode: ChannelMode): Flow<List<Channel>> = b.channels.map { list ->
        list.filter { it.companyId == null && it.mode == mode && it.isActive }.sortedBy { it.order }
    }

    override fun observeCompanyChannels(companyId: String, mode: ChannelMode): Flow<List<Channel>> =
        combine(b.channels, b.members, b.currentUserId) { list, members, uid ->
            // Igual que las reglas: solo miembros ACTIVE ven los canales privados.
            val member = uid?.let { members[companyId]?.get(it) }
            if (member?.status != MemberStatus.ACTIVE) emptyList()
            else list.filter { it.companyId == companyId && it.mode == mode && it.isActive }.sortedBy { it.order }
        }

    override fun observeChannel(key: ChannelKey): Flow<Channel?> = b.channels.map { list -> list.firstOrNull { it.key == key } }

    override suspend fun createCompanyChannel(companyId: String, name: String, mode: ChannelMode): AppResult<String> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        val member = b.members.value[companyId]?.get(uid)
        if (!RolePolicy.has(member, CompanyPermission.MANAGE_CHANNELS)) return AppResult.Failure(AppError.PermissionDenied)
        val id = b.newId()
        b.channels.update {
            it + Channel(id, name, "", ChannelType.PRIVATE_COMPANY, mode, "", "", null, companyId, it.size, true, null)
        }
        return AppResult.Success(id)
    }
}

class DemoMessageRepository(private val b: DemoBackend) : MessageRepository {

    override fun observeLatest(key: ChannelKey, limit: Int): Flow<List<Message>> =
        b.messages.map { all -> (all[key] ?: emptyList()).sortedByDescending { it.createdAt }.take(limit) }

    override suspend fun loadOlder(key: ChannelKey, beforeCreatedAt: Long, limit: Int): AppResult<List<Message>> {
        b.latency()
        val page = (b.messages.value[key] ?: emptyList())
            .filter { it.createdAt < beforeCreatedAt }
            .sortedByDescending { it.createdAt }
            .take(limit)
        return AppResult.Success(page)
    }

    override suspend fun sendText(key: ChannelKey, text: String, replyTo: Message?): AppResult<Unit> {
        val user = b.currentUser() ?: return AppResult.Failure(AppError.Unauthenticated)
        if (!canWrite(key, user.id)) return AppResult.Failure(AppError.PermissionDenied)
        b.addMessage(
            Message(
                id = b.newId(), channelId = key.id, companyId = key.companyId, senderId = user.id,
                senderName = user.displayName, senderPhotoUrl = user.photoUrl, type = MessageType.TEXT, text = text,
                audioUrl = null, audioDurationMs = null, replyToId = replyTo?.id,
                replyPreview = replyTo?.let { "${it.senderName}: ${it.text.orEmpty().take(60)}" },
                createdAt = b.clock.now(), deletedAt = null, status = MessageStatus.SENT,
            )
        )
        return AppResult.Success(Unit)
    }

    override suspend fun sendAudio(key: ChannelKey, clip: AudioClip): AppResult<Unit> {
        val user = b.currentUser() ?: return AppResult.Failure(AppError.Unauthenticated)
        if (!canWrite(key, user.id)) return AppResult.Failure(AppError.PermissionDenied)
        b.latency()
        // "Subida" local: se copia el audio al almacenamiento interno de la demo.
        val source = File(clip.localPath)
        val target = File(b.audioDir.apply { mkdirs() }, source.name)
        source.copyTo(target, overwrite = true)
        source.delete()
        b.addMessage(
            Message(
                id = b.newId(), channelId = key.id, companyId = key.companyId, senderId = user.id,
                senderName = user.displayName, senderPhotoUrl = user.photoUrl, type = MessageType.AUDIO, text = null,
                audioUrl = target.absolutePath, audioDurationMs = clip.durationMs, replyToId = null, replyPreview = null,
                createdAt = b.clock.now(), deletedAt = null, status = MessageStatus.SENT,
            )
        )
        return AppResult.Success(Unit)
    }

    override suspend fun deleteOwn(key: ChannelKey, messageId: String): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        val message = b.messages.value[key]?.firstOrNull { it.id == messageId } ?: return AppResult.Failure(AppError.NotFound)
        if (message.senderId != uid) return AppResult.Failure(AppError.PermissionDenied)
        b.messages.update { all ->
            all + (key to all.getValue(key).map {
                if (it.id == messageId) it.copy(text = null, deletedAt = b.clock.now()) else it
            })
        }
        return AppResult.Success(Unit)
    }

    private fun canWrite(key: ChannelKey, uid: String): Boolean {
        val companyId = key.companyId ?: return true
        val member = b.members.value[companyId]?.get(uid)
        return RolePolicy.has(member, CompanyPermission.USE_COMPANY_CHANNELS)
    }
}

class DemoAlertRepository(private val b: DemoBackend) : AlertRepository {

    override fun observeActiveAlerts(city: String?): Flow<List<Alert>> = b.alerts.map { map ->
        val now = b.clock.now()
        map.values.filter { it.isActiveAt(now) && (city.isNullOrBlank() || it.city.equals(city, ignoreCase = true)) }
            .sortedByDescending { it.createdAt }
    }

    override fun observeAlert(alertId: String): Flow<Alert?> = b.alerts.map { it[alertId] }

    override suspend fun createAlert(draft: AlertDraft, expiresAt: Long): AppResult<String> {
        val user = b.currentUser() ?: return AppResult.Failure(AppError.Unauthenticated)
        b.latency()
        val now = b.clock.now()
        val id = b.newId()
        b.alerts.update {
            it + (id to Alert(
                id = id, creatorId = user.id, creatorName = user.displayName, category = draft.category,
                description = draft.description, location = draft.location, city = draft.city.ifBlank { "Santiago" },
                commune = draft.commune, status = AlertStatus.ACTIVE, confirmationsCount = 0, endedCount = 0,
                incorrectCount = 0, createdAt = now, expiresAt = expiresAt, updatedAt = now,
            ))
        }
        return AppResult.Success(id)
    }

    override suspend fun vote(alertId: String, vote: AlertVote): AppResult<Unit> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        val alert = b.alerts.value[alertId] ?: return AppResult.Failure(AppError.NotFound)
        if (alert.creatorId == uid) return AppResult.Failure(AppError.PermissionDenied)
        if (b.votes.value[alertId]?.containsKey(uid) == true) return AppResult.Failure(AppError.AlreadyVoted)
        b.latency()
        b.votes.update { it + (alertId to ((it[alertId] ?: emptyMap()) + (uid to vote))) }
        // Contadores y auto-resolución: en producción lo hace la Cloud Function onAlertVote.
        val threshold = b.config.value.alertResolveThreshold
        b.alerts.update { map ->
            val a = map[alertId] ?: return@update map
            var updated = when (vote) {
                AlertVote.CONFIRM -> a.copy(confirmationsCount = a.confirmationsCount + 1)
                AlertVote.ENDED -> a.copy(endedCount = a.endedCount + 1)
                AlertVote.INCORRECT -> a.copy(incorrectCount = a.incorrectCount + 1)
            }
            if (updated.endedCount >= threshold) updated = updated.copy(status = AlertStatus.RESOLVED)
            if (updated.incorrectCount >= threshold) updated = updated.copy(status = AlertStatus.HIDDEN)
            map + (alertId to updated.copy(updatedAt = b.clock.now()))
        }
        return AppResult.Success(Unit)
    }

    override suspend fun myVote(alertId: String): AppResult<AlertVote?> {
        val uid = b.currentUserId.value ?: return AppResult.Failure(AppError.Unauthenticated)
        return AppResult.Success(b.votes.value[alertId]?.get(uid))
    }
}
