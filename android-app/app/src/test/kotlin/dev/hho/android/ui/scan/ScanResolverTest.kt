package dev.hho.android.ui.scan

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.items.ItemSearch
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import dev.hho.android.ui.deeplink.DeepLinkResolver
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ScanResolverTest {
    private lateinit var db: HhoDatabase
    private lateinit var resolver: ScanResolver
    private val id = "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"

    private fun item(id: String, name: String, code: String? = null) = ItemEntity(
        id = id, groupChangeSeq = 1, name = name, description = null, locationId = null,
        quantity = null, shortCode = code, createdAt = null, updatedAt = null, version = null,
    )

    private fun ident(rowId: String, itemId: String, value: String) = ItemIdentificationEntity(
        id = rowId, groupChangeSeq = 1L, itemId = itemId, kind = "barcode", value = value,
        createdAt = 1L, updatedAt = 1L, version = 1L,
    )

    @Before fun setUp() {
        db = inMemoryHhoDatabase()
        resolver = ScanResolver(DeepLinkResolver(db.itemDao()), ItemSearch(db.itemDao()), db.itemDao())
    }

    @After fun tearDown() = db.close()

    @Test fun labelUrlByIdResolvesToSingle() = runTest {
        val a = item(id, "Drill", "AB-1")
        db.itemDao().upsert(a)
        assertEquals(ScanResolution.Single(a), resolver.resolve("https://hho.example.com/i/$id"))
    }

    @Test fun labelUrlByShortCodeResolvesToSingle() = runTest {
        val a = item(id, "Drill", "AB-1")
        db.itemDao().upsert(a)
        assertEquals(ScanResolution.Single(a), resolver.resolve("http://192.168.1.5:7745/i/AB-1"))
    }

    @Test fun unknownLabelUrlIsNoMatchEvenIfSomeItemHasThatIdentifier() = runTest {
        val url = "https://hho.example.com/i/$id"
        db.itemDao().upsert(item("other", "Saw"))
        db.itemIdentificationDao().upsert(ident("i1", "other", url))
        val resolution = resolver.resolve(url)
        assertEquals(ScanResolution.NoMatch(url), resolution)
        assertFalse((resolution as ScanResolution.NoMatch).canCreate)
    }

    @Test fun identifierSingleMatch() = runTest {
        val a = item("a", "Saw")
        db.itemDao().upsert(a)
        db.itemDao().upsert(item("b", "Hammer"))
        db.itemIdentificationDao().upsert(ident("i1", "a", "4006381333931"))
        assertEquals(ScanResolution.Single(a), resolver.resolve("4006381333931"))
    }

    @Test fun identifierMultipleMatches() = runTest {
        val a = item("a", "Saw")
        val b = item("b", "Hammer")
        db.itemDao().upsert(a)
        db.itemDao().upsert(b)
        db.itemIdentificationDao().upsert(ident("i1", "a", "123"))
        db.itemIdentificationDao().upsert(ident("i2", "b", "123"))
        val result = resolver.resolve("123") as ScanResolution.Multiple
        assertEquals(setOf("a", "b"), result.items.map { it.id }.toSet())
    }

    @Test fun identifierNoneAndCaseSensitive() = runTest {
        db.itemDao().upsert(item("a", "Saw"))
        db.itemIdentificationDao().upsert(ident("i1", "a", "abc"))
        assertEquals(ScanResolution.NoMatch("ABC"), resolver.resolve("ABC"))
        assertEquals(ScanResolution.NoMatch("999"), resolver.resolve("999"))
    }

    @Test fun plainUnmatchedValueCanBeCreated() = runTest {
        val resolution = resolver.resolve("4006381333931") as ScanResolution.NoMatch
        assertTrue(resolution.canCreate)
    }
}
