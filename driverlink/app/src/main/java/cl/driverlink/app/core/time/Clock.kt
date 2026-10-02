package cl.driverlink.app.core.time

/** Abstracción del reloj para poder probar reglas que dependen del tiempo. */
fun interface Clock {
    fun now(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
