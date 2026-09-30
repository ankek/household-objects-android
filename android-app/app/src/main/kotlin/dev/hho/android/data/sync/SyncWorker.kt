package dev.hho.android.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.hho.android.data.auth.DeviceIdProvider
import dev.hho.android.domain.AuthRepository
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val syncEngine: SyncEngine,
        private val deviceIdProvider: DeviceIdProvider,
        private val authRepository: AuthRepository,
        private val recorder: SyncRunRecorder,
    ) : CoroutineWorker(context, params) {

        override suspend fun doWork(): Result {
            if (authRepository.authState.first() != AuthState.LoggedIn) {
                return Result.success()
            }

            val deviceId = deviceIdProvider.deviceId()
            val result =
                try {
                    syncEngine.sync(deviceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    recorder.recordFailure(e.javaClass.simpleName)
                    return Result.retry()
                }
            return result.fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
        }
    }
