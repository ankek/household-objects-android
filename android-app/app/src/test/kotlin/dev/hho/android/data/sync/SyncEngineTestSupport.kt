package dev.hho.android.data.sync

import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.outbox.OutboxRepository
import dev.hho.android.data.room.HhoDatabase

internal class CountingHook(private val failure: Throwable? = null) : PostSyncHook {
    var calls = 0

    override suspend fun onSyncSucceeded() {
        calls++
        failure?.let { throw it }
    }
}

internal fun testSyncEngine(
    client: HhoApiClient,
    db: HhoDatabase,
    outbox: OutboxRepository = OutboxRepository(db),
    hooks: Set<PostSyncHook> = emptySet(),
    push: suspend (String) -> PushPhaseOutcome = OutboxSyncer(db, client, outbox)::push,
    withConflictLog: Boolean = false,
    conflictLogStep: (suspend () -> Unit)? = null,
): SyncEngine {
    val recorder = SyncRunRecorder(db.syncRunStateDao()) { 1_000L }
    val realStep: (suspend () -> Unit)? =
        if (withConflictLog) {
            val log = ConflictLogSync(client, db.conflictRecordDao(), recorder)
            suspend { log.sync().getOrThrow() }
        } else {
            null
        }
    return SyncEngine(
        FullSyncCoordinator(client, db),
        IncrementalSyncCoordinator(client, db),
        db,
        push,
        outbox,
        hooks,
        recorder,
        conflictLogStep ?: realStep,
    )
}
