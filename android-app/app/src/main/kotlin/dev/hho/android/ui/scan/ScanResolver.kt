package dev.hho.android.ui.scan

import dev.hho.android.data.items.ItemSearch
import dev.hho.android.data.room.ItemDao
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.ui.deeplink.DeepLinkResolution
import dev.hho.android.ui.deeplink.DeepLinkResolver
import dev.hho.android.ui.deeplink.parseItemDeepLink
import javax.inject.Inject

sealed interface ScanResolution {
    data class Single(val item: ItemEntity) : ScanResolution

    data class Multiple(val items: List<ItemEntity>) : ScanResolution

    data class NoMatch(val value: String) : ScanResolution
}

class ScanResolver
    @Inject
    constructor(
        private val deepLinks: DeepLinkResolver,
        private val itemSearch: ItemSearch,
        private val itemDao: ItemDao,
    ) {
        suspend fun resolve(value: String): ScanResolution {
            val link = parseItemDeepLink(value)
            val matches =
                if (link != null) {
                    when (val resolution = deepLinks.resolve(link)) {
                        is DeepLinkResolution.Found -> listOfNotNull(itemDao.getById(resolution.itemId))
                        is DeepLinkResolution.NotFound -> emptyList()
                    }
                } else {
                    itemSearch.findByIdentifierValue(value)
                }
            return when (matches.size) {
                0 -> ScanResolution.NoMatch(value)
                1 -> ScanResolution.Single(matches.single())
                else -> ScanResolution.Multiple(matches)
            }
        }
    }
