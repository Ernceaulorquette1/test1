package com.pronoscore360.app.data

import kotlinx.coroutines.flow.MutableSharedFlow

/** El Authenticator (hilo de OkHttp) avisa aquí cuando la sesión caduca definitivamente. */
object AuthEvents {
    val logout = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    fun signalLogout() { logout.tryEmit(Unit) }
}
