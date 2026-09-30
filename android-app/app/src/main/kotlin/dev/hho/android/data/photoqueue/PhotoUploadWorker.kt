package dev.hho.android.data.photoqueue

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.hho.android.domain.AuthRepository
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.flow.first

@HiltWorker
class PhotoUploadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val uploader: PhotoUploader,
        private val authRepository: AuthRepository,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            if (authRepository.authState.first() != AuthState.LoggedIn) return Result.success()
            return when (uploader.drain()) {
                PhotoDrainOutcome.DRAINED -> Result.success()
                PhotoDrainOutcome.RETRY_LATER, PhotoDrainOutcome.BLOCKED -> Result.retry()
            }
        }
    }
