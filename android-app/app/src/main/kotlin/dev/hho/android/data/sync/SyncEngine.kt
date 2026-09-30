package dev.hho.android.data.sync

import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncRunOutcome {
    sealed interface FullPullReason {
        data object FirstSync : FullPullReason

        data object CursorTooOldRecovery : FullPullReason

        data object ConflictReconcile : FullPullReason
    }

    data class FullPullRan(val reason: FullPullReason) : SyncRunOutcome

    data object IncrementalPullRan : SyncRunOutcome

    data object AuthRequired : SyncRunOutcome

    data class UpgradeRequired(val minimumVersion: String?) : SyncRunOutcome
}

fun interface PostSyncHook {
    suspend fun onSyncSucceeded()
}

@Singleton
class SyncEngine
    internal constructor(
        private val fullSyncCoordinator: FullSyncCoordinator,
        private val incrementalSyncCoordinator: IncrementalSyncCoordinator,
        private val database: HhoDatabase,
        private val pushPhase: suspend (deviceId: String) -> PushPhaseOutcome,
        private val outbox: OutboxRepository,
        private val hooks: Set<@JvmSuppressWildcards PostSyncHook>,
        private val recorder: SyncRunRecorder,
        private val conflictLog: (suspend () -> Unit)?,
    ) {
        @Inject
        internal constructor(
            fullSyncCoordinator: FullSyncCoordinator,
            incrementalSyncCoordinator: IncrementalSyncCoordinator,
            database: HhoDatabase,
            api: HhoApiClient,
            outbox: OutboxRepository,
            hooks: Set<@JvmSuppressWildcards PostSyncHook>,
            recorder: SyncRunRecorder,
            conflictLogSync: ConflictLogSync,
        ) : this(
            fullSyncCoordinator,
            incrementalSyncCoordinator,
            database,
            OutboxSyncer(database, api, outbox)::push,
            outbox,
            hooks,
            recorder,
            { conflictLogSync.sync().getOrThrow() },
        )

        private val singleFlight = Mutex()

        suspend fun sync(deviceId: String): Result<SyncRunOutcome> =
            singleFlight.withLock { runLocked(deviceId) }

        private suspend fun runLocked(deviceId: String): Result<SyncRunOutcome> {
            outbox.resetInFlightToPending()
            val owed = recorder.reconcileOwed()

            when (val push = pushPhase(deviceId)) {
                is PushPhaseOutcome.Failed -> {
                    recordStop(push.reconcilePending, push.cause.message)
                    return Result.failure(push.cause)
                }
                is PushPhaseOutcome.Blocked -> {
                    val (outcome, message) =
                        when (push.reason) {
                            BlockedReason.AUTH -> SyncRunOutcome.AuthRequired to "Not authenticated"
                            BlockedReason.UPGRADE_REQUIRED ->
                                SyncRunOutcome.UpgradeRequired(push.minimumVersion) to
                                    "Client too old: server requires at least ${push.minimumVersion}"
                        }
                    recordStop(push.reconcilePending, message)
                    return Result.success(outcome)
                }
                PushPhaseOutcome.NeedsReconcile -> {
                    recorder.recordPushReachedServer()
                    return pullPhase(deviceId, forceFull = true, debtAlreadyStored = owed)
                }
                PushPhaseOutcome.Drained -> {
                    recorder.recordPushReachedServer()
                    return pullPhase(deviceId, forceFull = owed, debtAlreadyStored = owed)
                }
            }
        }

        private suspend fun pullPhase(
            deviceId: String,
            forceFull: Boolean,
            debtAlreadyStored: Boolean,
        ): Result<SyncRunOutcome> {
            if (forceFull && !debtAlreadyStored) setDebt(true)

            val pulled: Result<SyncRunOutcome> =
                when {
                    forceFull -> fullPull(deviceId, SyncRunOutcome.FullPullReason.ConflictReconcile)
                    database.syncStateDao().get() == null -> fullPull(deviceId, SyncRunOutcome.FullPullReason.FirstSync)
                    else -> incrementalOrRecover(deviceId)
                }
            if (pulled.isFailure) {
                recordStop(reconcilePending = false, message = pulled.exceptionOrNull()?.message)
                return pulled
            }
            return afterPull().map { pulled.getOrThrow() }
        }

        private suspend fun incrementalOrRecover(deviceId: String): Result<SyncRunOutcome> {
            val outcome = incrementalSyncCoordinator.pullIncremental(deviceId).getOrElse { return Result.failure(it) }
            return when (outcome) {
                IncrementalPullOutcome.Applied -> Result.success(SyncRunOutcome.IncrementalPullRan)
                IncrementalPullOutcome.CursorTooOld -> fullPull(deviceId, SyncRunOutcome.FullPullReason.CursorTooOldRecovery)
            }
        }

        private suspend fun fullPull(
            deviceId: String,
            reason: SyncRunOutcome.FullPullReason,
        ): Result<SyncRunOutcome> =
            fullSyncCoordinator.pullFull(deviceId).map {
                setDebt(false)
                SyncRunOutcome.FullPullRan(reason)
            }

        private suspend fun afterPull(): Result<Unit> =
            try {
                outbox.releaseHeld(::mirrorVersion)
                for (hook in hooks) runHook(hook)
                runConflictLog()
                recorder.recordSuccess()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Result.failure(e)
            }

        private suspend fun runConflictLog() {
            val fetch = conflictLog ?: return
            try {
                fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "conflict log fetch failed: ${e.javaClass.simpleName}")
            }
        }

        private suspend fun runHook(hook: PostSyncHook) {
            try {
                hook.onSyncSucceeded()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "post-sync hook ${hook.javaClass.simpleName} failed: ${e.javaClass.simpleName}")
            }
        }

        private suspend fun mirrorVersion(
            type: String,
            id: String,
        ): Long? =
            when (type) {
                "item" -> database.itemDao().getById(id)?.version
                "purchased_from_block" -> database.purchasedFromBlockDao().versionById(id)
                else -> null
            }

        private companion object {
            const val TAG = "SyncEngine"
        }

        private suspend fun setDebt(owed: Boolean) {
            if (recorder.reconcileOwed() != owed) recorder.setReconcilePending(owed)
        }

        private suspend fun recordStop(
            reconcilePending: Boolean,
            message: String?,
        ) = recorder.recordFailure(message, reconcilePending)
    }
