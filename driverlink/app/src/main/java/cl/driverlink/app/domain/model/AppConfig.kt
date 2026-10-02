package cl.driverlink.app.domain.model

/**
 * Configuración remota (Remote Config / adminConfig). Todos los valores ajustables
 * del producto viven aquí y no dispersos por las pantallas.
 */
data class AppConfig(
    val trialDays: Int = 30,
    val maxAudioDurationSeconds: Int = 30,
    val alertExpirationMinutes: Map<AlertCategory, Int> = DEFAULT_ALERT_EXPIRATION,
    val premiumPriceDisplay: String = "$5.990 CLP / mes",
    val sosEnabled: Boolean = true,
    val companyFeaturesEnabled: Boolean = true,
    val maintenanceMode: Boolean = false,
    val sosHoldMillis: Long = 3_000,
    val chatPageSize: Int = 30,
    val alertResolveThreshold: Int = 3,
    val sosLocationIntervalSeconds: Int = 20,
    val workLocationIntervalSeconds: Int = 60,
    val officialServices: List<OfficialService> = DEFAULT_OFFICIAL_SERVICES,
) {
    companion object {
        val DEFAULT_ALERT_EXPIRATION: Map<AlertCategory, Int> = mapOf(
            AlertCategory.ACCIDENT to 90,
            AlertCategory.TRAFFIC to 45,
            AlertCategory.ROAD_CLOSED to 360,
            AlertCategory.DANGER to 120,
            AlertCategory.SECURITY to 120,
            AlertCategory.ROAD_PROBLEM to 720,
            AlertCategory.EMERGENCY to 60,
            AlertCategory.OTHER to 60,
        )

        /** Servicios oficiales de Chile. Configurable para futuros países. */
        val DEFAULT_OFFICIAL_SERVICES = listOf(
            OfficialService("Carabineros", "133"),
            OfficialService("Ambulancia (SAMU)", "131"),
            OfficialService("Bomberos", "132"),
            OfficialService("PDI", "134"),
        )
    }
}
