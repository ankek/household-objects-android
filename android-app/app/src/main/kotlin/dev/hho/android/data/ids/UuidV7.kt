package dev.hho.android.data.ids

import java.security.SecureRandom
import java.util.Random
import java.util.UUID

public class UuidV7Generator(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = SecureRandom(),
) {
    private var lastMillis: Long = -1L
    private var counter: Int = 0

    public fun generate(): String = next().toString()

    @Synchronized
    public fun next(): UUID {
        val now = clock().coerceIn(0L, MAX_MILLIS)
        if (now > lastMillis) {
            lastMillis = now
            counter = random.nextInt(SEED_BOUND)
        } else if (counter < COUNTER_MAX) {
            counter++
        } else {
            check(lastMillis < MAX_MILLIS) { "UUIDv7 48-bit timestamp exhausted" }
            lastMillis++
            counter = random.nextInt(SEED_BOUND)
        }
        val msb = (lastMillis shl 16) or (VERSION_7 shl 12) or counter.toLong()
        val lsb = (random.nextLong() and RAND_B_MASK) or VARIANT_BITS
        return UUID(msb, lsb)
    }

    private companion object {
        const val MAX_MILLIS = 0xFFFF_FFFF_FFFFL
        const val COUNTER_MAX = 0xFFF
        const val SEED_BOUND = 0x800
        const val VERSION_7 = 0x7L
        const val RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL
        const val VARIANT_BITS = Long.MIN_VALUE
    }
}
