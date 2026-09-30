package dev.hho.android.ui.syncstatus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.data.sync.SyncStatusRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
internal class SyncProblemViewModel
    @Inject
    constructor(
        repository: SyncStatusRepository,
    ) : ViewModel() {
        val hasProblem: StateFlow<Boolean> =
            repository.observe().map { it.hasProblem }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    }
