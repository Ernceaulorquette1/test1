package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.NotificationPreferences
import kotlinx.coroutines.flow.Flow

interface NotificationPreferencesRepository {
    fun observePreferences(): Flow<NotificationPreferences>
    suspend fun updatePreferences(preferences: NotificationPreferences): AppResult<Unit>
}
