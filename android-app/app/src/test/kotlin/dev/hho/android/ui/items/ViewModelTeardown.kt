package dev.hho.android.ui.items

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.robolectric.shadows.ShadowLooper

internal class ViewModelTracker {
    private val tracked = mutableListOf<ViewModel>()

    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { tracked += it }

    fun cancelAll() {
        val jobs = tracked.map { it.viewModelScope.coroutineContext.job }
        jobs.forEach { it.cancel() }
        runBlocking { withTimeout(10_000) { jobs.forEach { it.join() } } }
        tracked.clear()
        ShadowLooper.idleMainLooper()
    }
}
