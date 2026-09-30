package dev.hho.android.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import org.robolectric.shadows.ShadowLooper

internal class ViewModelTracker {
    private val tracked = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { tracked += it }

    fun cancelAll() {
        tracked.forEach { it.viewModelScope.cancel() }
        tracked.clear()
        ShadowLooper.idleMainLooper()
    }
}
