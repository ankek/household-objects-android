package dev.hho.android.ui.syncstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.room.ConflictOrigin
import dev.hho.android.data.room.ConflictRecordDao
import dev.hho.android.data.room.ConflictRecordEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

internal data class ConflictUi(
    val id: Long,
    val entityLabel: String,
    val fieldLabel: String,
    val rejectedValue: String,
    val keptValue: String,
    val detectedAt: Long,
    val originLabel: String,
    val mutationId: String?,
)

@HiltViewModel
internal class ConflictLogViewModel
    @Inject
    constructor(
        private val conflicts: ConflictRecordDao,
    ) : ViewModel() {
        val state: StateFlow<List<ConflictUi>> =
            conflicts.observeAll()
                .map { rows -> rows.map { it.toUi() } }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

        private suspend fun ConflictRecordEntity.toUi(): ConflictUi {
            val name = conflicts.itemNameFor(entityType, entityId)
            return ConflictUi(
                id = id,
                entityLabel = name ?: "$entityType ${entityId.take(SHORT_ID)}",
                fieldLabel = if (fieldName == WHOLE_ENTITY) "whole item" else fieldName,
                rejectedValue = renderJsonValue(losingValueJson, "not recorded"),
                keptValue = renderJsonValue(serverValueJson, "not yet known"),
                detectedAt = detectedAt,
                originLabel = if (origin == ConflictOrigin.LOCAL_PUSH) {
                    "Rejected when this device pushed"
                } else {
                    "From the server log (any device in the group)"
                },
                mutationId = mutationId.takeUnless { it.isBlank() || it.startsWith("server:") },
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
            const val SHORT_ID = 8
            const val WHOLE_ENTITY = "_entity"
        }
    }

internal fun renderJsonValue(json: String?, absent: String): String {
    if (json == null) return absent
    val element = runCatching { Json.parseToJsonElement(json) }.getOrNull() ?: return json
    return when {
        element is JsonNull -> "—"
        element is JsonPrimitive -> element.content
        else -> element.toString()
    }
}
