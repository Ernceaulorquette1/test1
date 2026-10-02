package cl.driverlink.app.domain.repository

import cl.driverlink.app.core.result.AppResult
import cl.driverlink.app.domain.model.BlockedUser
import cl.driverlink.app.domain.model.ReportDraft
import kotlinx.coroutines.flow.Flow

interface ModerationRepository {
    suspend fun report(draft: ReportDraft): AppResult<Unit>
    suspend fun blockUser(userId: String, displayName: String): AppResult<Unit>
    suspend fun unblockUser(userId: String): AppResult<Unit>
    fun observeBlockedUsers(): Flow<List<BlockedUser>>
}
