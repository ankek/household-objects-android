package dev.hho.android.ui.deeplink

import dev.hho.android.data.room.ItemDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject

sealed interface DeepLinkResolution {
    data class Found(val itemId: String) : DeepLinkResolution

    data class NotFound(val link: ItemDeepLink) : DeepLinkResolution
}

class DeepLinkResolver @Inject constructor(private val itemDao: ItemDao) {

    suspend fun resolve(link: ItemDeepLink): DeepLinkResolution {
        val id = when (link) {
            is ItemDeepLink.ById -> itemDao.getById(link.itemId)?.id
            is ItemDeepLink.ByShortCode ->
                itemDao.observeAll().first().firstOrNull { it.shortCode == link.shortCode }?.id
        }
        return id?.let { DeepLinkResolution.Found(it) } ?: DeepLinkResolution.NotFound(link)
    }
}
