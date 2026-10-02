package cl.driverlink.app.data.demo

import cl.driverlink.app.domain.model.AccountStatus
import cl.driverlink.app.domain.model.Alert
import cl.driverlink.app.domain.model.AlertCategory
import cl.driverlink.app.domain.model.AlertStatus
import cl.driverlink.app.domain.model.Channel
import cl.driverlink.app.domain.model.ChannelMode
import cl.driverlink.app.domain.model.ChannelType
import cl.driverlink.app.domain.model.Company
import cl.driverlink.app.domain.model.CompanyMember
import cl.driverlink.app.domain.model.CompanyRole
import cl.driverlink.app.domain.model.CompanyStatus
import cl.driverlink.app.domain.model.EmergencyContact
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.MemberStatus
import cl.driverlink.app.domain.model.Message
import cl.driverlink.app.domain.model.MessageStatus
import cl.driverlink.app.domain.model.MessageType
import cl.driverlink.app.domain.model.NotificationCategory
import cl.driverlink.app.domain.model.PlatformRole
import cl.driverlink.app.domain.model.Reputation
import cl.driverlink.app.domain.model.SosStatus
import cl.driverlink.app.domain.model.Subscription
import cl.driverlink.app.domain.model.SubscriptionStatus
import cl.driverlink.app.domain.model.User
import cl.driverlink.app.domain.model.Vehicle
import cl.driverlink.app.domain.model.VehicleStatus
import cl.driverlink.app.domain.model.VehicleType
import cl.driverlink.app.domain.model.VerificationStatus
import cl.driverlink.app.domain.model.WorkPlatform
import cl.driverlink.app.domain.policy.AlertExpirationPolicy
import kotlinx.coroutines.flow.update

/**
 * Datos de demostración. Cuentas disponibles (contraseña "demo1234"):
 *  - demo@driverlink.cl     → plan Comunidad, puede activar la prueba Pro.
 *  - pro@driverlink.cl      → DriverLink Pro + dueño de "Transportes Demo SpA".
 *  - expirado@driverlink.cl → prueba Pro terminada (muestra el bloqueo Premium).
 */
object DemoSeed {
    const val DEMO_PASSWORD = "demo1234"
    const val COMPANY_ID = "empresa-demo"
    const val INVITE_CODE = "DL-DEM-2468"

    private const val MIN = 60_000L
    private const val DAY = 24 * 60 * MIN

    private val others = listOf(
        "u-juan" to ("Juan" to "Pérez"),
        "u-maria" to ("María" to "González"),
        "u-pedro" to ("Pedro" to "Soto"),
        "u-ana" to ("Ana" to "Muñoz"),
        "u-luis" to ("Luis" to "Fernández"),
    )

    private val chatLines = listOf(
        "Buenas tardes a todos, ¿cómo está el tráfico en Alameda?",
        "Taco fuerte en Costanera Norte dirección oriente.",
        "Ojo con el control en Vicuña Mackenna con Departamental.",
        "Aeropuerto con mucha demanda ahora.",
        "Gracias por el aviso!",
        "Se despejó el accidente en Kennedy.",
        "¿Alguien sabe si sigue cerrada Providencia con Los Leones?",
        "Lluvia en Maipú, manejen con cuidado.",
        "Hay un hoyo grande en Pajaritos, pista derecha.",
        "Buen turno a todos 👋",
    )

    fun populate(b: DemoBackend) {
        val now = b.clock.now()
        // Usuarios
        val demo = user("u-demo", "Conductor", "Demo", "demo@driverlink.cl", "Santiago", "Providencia",
            VerificationStatus.UNVERIFIED, SubscriptionStatus.FREE, now - 40 * DAY)
        val pro = user("u-pro", "Valentina", "Rojas", "pro@driverlink.cl", "Santiago", "Las Condes",
            VerificationStatus.VERIFIED, SubscriptionStatus.PRO, now - 200 * DAY)
        val expired = user("u-exp", "Diego", "Morales", "expirado@driverlink.cl", "Valparaíso", "Viña del Mar",
            VerificationStatus.VERIFIED, SubscriptionStatus.EXPIRED, now - 90 * DAY)
        val otherUsers = others.map { (id, name) ->
            user(id, name.first, name.second, "$id@demo.local", "Santiago", "Santiago", VerificationStatus.VERIFIED, SubscriptionStatus.FREE, now - 100 * DAY)
        }
        b.users.value = (listOf(demo, pro, expired) + otherUsers).associateBy { it.id }
        listOf(demo, pro, expired).forEach { b.passwords[it.email] = DEMO_PASSWORD to it.id }

        b.subscriptions.value = mapOf(
            demo.id to Subscription.FREE,
            pro.id to Subscription(SubscriptionStatus.PRO, now - 30 * DAY, now + 30 * DAY, trialUsed = true, source = "demo"),
            expired.id to Subscription(SubscriptionStatus.TRIAL, now - 31 * DAY, now - DAY, trialUsed = true, source = "trial"),
        )
        b.contacts.value = mapOf(pro.id to listOf(EmergencyContact("c1", "Carolina Rojas", "Hermana", "+56987654321")))

        // Canales comunitarios (en producción se crean desde administración, sin actualizar la app).
        val community = listOf(
            channel("santiago", "Santiago", "Canal general de la Región Metropolitana", "Región Metropolitana", "Santiago", null, ChannelMode.TEXT, 0),
            channel("aeropuerto", "Aeropuerto", "Información del AMB y alrededores", "Región Metropolitana", "Santiago", "Pudahuel", ChannelMode.TEXT, 1),
            channel("santiago-centro", "Santiago Centro", "", "Región Metropolitana", "Santiago", "Santiago", ChannelMode.TEXT, 2),
            channel("providencia", "Providencia", "", "Región Metropolitana", "Santiago", "Providencia", ChannelMode.TEXT, 3),
            channel("maipu", "Maipú", "", "Región Metropolitana", "Santiago", "Maipú", ChannelMode.TEXT, 4),
            channel("valparaiso", "Valparaíso", "", "Región de Valparaíso", "Valparaíso", null, ChannelMode.TEXT, 5),
            channel("vina", "Viña del Mar", "", "Región de Valparaíso", "Viña del Mar", null, ChannelMode.TEXT, 6),
            channel("concepcion", "Concepción", "", "Región del Biobío", "Concepción", null, ChannelMode.TEXT, 7),
            channel("emergencias-rm", "Emergencias RM", "Solo información de seguridad", "Región Metropolitana", "Santiago", null, ChannelMode.TEXT, 8, ChannelType.EMERGENCY),
            channel("radio-santiago", "Radio Santiago", "", "Región Metropolitana", "Santiago", null, ChannelMode.RADIO, 0),
            channel("radio-aeropuerto", "Radio Aeropuerto", "", "Región Metropolitana", "Santiago", "Pudahuel", ChannelMode.RADIO, 1),
            channel("radio-valparaiso", "Radio Valparaíso", "", "Región de Valparaíso", "Valparaíso", null, ChannelMode.RADIO, 2),
        )
        val companyChannels = listOf(
            companyChannel("general", "General", ChannelMode.TEXT, 0),
            companyChannel("despacho", "Despacho", ChannelMode.TEXT, 1),
            companyChannel("conductores", "Conductores", ChannelMode.TEXT, 2),
            companyChannel("emergencias", "Emergencias", ChannelMode.TEXT, 3),
            companyChannel("radio-general", "Radio General", ChannelMode.RADIO, 0),
            companyChannel("radio-despacho", "Radio Despacho", ChannelMode.RADIO, 1),
        )
        b.channels.value = community + companyChannels

        // 45 mensajes en Santiago para demostrar la paginación (página = 30).
        val santiago = (0 until 45).map { i ->
            val (uid, name) = others[i % others.size]
            message(b.newId(), "santiago", null, uid, "${name.first} ${name.second}", chatLines[i % chatLines.size],
                now - (45 - i) * 7 * MIN)
        }
        val companyGeneral = listOf(
            message(b.newId(), "general", COMPANY_ID, "u-pro", "Valentina Rojas", "Bienvenidos al canal privado de la empresa.", now - 120 * MIN),
            message(b.newId(), "general", COMPANY_ID, "u-juan", "Juan Pérez", "Recibido, iniciando jornada.", now - 100 * MIN),
        )
        val radioMessages = listOf(
            audio(b, "radio-santiago", null, "u-maria", "María González", 4, 523.0, now - 20 * MIN),
            audio(b, "radio-santiago", null, "u-juan", "Juan Pérez", 3, 440.0, now - 12 * MIN),
            audio(b, "radio-santiago", null, "u-ana", "Ana Muñoz", 5, 392.0, now - 4 * MIN),
            audio(b, "radio-general", COMPANY_ID, "u-pro", "Valentina Rojas", 3, 600.0, now - 30 * MIN),
        )
        (santiago + companyGeneral + radioMessages).forEach { b.addMessage(it) }

        // Alertas activas alrededor de Santiago.
        val policy = AlertExpirationPolicy(b.config.value.alertExpirationMinutes)
        val alerts = listOf(
            alert("a1", "u-juan", "Juan Pérez", AlertCategory.ACCIDENT, "Choque en pista izquierda, tránsito lento", -33.4372, -70.6506, "Providencia", now - 12 * MIN, policy, 4),
            alert("a2", "u-maria", "María González", AlertCategory.TRAFFIC, "Congestión en Costanera Norte", -33.4110, -70.6000, "Vitacura", now - 25 * MIN, policy, 7),
            alert("a3", "u-pedro", "Pedro Soto", AlertCategory.ROAD_CLOSED, "Calle cerrada por obras", -33.4569, -70.6483, "Santiago", now - 90 * MIN, policy, 2),
            alert("a4", "u-ana", "Ana Muñoz", AlertCategory.ROAD_PROBLEM, "Hoyo grande en pista derecha", -33.5100, -70.7580, "Maipú", now - 200 * MIN, policy, 5),
            alert("a5", "u-luis", "Luis Fernández", AlertCategory.SECURITY, "Personas sospechosas en semáforo", -33.4250, -70.6150, "Providencia", now - 40 * MIN, policy, 1),
        )
        b.alerts.value = alerts.associateBy { it.id }

        // Empresa demo: dueño "pro", conductores activos y una solicitud pendiente.
        val company = Company(COMPANY_ID, "76.123.456-0", "Transportes Demo SpA", "Transportes Demo", "contacto@transportesdemo.cl",
            "+56222222222", "Av. Providencia 1234, Santiago", CompanyStatus.ACTIVE, "FLOTAS_BASE", INVITE_CODE, now - 60 * DAY)
        b.companies.value = mapOf(COMPANY_ID to company)
        b.members.value = mapOf(
            COMPANY_ID to listOf(
                CompanyMember(COMPANY_ID, "u-pro", "Valentina Rojas", CompanyRole.OWNER, MemberStatus.ACTIVE, now - 60 * DAY, now - 60 * DAY),
                CompanyMember(COMPANY_ID, "u-juan", "Juan Pérez", CompanyRole.DRIVER, MemberStatus.ACTIVE, now - 50 * DAY, now - 51 * DAY),
                CompanyMember(COMPANY_ID, "u-maria", "María González", CompanyRole.DISPATCHER, MemberStatus.ACTIVE, now - 45 * DAY, now - 46 * DAY),
                CompanyMember(COMPANY_ID, "u-pedro", "Pedro Soto", CompanyRole.DRIVER, MemberStatus.PENDING, null, now - DAY),
            ).associateBy { it.userId }
        )
        b.vehicles.value = mapOf(
            COMPANY_ID to listOf(
                Vehicle("v1", COMPANY_ID, "KXTR21", "Toyota", "Corolla", 2021, VehicleType.CAR, VehicleStatus.ACTIVE, "u-juan", "Juan Pérez"),
                Vehicle("v2", COMPANY_ID, "LBCD45", "Hyundai", "H-1", 2022, VehicleType.VAN, VehicleStatus.ACTIVE, null, null),
            )
        )
    }

    fun simulatedMessage(b: DemoBackend, index: Int) {
        val (uid, name) = others[index % others.size]
        b.addMessage(message(b.newId(), "santiago", null, uid, "${name.first} ${name.second}", chatLines[(index + 3) % chatLines.size], b.clock.now()))
    }

    fun simulatedAlert(b: DemoBackend, index: Int) {
        val now = b.clock.now()
        val categories = listOf(AlertCategory.TRAFFIC, AlertCategory.ACCIDENT, AlertCategory.DANGER)
        val category = categories[index % categories.size]
        val (uid, name) = others[(index + 1) % others.size]
        val policy = AlertExpirationPolicy(b.config.value.alertExpirationMinutes)
        val a = alert("sim-$index-$now", uid, "${name.first} ${name.second}", category, "Reporte de la comunidad",
            -33.44 + (index % 5) * 0.01, -70.65 + (index % 3) * 0.01, "Santiago", now, policy, 0)
        b.alerts.update { it + (a.id to a) }
        b.currentUserId.value?.let { me ->
            b.notify(me, NotificationCategory.NEARBY_ALERTS, "Nueva alerta cercana", "${name.first} reportó un incidente en Santiago")
        }
    }

    fun sosStatusText(status: SosStatus): String = when (status) {
        SosStatus.CREATED -> "Buscando asistencia"
        SosStatus.RECEIVED -> "Solicitud recibida por un operador"
        SosStatus.IN_PROGRESS -> "Tu SOS está en atención"
        SosStatus.RESOLVED -> "SOS finalizado"
        SosStatus.CANCELLED -> "SOS cancelado"
    }

    private fun user(
        id: String, first: String, last: String, email: String, city: String, commune: String,
        verification: VerificationStatus, sub: SubscriptionStatus, createdAt: Long,
    ) = User(
        id = id, firstName = first, lastName = last, email = email, phone = "+56912345678", photoUrl = null,
        city = city, commune = commune, platforms = listOf(WorkPlatform.UBER, WorkPlatform.DIDI),
        verificationStatus = verification, accountStatus = AccountStatus.ACTIVE, subscriptionStatus = sub,
        role = PlatformRole.DRIVER, reputation = Reputation(alertsCreated = 3, alertsConfirmed = 11),
        createdAt = createdAt, updatedAt = createdAt,
    )

    private fun channel(
        id: String, name: String, description: String, region: String, city: String, zone: String?,
        mode: ChannelMode, order: Int, type: ChannelType = ChannelType.COMMUNITY,
    ) = Channel(id, name, description, type, mode, region, city, zone, null, order, true, null)

    private fun companyChannel(id: String, name: String, mode: ChannelMode, order: Int) =
        Channel(id, name, "", ChannelType.PRIVATE_COMPANY, mode, "", "", null, COMPANY_ID, order, true, null)

    fun message(id: String, channelId: String, companyId: String?, uid: String, name: String, text: String, at: Long) =
        Message(id, channelId, companyId, uid, name, null, MessageType.TEXT, text, null, null, null, null, at, null, MessageStatus.SENT)

    private fun audio(b: DemoBackend, channelId: String, companyId: String?, uid: String, name: String, seconds: Int, freq: Double, at: Long): Message {
        val path = DemoAudio.ensureTone(b.audioDir, "seed_${channelId}_$seconds", seconds, freq)
        return Message(b.newId(), channelId, companyId, uid, name, null, MessageType.AUDIO, null, path, seconds * 1000L,
            null, null, at, null, MessageStatus.SENT)
    }

    private fun alert(
        id: String, uid: String, name: String, category: AlertCategory, description: String,
        lat: Double, lng: Double, commune: String, createdAt: Long, policy: AlertExpirationPolicy, confirmations: Int,
    ) = Alert(
        id = id, creatorId = uid, creatorName = name, category = category, description = description,
        location = GeoPoint(lat, lng), city = "Santiago", commune = commune, status = AlertStatus.ACTIVE,
        confirmationsCount = confirmations, endedCount = 0, incorrectCount = 0, createdAt = createdAt,
        expiresAt = policy.expiresAt(category, createdAt), updatedAt = createdAt,
    )

}
