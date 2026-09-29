package io.legado.app.data.entities.readRecord

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class ReadRecordTimeSlice(
    val date: LocalDate,
    val hour: Int,
    val startTime: Long,
    val endTime: Long,
) {
    val duration: Long
        get() = endTime - startTime
}

object ReadRecordTimeBuckets {

    /**
     * 按本地时间的整点边界切分会话, 让跨小时和跨午夜的时长分别落入正确桶中。
     * 使用时间线上的 Instant 推进, 避免夏令时切换时出现重复或丢失时长。
     */
    fun split(
        startTime: Long,
        endTime: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<ReadRecordTimeSlice> {
        if (endTime <= startTime) return emptyList()

        val finish = Instant.ofEpochMilli(endTime)
        var cursor = Instant.ofEpochMilli(startTime)
        val slices = mutableListOf<ReadRecordTimeSlice>()
        while (cursor < finish) {
            val localCursor = cursor.atZone(zoneId)
            val nextHour = localCursor
                .truncatedTo(ChronoUnit.HOURS)
                .plusHours(1)
                .toInstant()
            val sliceEnd = minOf(finish, nextHour)
            if (sliceEnd > cursor) {
                slices += ReadRecordTimeSlice(
                    date = localCursor.toLocalDate(),
                    hour = localCursor.hour,
                    startTime = cursor.toEpochMilli(),
                    endTime = sliceEnd.toEpochMilli(),
                )
            }
            cursor = sliceEnd
        }
        return slices
    }
}
