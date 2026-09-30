package dev.hho.android.data.items

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.ItemIdentificationEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ItemSearchTest {

    private lateinit var db: HhoDatabase
    private lateinit var search: ItemSearch

    @Before
    fun setUp() {
        db = inMemoryHhoDatabase()
        search = ItemSearch(db.itemDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun itemFixture(id: String, name: String, shortCode: String? = null) =
        ItemEntity(
            id = id,
            groupChangeSeq = 1L,
            name = name,
            description = null,
            locationId = null,
            quantity = 1L,
            shortCode = shortCode,
            createdAt = 1L,
            updatedAt = 1L,
            version = 1L,
        )

    private fun identifierFixture(id: String, itemId: String, value: String, kind: String = "barcode") =
        ItemIdentificationEntity(
            id = id,
            groupChangeSeq = 1L,
            itemId = itemId,
            kind = kind,
            value = value,
            createdAt = 1L,
            updatedAt = 1L,
            version = 1L,
        )

    @Test
    fun `blank query returns every synced item, sorted by name`() = runTest {
        db.itemDao().upsert(itemFixture("item-2", name = "Saw"))
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))

        val results = search.results("").first()

        assertEquals(listOf("item-1", "item-2"), results.map { it.id })
    }

    @Test
    fun `whitespace-only query is treated the same as a blank one`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))

        val results = search.results("   ").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `matches a substring of the item name`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill"))
        db.itemDao().upsert(itemFixture("item-2", name = "Table Saw"))

        val results = search.results("Drill").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `matches a substring of the short code`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill", shortCode = "DRL-42"))
        db.itemDao().upsert(itemFixture("item-2", name = "Table Saw", shortCode = "SAW-1"))

        val results = search.results("drl").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `matches a substring of a linked identifier value`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "EAN-1234567890"))

        val results = search.results("1234567").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `matching is case-insensitive across name, short code, and identifier value`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill", shortCode = "drl-42"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "eAn-999"))

        assertEquals(listOf("item-1"), search.results("DRILL").first().map { it.id })
        assertEquals(listOf("item-1"), search.results("DRL-42").first().map { it.id })
        assertEquals(listOf("item-1"), search.results("ean-999").first().map { it.id })
    }

    @Test
    fun `an item is not duplicated when its name and one of its identifiers both match`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "drill-barcode"))

        val results = search.results("drill").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `an item is not duplicated when more than one of its identifiers matches`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "drill-barcode-1"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-2", itemId = "item-1", value = "drill-barcode-2"))

        val results = search.results("drill").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `a literal percent sign in the query is matched literally, not treated as a wildcard`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "100% Cotton Rag"))
        db.itemDao().upsert(itemFixture("item-2", name = "1000 Screws"))

        val results = search.results("100%").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `a literal underscore in the query is matched literally, not treated as a single-char wildcard`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "SKU_123"))
        db.itemDao().upsert(itemFixture("item-2", name = "Table Saw"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-2", itemId = "item-2", value = "SKUX123"))

        val results = search.results("SKU_123").first()

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `findByIdentifierValue resolves the exact barcode to its owning item`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "EAN-1234567890"))

        val results = search.findByIdentifierValue("EAN-1234567890")

        assertEquals(listOf("item-1"), results.map { it.id })
    }

    @Test
    fun `findByIdentifierValue is exact, not substring — a partial value finds nothing`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "EAN-1234567890"))

        val results = search.findByIdentifierValue("1234567")

        assertEquals(emptyList<String>(), results.map { it.id })
    }

    @Test
    fun `findByIdentifierValue with no matching identifier returns an empty list, not a crash`() = runTest {
        val results = search.findByIdentifierValue("does-not-exist")

        assertEquals(emptyList<String>(), results.map { it.id })
    }

    @Test
    fun `findByIdentifierValue is case-sensitive and does not trim`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Cordless Drill"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "EAN-abc"))

        assertEquals(emptyList<String>(), search.findByIdentifierValue("ean-ABC").map { it.id })
        assertEquals(emptyList<String>(), search.findByIdentifierValue(" EAN-abc").map { it.id })
        assertEquals(listOf("item-1"), search.findByIdentifierValue("EAN-abc").map { it.id })
    }

    @Test
    fun `findByIdentifierValue returns each item once even with duplicate identifiers, and all sharing items`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        db.itemDao().upsert(itemFixture("item-2", name = "Saw"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "X1", kind = "barcode"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-2", itemId = "item-1", value = "X1", kind = "qr"))
        db.itemIdentificationDao().upsert(identifierFixture("ident-3", itemId = "item-2", value = "X1"))

        assertEquals(setOf("item-1", "item-2"), search.findByIdentifierValue("X1").map { it.id }.toSet())
        assertEquals(2, search.findByIdentifierValue("X1").size)
    }

    @Test
    fun `an identifier orphaned from any item does not resolve or match`() = runTest {
        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "gone", value = "ORPHAN"))

        assertEquals(emptyList<String>(), search.findByIdentifierValue("ORPHAN").map { it.id })
        assertEquals(emptyList<String>(), search.results("ORPHAN").first().map { it.id })
    }

    @Test
    fun `a non-ASCII query still matches with the same case`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Ölkanne"))

        assertEquals(listOf("item-1"), search.results("Öl").first().map { it.id })
    }

    @Test
    fun `results re-emits when a new matching identifier is synced`() = runTest {
        db.itemDao().upsert(itemFixture("item-1", name = "Drill"))
        val flow = search.results("ZZ99")
        assertEquals(emptyList<String>(), flow.first().map { it.id })

        db.itemIdentificationDao().upsert(identifierFixture("ident-1", itemId = "item-1", value = "ZZ99"))

        assertEquals(listOf("item-1"), flow.first().map { it.id })
    }

    @Test
    fun `likePatternFor wraps the query and escapes backslash, percent, and underscore`() {
        assertEquals("%50\\%\\_off\\\\ish%", likePatternFor("50%_off\\ish"))
    }
}
