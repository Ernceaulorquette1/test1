package cl.driverlink.app.core.time

/** Diferencia de tiempo en unidades legibles, independiente de la UI. */
sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val value: Int) : RelativeTime
    data class Hours(val value: Int) : RelativeTime
    data class Days(val value: Int) : RelativeTime

    companion object {
        fun between(then: Long, now: Long): RelativeTime {
            val minutes = ((now - then).coerceAtLeast(0) / 60_000).toInt()
            return when {
                minutes < 1 -> JustNow
                minutes < 60 -> Minutes(minutes)
                minutes < 24 * 60 -> Hours(minutes / 60)
                else -> Days(minutes / (24 * 60))
            }
        }
    }
}

/** Formatea duraciones de audio como m:ss. */
fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
