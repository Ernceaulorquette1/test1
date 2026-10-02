package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.EmergencyContact
import cl.driverlink.app.domain.model.GeoPoint
import cl.driverlink.app.domain.model.SosEvent
import cl.driverlink.app.domain.model.SosStatus
import cl.driverlink.app.domain.model.SosType
import kotlinx.coroutines.flow.Flow

interface SosRepository {
    /** Crea el evento en servidor (Cloud Function), que valida el acceso Premium. */
    suspend fun createSos(type: SosType, location: GeoPoint?, notes: String): AppResult<String>
    fun observeSos(sosId: String): Flow<SosEvent?>
    /** SOS activo del usuario actual, si existe. */
    fun observeMyActiveSos(): Flow<SosEvent?>
    suspend fun cancelSos(sosId: String): AppResult<Unit>
    /** Ubicación operacional mientras el SOS está activo. */
    suspend fun pushLocation(sosId: String, point: GeoPoint): AppResult<Unit>
}

/** Operaciones del panel de operadores SOS (panel web futuro / rol SOS_OPERATOR). */
interface SosOperatorRepository {
    fun observeActiveCases(): Flow<List<SosEvent>>
    suspend fun acceptCase(sosId: String): AppResult<Unit>
    suspend fun updateStatus(sosId: String, status: SosStatus): AppResult<Unit>
    suspend fun addNote(sosId: String, note: String): AppResult<Unit>
}

interface EmergencyContactRepository {
    fun observeContacts(): Flow<List<EmergencyContact>>
    suspend fun saveContact(contact: EmergencyContact): AppResult<Unit>
    suspend fun deleteContact(contactId: String): AppResult<Unit>
}
