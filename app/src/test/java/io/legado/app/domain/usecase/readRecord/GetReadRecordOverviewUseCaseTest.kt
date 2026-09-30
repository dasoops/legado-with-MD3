package io.legado.app.domain.usecase.readRecord

import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.ui.book.readRecord.ReadPeriod
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class GetReadRecordOverviewUseCaseTest {
    private val useCase = GetReadRecordOverviewUseCase()

    @Test
    fun `ALL mode total time uses merged record totals including legacy`() {
        // 每日详情只有新版本数据；旧版遗留时长只存在于 readRecord。
        val details =
            listOf(
                detail("2026-01-02", readTime = 100_000L),
                detail("2026-01-03", readTime = 200_000L),
            )
        val records =
            listOf(
                ReadRecord("", "book", "author", readTime = 800_000L, lastRead = 0L),
            )

        val state = useCase(ReadPeriod.ALL, LocalDate.of(2026, 1, 3), details, records, emptyList())

        // 与阅读记录页/首页的总时长同源：100_000 + 200_000 + 遗留 500_000。
        assertEquals(800_000L, state.totalTime)
    }

    @Test
    fun `period mode total time uses filtered details`() {
        val details =
            listOf(
                detail("2026-01-02", readTime = 100_000L),
                detail("2026-02-02", readTime = 200_000L),
            )
        val records =
            listOf(
                ReadRecord("", "book", "author", readTime = 300_000L, lastRead = 0L),
            )

        val state =
            useCase(ReadPeriod.MONTH, LocalDate.of(2026, 1, 15), details, records, emptyList())

        // 周期视图没有日期的旧版时长无法归属，仍按详情统计。
        assertEquals(100_000L, state.totalTime)
    }

    @Test
    fun `day mode exposes all 24 hourly buckets from sessions`() {
        val date = LocalDate.of(2026, 1, 2)
        val zone = ZoneId.systemDefault()
        val start =
            date
                .atTime(9, 30)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val end =
            date
                .atTime(11, 15)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val session =
            ReadRecordSession(
                deviceId = "",
                bookName = "book",
                bookAuthor = "author",
                startTime = start,
                endTime = end,
            )

        val state =
            useCase(
                ReadPeriod.DAY,
                date,
                details = listOf(detail(date.toString(), 6_300_000L)),
                latestRecords = listOf(ReadRecord("", "book", "author", 6_300_000L, end)),
                allBooks = emptyList(),
                sessions = listOf(session),
            )

        assertEquals(24, state.hourlyTimeData.size)
        assertEquals(1_800_000L, state.hourlyTimeData[9].second)
        assertEquals(3_600_000L, state.hourlyTimeData[10].second)
        assertEquals(900_000L, state.hourlyTimeData[11].second)
        assertEquals(6_300_000L, state.hourlyTimeData.sumOf { it.second })
    }

    private fun detail(
        date: String,
        readTime: Long,
    ) = ReadRecordDetail(
        deviceId = "",
        bookName = "book",
        bookAuthor = "author",
        date = date,
        readTime = readTime,
        readWords = 0L,
    )
}
