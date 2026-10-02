package cl.driverlink.app.core.logging

import android.util.Log
import cl.driverlink.app.core.analytics.AnalyticsEvent
import cl.driverlink.app.core.analytics.AnalyticsTracker

/** Implementaciones locales usadas por el flavor demo y como respaldo. */
class LogcatCrashReporter : CrashReporter {
    override fun log(message: String) { Log.i(TAG, message) }
    override fun recordException(throwable: Throwable) { Log.e(TAG, "Error registrado", throwable) }
    override fun setUserId(userId: String?) { Log.i(TAG, "userId=${userId != null}") }

    private companion object { const val TAG = "DriverLink" }
}

class LogcatAnalyticsTracker : AnalyticsTracker {
    override fun log(event: AnalyticsEvent, params: Map<String, String>) {
        Log.d("DriverLinkAnalytics", "${event.eventName} $params")
    }
    override fun setUserId(userId: String?) = Unit
}
