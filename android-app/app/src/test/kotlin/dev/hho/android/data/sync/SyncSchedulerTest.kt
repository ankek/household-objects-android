package dev.hho.android.data.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SyncSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        val configuration =
            Configuration.Builder()
                .setExecutor(Executors.newSingleThreadExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    private fun scheduler() = SyncScheduler(context)

    @Test
    fun `schedulePeriodic enqueues a unique periodic SyncWorker request constrained to CONNECTED and battery-not-low`() {
        scheduler().schedulePeriodic()

        val workInfos =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_WORK_NAME)
                .get()

        assertEquals(1, workInfos.size)
        val workInfo = workInfos.single()
        assertEquals(WorkInfo.State.ENQUEUED, workInfo.state)
        assertTrue(workInfo.tags.contains(SyncWorker::class.java.name))
        assertEquals(NetworkType.CONNECTED, workInfo.constraints.requiredNetworkType)
        assertTrue(workInfo.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `schedulePeriodic called twice keeps the existing schedule (ExistingPeriodicWorkPolicy KEEP) rather than duplicating it`() {
        val scheduler = scheduler()

        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic()

        val workInfos =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(SyncScheduler.PERIODIC_WORK_NAME)
                .get()
                .filter { it.state != WorkInfo.State.CANCELLED }

        assertEquals(1, workInfos.size)
    }

    @Test
    fun `syncNow enqueues a unique one-off SyncWorker request constrained to CONNECTED only`() {
        scheduler().syncNow()

        val workInfos =
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(SyncScheduler.ON_DEMAND_WORK_NAME)
                .get()

        assertEquals(1, workInfos.size)
        val workInfo = workInfos.single()
        assertTrue(workInfo.tags.contains(SyncWorker::class.java.name))
        assertEquals(NetworkType.CONNECTED, workInfo.constraints.requiredNetworkType)
        assertFalse(workInfo.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `the periodic request uses exponential backoff with WorkManager's own minimum initial delay`() {
        val request = SyncScheduler.buildPeriodicRequest()

        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, request.workSpec.backoffDelayDuration)
    }

    @Test
    fun `the on-demand request uses exponential backoff with WorkManager's own minimum initial delay`() {
        val request = SyncScheduler.buildOnDemandRequest()

        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(androidx.work.WorkRequest.MIN_BACKOFF_MILLIS, request.workSpec.backoffDelayDuration)
    }

    @Test
    fun `the periodic request runs at WorkManager's own minimum periodic interval`() {
        val request = SyncScheduler.buildPeriodicRequest()

        assertEquals(
            androidx.work.PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS,
            request.workSpec.intervalDuration,
        )
    }
}
