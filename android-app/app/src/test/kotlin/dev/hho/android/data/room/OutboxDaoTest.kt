package dev.hho.android.data.room

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OutboxDaoTest {
    private lateinit var db: HhoDatabase
    private lateinit var dao: OutboxDao

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        dao = db.outboxDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insert_assignsIncreasingSeq_andReadsBackFifo() = runTest {
        val s1 = dao.insert(sample("m-1", "a"))
        val s2 = dao.insert(sample("m-2", "b"))
        val s3 = dao.insert(sample("m-3", "a"))
        assertEquals(listOf(1L, 2L, 3L), listOf(s1, s2, s3))
        assertEquals(listOf("m-1", "m-2", "m-3"), dao.getAllOrdered().map { it.mutationId })
    }

    @Test
    fun getByState_filtersAndLimitsInSeqOrder() = runTest {
        dao.insert(sample("m-1", "a"))
        dao.insert(sample("m-2", "b", OutboxState.HELD))
        dao.insert(sample("m-3", "c"))
        dao.insert(sample("m-4", "d"))
        assertEquals(listOf("m-1", "m-3"), dao.getByState(OutboxState.PENDING, 2).map { it.mutationId })
    }

    @Test
    fun getForEntity_returnsOnlyThatEntityInFifo() = runTest {
        dao.insert(sample("m-1", "a"))
        dao.insert(sample("m-2", "b"))
        dao.insert(sample("m-3", "a", OutboxState.HELD))
        assertEquals(listOf("m-1", "m-3"), dao.getForEntity("item", "a").map { it.mutationId })
        assertEquals(emptyList<OutboxMutationEntity>(), dao.getForEntity("stock_adjustment", "a"))
    }

    @Test
    fun duplicateMutationId_isRejected() = runTest {
        dao.insert(sample("m-1", "a"))
        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking { dao.insert(sample("m-1", "b")) }
        }
    }

    @Test
    fun updateDeleteLookupAndCount() = runTest {
        val s = dao.insert(sample("m-1", "a"))
        dao.insert(sample("m-2", "b"))
        dao.updateState(s, OutboxState.IN_FLIGHT)
        assertEquals(OutboxState.IN_FLIGHT, dao.getByMutationId("m-1")?.state)
        assertEquals(1, dao.observeCount(listOf(OutboxState.PENDING)).first())
        dao.deleteBySeq(s)
        assertNull(dao.getByMutationId("m-1"))
    }
}
