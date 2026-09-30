package io.legado.app.data.entities.readRecord

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadRecordTimeBucketsTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `split session at every local hour boundary`() {
        val start = Instant.parse("2026-01-02T09:30:00Z").toEpochMilli()
        val end = Instant.parse("2026-01-02T11:15:00Z").toEpochMilli()

        val slices = ReadRecordTimeBuckets.split(start, end, utc)

        assertEquals(listOf(30 * 60_000L, 60 * 60_000L, 15 * 60_000L), slices.map { it.duration })
        assertEquals(listOf(9, 10, 11), slices.map { it.hour })
        assertEquals(listOf("2026-01-02", "2026-01-02", "2026-01-02"), slices.map { it.date.toString() })
    }

    @Test
    fun `split session across midnight`() {
        val start = Instant.parse("2026-01-02T23:30:00Z").toEpochMilli()
        val end = Instant.parse("2026-01-03T00:30:00Z").toEpochMilli()

        val slices = ReadRecordTimeBuckets.split(start, end, utc)

        assertEquals(listOf("2026-01-02", "2026-01-03"), slices.map { it.date.toString() })
        assertEquals(listOf(23, 0), slices.map { it.hour })
        assertEquals(listOf(30 * 60_000L, 30 * 60_000L), slices.map { it.duration })
    }
}
