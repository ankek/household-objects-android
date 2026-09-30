package dev.hho.android.data.ids

import java.util.Random
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidV7GeneratorTest {
    private class FixedRandom(private val long: Long, private val int: Int) : Random() {
        override fun nextLong(): Long = long
        override fun nextInt(bound: Int): Int = int
    }

    private fun cmp(a: UUID, b: UUID): Int {
        val m = java.lang.Long.compareUnsigned(a.mostSignificantBits, b.mostSignificantBits)
        return if (m != 0) m else java.lang.Long.compareUnsigned(a.leastSignificantBits, b.leastSignificantBits)
    }

    private fun millisOf(u: UUID): Long = u.mostSignificantBits ushr 16

    @Test
    fun layoutMatchesRfc9562ForFixedClockAndRandom() {
        val ms = 0x0123_4567_89ABL
        val gen = UuidV7Generator({ ms }, FixedRandom(-1L, 0x155))
        val id = gen.next()
        assertEquals("01234567-89ab-7155-bfff-ffffffffffff", id.toString())
        assertEquals(ms, millisOf(id))
        assertEquals(7, (id.mostSignificantBits ushr 12).toInt() and 0xF)
        assertEquals(0b10L, id.leastSignificantBits ushr 62)
    }

    @Test
    fun zeroRandomKeepsVersionAndVariantBits() {
        val id = UuidV7Generator({ 1L }, FixedRandom(0L, 0)).next()
        assertEquals("00000000-0001-7000-8000-000000000000", id.toString())
    }

    @Test
    fun stringFormIsLowercaseCanonical() {
        val s = UuidV7Generator({ 0xABCDEF012345L }, FixedRandom(-1L, 0xABC and 0x7FF)).generate()
        assertEquals(s.lowercase(), s)
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(s))
        assertEquals(UUID.fromString(s).toString(), s)
    }

    @Test
    fun strictlyIncreasingWithinOneMillisecond() {
        val gen = UuidV7Generator({ 1_000L }, Random(1))
        val ids = List(2_000) { gen.next() }
        ids.zipWithNext().forEach { (a, b) ->
            assertTrue("$a < $b", cmp(a, b) < 0)
            assertTrue(a.toString() < b.toString())
        }
    }

    @Test
    fun strictlyIncreasingAcrossMilliseconds() {
        var now = 5_000L
        val gen = UuidV7Generator({ now }, Random(2))
        val ids = buildList {
            repeat(50) { step ->
                now += step % 3
                add(gen.next())
            }
        }
        ids.zipWithNext().forEach { (a, b) ->
            assertTrue(cmp(a, b) < 0)
            assertTrue(a.toString() < b.toString())
        }
    }

    @Test
    fun counterOverflowBorrowsNextMillisecondAndStaysOrdered() {
        val gen = UuidV7Generator({ 9_000L }, Random(3))
        val ids = List(10_000) { gen.next() }
        ids.zipWithNext().forEach { (a, b) -> assertTrue(cmp(a, b) < 0) }
        assertTrue(millisOf(ids.last()) > 9_000L)
    }

    @Test
    fun clockRegressionNeverProducesSmallerId() {
        var now = 10_000L
        val gen = UuidV7Generator({ now }, Random(4))
        val first = gen.next()
        now = 3_000L
        val second = gen.next()
        assertTrue(cmp(first, second) < 0)
        assertEquals(millisOf(first), millisOf(second))
        now = 20_000L
        val third = gen.next()
        assertTrue(cmp(second, third) < 0)
        assertEquals(20_000L, millisOf(third))
    }

    @Test
    fun tenThousandIdsAreUnique() {
        val gen = UuidV7Generator()
        assertEquals(10_000, List(10_000) { gen.generate() }.toSet().size)
    }
}
