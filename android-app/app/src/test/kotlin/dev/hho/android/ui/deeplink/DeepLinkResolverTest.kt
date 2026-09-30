package dev.hho.android.ui.deeplink

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.hho.android.data.room.HhoDatabase
import dev.hho.android.data.room.ItemEntity
import dev.hho.android.data.room.inMemoryHhoDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class DeepLinkResolverTest {
    private lateinit var db: HhoDatabase
    private lateinit var resolver: DeepLinkResolver
    private val id = "0190a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b"

    private fun item(id: String, code: String?) = ItemEntity(
        id = id, groupChangeSeq = 1, name = "n", description = null, locationId = null,
        quantity = null, shortCode = code, createdAt = null, updatedAt = null, version = null,
    )

    @Before fun setUp() {
        db = inMemoryHhoDatabase()
        resolver = DeepLinkResolver(db.itemDao())
    }

    @After fun tearDown() = db.close()

    @Test fun knownIdResolves() = runTest {
        db.itemDao().upsert(item(id, "AB-1"))
        assertEquals(DeepLinkResolution.Found(id), resolver.resolve(ItemDeepLink.ById(id)))
    }

    @Test fun shortCodeResolvesExactly() = runTest {
        db.itemDao().upsert(item(id, "AB-1"))
        assertEquals(DeepLinkResolution.Found(id), resolver.resolve(ItemDeepLink.ByShortCode("AB-1")))
        assertEquals(
            DeepLinkResolution.NotFound(ItemDeepLink.ByShortCode("ab-1")),
            resolver.resolve(ItemDeepLink.ByShortCode("ab-1")),
        )
    }

    @Test fun unknownTokenIsNotFound() = runTest {
        db.itemDao().upsert(item("other", null))
        val link = ItemDeepLink.ById(id)
        assertEquals(DeepLinkResolution.NotFound(link), resolver.resolve(link))
    }

    @Test fun manifestFilterMatchesArbitraryHostsAndOnlyItemPaths() {
        val pm = ApplicationProvider.getApplicationContext<android.content.Context>().packageManager
        fun matches(url: String) = pm.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE), 0,
        )
        assertNotNull(matches("https://hho.example.com/i/$id"))
        assertNotNull(matches("http://192.168.1.5:7745/i/$id"))
        assertNull(matches("https://hho.example.com/items/$id"))
        assertNull(matches("ftp://hho.example.com/i/$id"))
    }
}
