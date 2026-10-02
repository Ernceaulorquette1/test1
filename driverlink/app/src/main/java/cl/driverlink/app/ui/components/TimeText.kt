package cl.driverlink.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import cl.driverlink.app.R
import cl.driverlink.app.core.time.RelativeTime
import java.text.DateFormat
import java.util.Date

@Composable
fun relativeTimeText(then: Long, now: Long = System.currentTimeMillis()): String =
    when (val rel = RelativeTime.between(then, now)) {
        RelativeTime.JustNow -> stringResource(R.string.time_just_now)
        is RelativeTime.Minutes -> pluralStringResource(R.plurals.time_minutes_ago, rel.value, rel.value)
        is RelativeTime.Hours -> pluralStringResource(R.plurals.time_hours_ago, rel.value, rel.value)
        is RelativeTime.Days -> pluralStringResource(R.plurals.time_days_ago, rel.value, rel.value)
    }

fun formatClock(timestamp: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))

fun formatDate(timestamp: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))
