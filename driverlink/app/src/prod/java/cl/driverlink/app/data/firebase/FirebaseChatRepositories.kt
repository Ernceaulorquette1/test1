package cl.driverlink.app.data.firebase

import android.net.Uri
import cl.driverlink.app.core.logging.CrashReporter
import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.core.result.safeCall
import cl.driverlink.app.domain.model.AudioClip
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelKey
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.ChannelType
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.MessageStatus
import cl.driverlink.app.domain.model.MessageType
import cl.driverlink.app.domain.repository.ChannelRepository
import cl.driverlink.app.domain.repository.MessageRepository
import cl.driverlink.app.domain.repository.UserRepository
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.io.File

class FirebaseChannelRepository(
    private val db: FirebaseFirestore,
    private val crash: CrashReporter,
) : ChannelRepository {

    /** Canales comunitarios: solo activos y del modo pedido (índice: active+mode+order). */
    override fun observeCommunityChannels(mode: ChannelMode): Flow<List<Channel>> =
        db.collection(Paths.CHANNELS)
            .whereEqualTo("active", true)
            .whereEqualTo("mode", mode.name)
            .orderBy("order")
            .observe(crash, emptyList()) { snap -> snap.documents.mapNotNull { it.toChannel(null) } }

    /** Canales privados: las reglas solo permiten leerlos a miembros ACTIVE de la empresa. */
    override fun observeCompanyChannels(companyId: String, mode: ChannelMode): Flow<List<Channel>> =
        db.collection(Paths.COMPANIES).document(companyId).collection(Paths.CHANNELS)
            .whereEqualTo("active", true)
            .whereEqualTo("mode", mode.name)
            .orderBy("order")
            .observe(crash, emptyList()) { snap -> snap.documents.mapNotNull { it.toChannel(companyId) } }

    override fun observeChannel(key: ChannelKey): Flow<Channel?> =
        channelRef(db, key).observe(crash, null) { it.toChannel(key.companyId) }

    override suspend fun createCompanyChannel(companyId: String, name: String, mode: ChannelMode): AppResult<String> =
        safeCall(FirebaseErrorMapper) {
            val ref = db.collection(Paths.COMPANIES).document(companyId).collection(Paths.CHANNELS).document()
            ref.set(
                mapOf(
                    "name" to name, "description" to "", "type" to ChannelType.PRIVATE_COMPANY.name,
                    "mode" to mode.name, "order" to 100, "active" to true, "createdAt" to FieldValue.serverTimestamp(),
                )
            ).await()
            ref.id
        }
}

fun channelRef(db: FirebaseFirestore, key: ChannelKey) =
    if (key.companyId != null) db.collection(Paths.COMPANIES).document(key.companyId).collection(Paths.CHANNELS).document(key.id)
    else db.collection(Paths.CHANNELS).document(key.id)

fun messagesRef(db: FirebaseFirestore, key: ChannelKey): CollectionReference = channelRef(db, key).collection(Paths.MESSAGES)

fun DocumentSnapshot.toChannel(companyId: String?): Channel? {
    if (!exists()) return null
    return Channel(
        id = id,
        name = str("name"),
        description = str("description"),
        type = enum("type", ChannelType.COMMUNITY),
        mode = enum("mode", ChannelMode.TEXT),
        region = str("region"),
        city = str("city"),
        zone = getString("zone"),
        companyId = companyId,
        order = int("order"),
        isActive = getBoolean("active") ?: true,
        lastMessageAt = millis("lastMessageAt"),
    )
}

class FirebaseMessageRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val users: UserRepository,
    private val crash: CrashReporter,
) : MessageRepository {

    /** Listener solo sobre los últimos [limit] mensajes del canal: nunca todo el historial. */
    override fun observeLatest(key: ChannelKey, limit: Int): Flow<List<Message>> =
        messagesRef(db, key)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .observe(crash, emptyList()) { snap -> snap.documents.mapNotNull { it.toMessage(key) } }

    override suspend fun loadOlder(key: ChannelKey, beforeCreatedAt: Long, limit: Int): AppResult<List<Message>> =
        safeCall(FirebaseErrorMapper) {
            messagesRef(db, key)
                .whereLessThan("createdAt", beforeCreatedAt.toTimestamp())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get().await()
                .documents.mapNotNull { it.toMessage(key) }
        }

    /**
     * Escritura con soporte offline: Firestore la encola y el mensaje aparece como PENDING
     * (hasPendingWrites) hasta confirmarse en servidor.
     */
    override suspend fun sendText(key: ChannelKey, text: String, replyTo: Message?): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val user = users.observeCurrentUser().filterNotNull().first()
        val data = mutableMapOf<String, Any?>(
            "senderId" to user.id,
            "senderName" to user.displayName,
            "senderPhotoUrl" to user.photoUrl,
            "type" to MessageType.TEXT.name,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
        )
        if (replyTo != null) {
            data["replyToId"] = replyTo.id
            data["replyPreview"] = "${replyTo.senderName}: ${replyTo.text.orEmpty().take(60)}"
        }
        // No se espera la confirmación del servidor para no bloquear la UI sin conexión.
        messagesRef(db, key).add(data)
        Unit
    }

    /** Sube el audio a Storage y luego publica el mensaje. Si la subida falla, no se publica. */
    override suspend fun sendAudio(key: ChannelKey, clip: AudioClip): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        val user = users.observeCurrentUser().filterNotNull().first()
        val ref = messagesRef(db, key).document()
        val path = if (key.companyId != null) "audio/companies/${key.companyId}/${key.id}/${user.id}/${ref.id}.m4a"
        else "audio/community/${key.id}/${user.id}/${ref.id}.m4a"
        val storageRef = storage.reference.child(path)
        val file = File(clip.localPath)
        storageRef.putFile(Uri.fromFile(file), StorageMetadata.Builder().setContentType("audio/mp4").build()).await()
        val url = storageRef.downloadUrl.await().toString()
        ref.set(
            mapOf(
                "senderId" to user.id,
                "senderName" to user.displayName,
                "senderPhotoUrl" to user.photoUrl,
                "type" to MessageType.AUDIO.name,
                "audioUrl" to url,
                "audioPath" to path,
                "audioDurationMs" to clip.durationMs,
                "createdAt" to FieldValue.serverTimestamp(),
            )
        ).await()
        file.delete()
        Unit
    }

    /** Borrado lógico: solo el autor, y solo los campos permitidos por las reglas. */
    override suspend fun deleteOwn(key: ChannelKey, messageId: String): AppResult<Unit> = safeCall(FirebaseErrorMapper) {
        messagesRef(db, key).document(messageId)
            .update(mapOf("deletedAt" to FieldValue.serverTimestamp(), "text" to null))
            .await()
        Unit
    }

    private fun DocumentSnapshot.toMessage(key: ChannelKey): Message? {
        if (!exists()) return null
        if (getBoolean("hidden") == true) return null // ocultado por moderación
        return Message(
            id = id,
            channelId = key.id,
            companyId = key.companyId,
            senderId = str("senderId"),
            senderName = str("senderName"),
            senderPhotoUrl = getString("senderPhotoUrl"),
            type = enum("type", MessageType.TEXT),
            text = getString("text"),
            audioUrl = getString("audioUrl"),
            audioDurationMs = getLong("audioDurationMs"),
            replyToId = getString("replyToId"),
            replyPreview = getString("replyPreview"),
            createdAt = millis("createdAt") ?: System.currentTimeMillis(),
            deletedAt = millis("deletedAt"),
            status = if (metadata.hasPendingWrites()) MessageStatus.PENDING else MessageStatus.SENT,
        )
    }
}

