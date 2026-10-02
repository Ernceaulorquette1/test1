package cl.driverlink.app.core.logging

/** Registro técnico de errores (Crashlytics en prod, Logcat en demo). */
interface CrashReporter {
    fun log(message: String)
    fun recordException(throwable: Throwable)
    fun setUserId(userId: String?)
}
