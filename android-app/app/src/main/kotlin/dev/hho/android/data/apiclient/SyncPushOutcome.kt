package dev.hho.android.data.apiclient

import dev.hho.android.data.apiclient.generated.models.SyncAppliedEntry
import dev.hho.android.data.apiclient.generated.models.SyncConflictEntry
import dev.hho.android.data.apiclient.generated.models.SyncSkippedEntry
import kotlinx.serialization.json.JsonElement

data class PushMutation(
    val mutationId: String,
    val entityType: String,
    val entityId: String,
    val baseVersion: Long,
    val fields: Map<String, JsonElement>,
    val op: String? = null,
)

sealed interface SyncPushOutcome {
    data class Success(
        val applied: List<SyncAppliedEntry>,
        val skipped: List<SyncSkippedEntry>,
        val conflicts: List<SyncConflictEntry>,
        val newWatermark: Long,
    ) : SyncPushOutcome

    data class StructuralRejection(
        val index: Int?,
        val mutationId: String?,
        val detail: String,
    ) : SyncPushOutcome
}

internal object SyncPushDetailParser {
    const val STRUCTURAL_PREFIX = "mutations["

    private val pattern = Regex("""^mutations\[(\d+)] \(mutation_id="((?:[^"\\]|\\.)*)"\): .*""", RegexOption.DOT_MATCHES_ALL)

    fun parse(detail: String): SyncPushOutcome.StructuralRejection {
        val match = pattern.matchEntire(detail)
        val index = match?.groupValues?.get(1)?.toIntOrNull()
        val id = match?.groupValues?.get(2)?.replace(Regex("""\\(.)"""), "$1")
        return if (index == null || id == null) {
            SyncPushOutcome.StructuralRejection(null, null, detail)
        } else {
            SyncPushOutcome.StructuralRejection(index, id, detail)
        }
    }
}
