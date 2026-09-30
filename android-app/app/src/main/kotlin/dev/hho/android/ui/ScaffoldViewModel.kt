package dev.hho.android.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hho.android.di.BuildInfo
import javax.inject.Inject

@HiltViewModel
class ScaffoldViewModel @Inject constructor(
    private val buildInfo: BuildInfo,
) : ViewModel() {

    val state: ScaffoldUiState = ScaffoldUiState(
        applicationId = buildInfo.applicationId,
        versionName = buildInfo.versionName,
        minSdk = buildInfo.minSdk,
    )
}

data class ScaffoldUiState(
    val applicationId: String,
    val versionName: String,
    val minSdk: Int,
)
