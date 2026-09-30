package dev.hho.android.data.photoqueue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.hho.android.data.apiclient.HhoApiClient
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.domain.AuthRepository
import dev.hho.android.domain.AuthState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PhotoSchedulerAndWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HhoDatabase

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(Executors.newSingleThreadExecutor()).build(),
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `schedule enqueues unique work constrained to CONNECTED`() {
        PhotoScheduler(context).schedule()
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PhotoScheduler.UNIQUE_WORK_NAME).get()
        val info = infos.single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertTrue(info.tags.contains(PhotoUploadWorker::class.java.name))
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test
    fun `schedule twice keeps a single unique work`() {
        val s = PhotoScheduler(context)
        s.schedule()
        s.schedule()
        val live =
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(PhotoScheduler.UNIQUE_WORK_NAME).get()
                .filter { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, live.size)
    }

    @Test
    fun `request uses exponential backoff`() {
        val spec = PhotoScheduler.buildRequest().workSpec
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    }

    private class FakeAuth(initial: AuthState) : AuthRepository {
        override val authState: Flow<AuthState> = MutableStateFlow(initial)

        override suspend fun login(
            username: String,
            password: String,
        ): Result<Unit> = error("unused")

        override suspend fun logout() = error("unused")
    }

    private class FakeUploader(private val outcome: PhotoDrainOutcome, repo: PhotoQueueRepository, db: HhoDatabase) :
        PhotoUploader(repo, db.photoQueueDao(), db.outboxDao(), unusedApi()) {
        var calls = 0

        override suspend fun drain(clock: () -> Long): PhotoDrainOutcome {
            calls++
            return outcome
        }
    }

    private fun worker(
        outcome: PhotoDrainOutcome,
        auth: AuthState,
    ): Pair<PhotoUploadWorker, FakeUploader> {
        val repo = PhotoQueueRepository(File(context.cacheDir, "pq"), db.photoQueueDao(), { 0L }) { it.inputStream() }
        val uploader = FakeUploader(outcome, repo, db)
        val w =
            TestListenableWorkerBuilder<PhotoUploadWorker>(context)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ): ListenableWorker = PhotoUploadWorker(appContext, workerParameters, uploader, FakeAuth(auth))
                    },
                ).build()
        return w to uploader
    }

    @Test
    fun `logged out is a no-op success that never drains`() =
        runTest {
            val (w, u) = worker(PhotoDrainOutcome.DRAINED, AuthState.LoggedOut)
            assertEquals(ListenableWorker.Result.success(), w.doWork())
            assertEquals(0, u.calls)
        }

    @Test
    fun `outcome mapping`() =
        runTest {
            assertEquals(ListenableWorker.Result.success(), worker(PhotoDrainOutcome.DRAINED, AuthState.LoggedIn).first.doWork())
            assertEquals(ListenableWorker.Result.retry(), worker(PhotoDrainOutcome.RETRY_LATER, AuthState.LoggedIn).first.doWork())
            assertEquals(ListenableWorker.Result.retry(), worker(PhotoDrainOutcome.BLOCKED, AuthState.LoggedIn).first.doWork())
        }
}

private fun unusedApi(): HhoApiClient = HhoApiClient(OkHttpClient(), OkHttpClient())
