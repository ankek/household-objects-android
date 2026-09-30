package dev.hho.android.data.items

import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.ItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ItemSearch
    @Inject
    constructor(
        private val itemDao: ItemDao,
    ) {
        fun results(query: String): Flow<List<ItemEntity>> {
            val trimmed = query.trim()
            return if (trimmed.isEmpty()) {
                itemDao.observeAll()
            } else {
                itemDao.search(likePatternFor(trimmed))
            }
        }

        suspend fun findByIdentifierValue(value: String): List<ItemEntity> = itemDao.findByIdentifierValue(value)
    }

internal fun likePatternFor(query: String): String {
    val escaped = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    return "%$escaped%"
}
