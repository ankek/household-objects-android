package dev.hho.android

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.hho.android.data.sync.SyncWorker
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = HhoApplication::class)
class HhoApplicationWorkerFactoryTest {

    private val app: HhoApplication = ApplicationProvider.getApplicationContext<Context>() as HhoApplication

    @Test
    fun `the application's workManagerConfiguration factory constructs a SyncWorker via Hilt`() {
        val appFactory = app.workManagerConfiguration.workerFactory
        assertTrue(appFactory is HiltWorkerFactory)

        var created: ListenableWorker? = null
        val observing =
            object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker? = appFactory.createWorker(appContext, workerClassName, workerParameters).also { created = it }
            }

        TestListenableWorkerBuilder<SyncWorker>(app).setWorkerFactory(observing).build()

        assertTrue("HiltWorkerFactory returned ${created?.javaClass}", created is SyncWorker)
    }

    @Test
    fun `the process-wide WorkManager initialised during app startup uses the Hilt worker factory`() {
        val workerFactory = WorkManager.getInstance(app).configuration.workerFactory

        assertTrue("WorkManager was initialised with ${workerFactory.javaClass}", workerFactory is HiltWorkerFactory)
    }
}
