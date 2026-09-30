package dev.hho.android.data.receiving

import androidx.room.withTransaction
import dev.hho.android.data.ids.UuidV7Generator
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ReceivingLineEntity
import dev.hho.android.data.room.ReceivingSessionEntity
import dev.hho.android.data.room.ReceivingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

sealed class ReceivingException(message: String) : RuntimeException(message) {
    class SessionNotFound(val sessionId: String) : ReceivingException("No receiving session $sessionId")

    class SessionNotOpen(val sessionId: String, val status: String) :
        ReceivingException("Receiving session $sessionId is $status, not OPEN")

    class UnknownItem(val itemId: String) : ReceivingException("Item $itemId is not in the local mirror")

    class LineNotFound(val sessionId: String, val itemId: String) :
        ReceivingException("Session $sessionId has no line for item $itemId")

    class InvalidArgument(message: String) : ReceivingException(message)
}

sealed interface ScanOutcome {
    data class Counted(val line: ReceivingLineEntity) : ScanOutcome

    data class Unexpected(val itemId: String) : ScanOutcome
}

enum class LineStatus { MATCHED, SHORT, OVER, UNEXPECTED }

data class LineDiscrepancy(
    val itemId: String,
    val expectedQty: Int?,
    val receivedQty: Int,
    val status: LineStatus,
    val difference: Int?,
)

data class DiscrepancySummary(val lines: List<LineDiscrepancy>) {
    val shortLines: List<LineDiscrepancy> get() = lines.filter { it.status == LineStatus.SHORT }
    val overLines: List<LineDiscrepancy> get() = lines.filter { it.status == LineStatus.OVER }
    val unexpectedLines: List<LineDiscrepancy> get() = lines.filter { it.status == LineStatus.UNEXPECTED }
    val matchedCount: Int get() = lines.count { it.status == LineStatus.MATCHED }

    val totalExpected: Int get() = lines.sumOf { it.expectedQty ?: 0 }

    val totalReceived: Int get() = lines.sumOf { it.receivedQty }

    val isClean: Boolean get() = lines.all { it.status == LineStatus.MATCHED }
}

fun ReceivingLineEntity.toDiscrepancy(): LineDiscrepancy {
    val expected = expectedQty
    val status = when {
        expected == null -> LineStatus.UNEXPECTED
        receivedQty < expected -> LineStatus.SHORT
        receivedQty > expected -> LineStatus.OVER
        else -> LineStatus.MATCHED
    }
    return LineDiscrepancy(itemId, expected, receivedQty, status, expected?.let { receivedQty - it })
}

@Singleton
class ReceivingRepository(
    private val db: HhoDatabase,
    private val newId: () -> String,
    private val clock: () -> Long,
) {
    @Inject
    constructor(db: HhoDatabase) : this(db, UuidV7Generator()::generate, System::currentTimeMillis)

    private val dao get() = db.receivingDao()

    suspend fun startSession(
        vendor: String,
        orderReference: String? = null,
        purchasedOn: LocalDate? = null,
    ): ReceivingSessionEntity {
        val trimmed = vendor.trim()
        if (trimmed.isEmpty()) throw ReceivingException.InvalidArgument("vendor must not be blank")
        val session = ReceivingSessionEntity(
            id = newId(),
            vendor = trimmed,
            orderReference = orderReference?.trim()?.ifEmpty { null },
            purchasedOn = purchasedOn,
            status = ReceivingStatus.OPEN,
            createdAt = clock(),
        )
        dao.insertSession(session)
        return session
    }

    suspend fun addExpected(sessionId: String, itemId: String, expectedQty: Int): ReceivingLineEntity {
        if (expectedQty < 1) throw ReceivingException.InvalidArgument("expectedQty must be >= 1")
        return db.withTransaction {
            requireOpen(sessionId)
            requireItem(itemId)
            val line = ReceivingLineEntity(
                sessionId, itemId, expectedQty, dao.getLine(sessionId, itemId)?.receivedQty ?: 0,
            )
            dao.upsertLine(line)
            line
        }
    }

    suspend fun recordScan(sessionId: String, itemId: String): ScanOutcome =
        db.withTransaction {
            requireOpen(sessionId)
            requireItem(itemId)
            if (dao.addReceived(sessionId, itemId, 1) == 0) {
                ScanOutcome.Unexpected(itemId)
            } else {
                ScanOutcome.Counted(checkNotNull(dao.getLine(sessionId, itemId)))
            }
        }

    suspend fun confirmUnexpected(sessionId: String, itemId: String): ReceivingLineEntity =
        db.withTransaction {
            requireOpen(sessionId)
            requireItem(itemId)
            if (dao.addReceived(sessionId, itemId, 1) == 0) {
                dao.upsertLine(ReceivingLineEntity(sessionId, itemId, null, 1))
            }
            checkNotNull(dao.getLine(sessionId, itemId))
        }

    suspend fun setReceived(sessionId: String, itemId: String, receivedQty: Int): ReceivingLineEntity {
        if (receivedQty < 0) throw ReceivingException.InvalidArgument("receivedQty must be >= 0")
        return db.withTransaction {
            requireOpen(sessionId)
            if (dao.setReceived(sessionId, itemId, receivedQty) == 0) {
                throw ReceivingException.LineNotFound(sessionId, itemId)
            }
            checkNotNull(dao.getLine(sessionId, itemId))
        }
    }

    suspend fun adjustReceived(sessionId: String, itemId: String, delta: Int): ReceivingLineEntity =
        db.withTransaction {
            if (dao.adjustReceived(sessionId, itemId, delta) == 0) {
                requireOpen(sessionId)
                throw ReceivingException.LineNotFound(sessionId, itemId)
            }
            checkNotNull(dao.getLine(sessionId, itemId))
        }

    suspend fun cancel(sessionId: String) {
        db.withTransaction {
            val session = dao.getSession(sessionId) ?: throw ReceivingException.SessionNotFound(sessionId)
            when (session.status) {
                ReceivingStatus.CANCELLED -> Unit
                ReceivingStatus.OPEN ->
                    dao.updateSession(session.copy(status = ReceivingStatus.CANCELLED, completedAt = clock()))
                else -> throw ReceivingException.SessionNotOpen(sessionId, session.status)
            }
        }
    }

    fun observeSession(sessionId: String): Flow<ReceivingSessionEntity?> = dao.observeSession(sessionId)

    fun observeLines(sessionId: String): Flow<List<ReceivingLineEntity>> = dao.observeLines(sessionId)

    fun observeOpenSessions(): Flow<List<ReceivingSessionEntity>> = dao.observeByStatus(ReceivingStatus.OPEN)

    fun observeSummary(sessionId: String): Flow<DiscrepancySummary> =
        dao.observeLines(sessionId).map { DiscrepancySummary(it.map(ReceivingLineEntity::toDiscrepancy)) }

    suspend fun summary(sessionId: String): DiscrepancySummary =
        DiscrepancySummary(dao.getLines(sessionId).map(ReceivingLineEntity::toDiscrepancy))

    private suspend fun requireOpen(sessionId: String) {
        val session = dao.getSession(sessionId) ?: throw ReceivingException.SessionNotFound(sessionId)
        if (session.status != ReceivingStatus.OPEN) {
            throw ReceivingException.SessionNotOpen(sessionId, session.status)
        }
    }

    private suspend fun requireItem(itemId: String) {
        if (db.itemDao().getById(itemId) == null) throw ReceivingException.UnknownItem(itemId)
    }
}
