package dev.hho.android.domain

import dev.hho.android.data.receiving.ReceivingException
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ReceivingStatus
import javax.inject.Inject

internal sealed interface CheckInResult {
    data class Completed(val lines: Int) : CheckInResult

    data object AlreadyCompleted : CheckInResult

    data class Failed(val error: EditError) : CheckInResult
}

internal class ReceivingCheckIn(
    private val db: HhoDatabase,
    private val edits: EditRepository,
    private val clock: () -> Long,
) {
    @Inject
    constructor(db: HhoDatabase, edits: EditRepository) : this(db, edits, System::currentTimeMillis)

    suspend fun checkIn(sessionId: String): CheckInResult {
        val result = edits.batch { checkInLines(sessionId) }
        return when (result) {
            is EditResult.Success -> result.value
            is EditResult.Failure -> {
                val cause = (result.error as? EditError.StorageFailed)?.cause
                if (cause is ReceivingException) throw cause
                CheckInResult.Failed(result.error)
            }
        }
    }

    private suspend fun EditRepository.EditTx.checkInLines(sessionId: String): EditResult<CheckInResult> {
        val dao = db.receivingDao()
        val session = dao.getSession(sessionId) ?: throw ReceivingException.SessionNotFound(sessionId)
        when (session.status) {
            ReceivingStatus.COMPLETED -> return EditResult.Success(CheckInResult.AlreadyCompleted)
            ReceivingStatus.OPEN -> Unit
            else -> throw ReceivingException.SessionNotOpen(sessionId, session.status)
        }
        var adjustments = 0
        for (line in dao.getLines(sessionId).filter { it.receivedQty > 0 }) {
            val adj = adjustStock(line.itemId, line.receivedQty.toLong(), RECEIVING_REASON, session.orderReference)
            if (adj is EditResult.Failure) return adj
            val stamp = stampPurchase(
                line.itemId,
                vendor = session.vendor,
                purchasedOn = session.purchasedOn,
                orderReference = session.orderReference,
            )
            if (stamp is EditResult.Failure) return stamp
            adjustments++
        }
        dao.updateSession(session.copy(status = ReceivingStatus.COMPLETED, completedAt = clock()))
        return EditResult.Success(CheckInResult.Completed(adjustments))
    }

    private companion object {
        const val RECEIVING_REASON = "receiving"
    }
}
