package dev.hho.android.ui.deeplink

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val resolver: DeepLinkResolver,
) : ViewModel() {
    suspend fun resolve(link: ItemDeepLink): DeepLinkResolution = resolver.resolve(link)
}
