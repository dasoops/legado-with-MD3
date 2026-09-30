package io.legado.app.data.repository

import android.app.Application
import androidx.room.Room
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.help.config.AppConfigStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class ReadRecordRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReadRecordRepository

    @Before
    fun setUp() {
        // ReadRecordRepository 构造即求值 readRecordEnabled，会访问 AppConfigStore；
        // 生产环境由 App.onCreate 首行 init() 完成，测试里在 Robolectric 应用上下文中补上。
        AppConfigStore.init(RuntimeEnvironment.getApplication())
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = ReadRecordRepository(database.readRecordDao, database, SettingsRepository())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun `merge accumulates target and source sessions`() = runBlocking {
        mergeAndAssert(targetSessionDuration = 100, sourceSessionDuration = 200)
    }

    @Test
    fun `merge keeps source legacy duration`() = runBlocking {
        mergeAndAssert(targetSessionDuration = 100, sourceLegacyTime = 200)
    }

    @Test
    fun `merge keeps target legacy duration`() = runBlocking {
        mergeAndAssert(targetLegacyTime = 100, sourceSessionDuration = 200)
    }

    @Test
    fun `repeating an independent merge does not add time again`() = runBlocking {
        val source = insertRecord(SOURCE_NAME, 200)
        insertRecord(TARGET_NAME, 100)

        repository.mergeIndependentReadRecordsInto(targetRecord(), listOf(source))
        repository.mergeIndependentReadRecordsInto(targetRecord(), listOf(source))

        assertEquals(300L, targetReadTime())
        assertNull(database.readRecordDao.getReadRecord(DEVICE_ID, SOURCE_NAME, AUTHOR))
    }

    @Test
    fun `cross device merge moves sessions and removes source records`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        database.readRecordDao.insert(ReadRecord(targetDevice, TARGET_NAME, AUTHOR, 100, 2_000))
        val source = ReadRecord(sourceDevice, SOURCE_NAME, AUTHOR, 200, 1_000)
        database.readRecordDao.insert(source)
        database.readRecordDao.insertSession(ReadRecordSession(deviceId = sourceDevice, bookName = SOURCE_NAME, bookAuthor = AUTHOR, startTime = 3_000, endTime = 3_200, words = 10))

        repository.mergeIndependentReadRecordsInto(ReadRecord("", TARGET_NAME, AUTHOR), listOf(source))

        assertEquals(300L, database.readRecordDao.getReadRecord(targetDevice, TARGET_NAME, AUTHOR)?.readTime)
        assertEquals(1, database.readRecordDao.getSessionsByBook(targetDevice, TARGET_NAME, AUTHOR).size)
        assertNull(database.readRecordDao.getReadRecord(sourceDevice, SOURCE_NAME, AUTHOR))
    }

    @Test
    fun `synchronized session copies are only counted once in details`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        val date = "1970-01-01"
        database.readRecordDao.insert(ReadRecord(targetDevice, TARGET_NAME, AUTHOR, 200, 2_000))
        database.readRecordDao.insert(ReadRecord(sourceDevice, SOURCE_NAME, AUTHOR, 200, 1_000))
        database.readRecordDao.insertSession(ReadRecordSession(deviceId = targetDevice, bookName = TARGET_NAME, bookAuthor = AUTHOR, startTime = 3_000, endTime = 3_200, words = 10))
        database.readRecordDao.insertSession(ReadRecordSession(deviceId = sourceDevice, bookName = SOURCE_NAME, bookAuthor = AUTHOR, startTime = 3_000, endTime = 3_200, words = 10))
        database.readRecordDao.insertDetail(ReadRecordDetail(targetDevice, TARGET_NAME, AUTHOR, date, 200, 10, 3_000, 3_200))
        database.readRecordDao.insertDetail(ReadRecordDetail(sourceDevice, SOURCE_NAME, AUTHOR, date, 200, 10, 3_000, 3_200))

        repository.mergeIndependentReadRecordsInto(ReadRecord("", TARGET_NAME, AUTHOR), listOf(ReadRecord(sourceDevice, SOURCE_NAME, AUTHOR)))

        val detail = database.readRecordDao.getDetail(targetDevice, TARGET_NAME, AUTHOR, date)
        assertEquals(200L, detail?.readTime)
        assertEquals(10L, detail?.readWords)
        assertEquals(1, database.readRecordDao.getSessionsByBook(targetDevice, TARGET_NAME, AUTHOR).size)
        assertNull(database.readRecordDao.getDetail(sourceDevice, SOURCE_NAME, AUTHOR, date))
    }

    @Test
    fun `repair duplicate sessions removes orphaned duplicate totals`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        val date = "1970-01-01"
        val session = ReadRecordSession(
            deviceId = targetDevice,
            bookName = TARGET_NAME,
            bookAuthor = AUTHOR,
            startTime = 3_000,
            endTime = 3_200,
            words = 10,
        )
        database.readRecordDao.insert(ReadRecord(targetDevice, TARGET_NAME, AUTHOR, 200, 2_000))
        database.readRecordDao.insert(ReadRecord(sourceDevice, TARGET_NAME, AUTHOR, 200, 2_000))
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertSession(session.copy(deviceId = sourceDevice))
        database.readRecordDao.insertDetail(ReadRecordDetail(targetDevice, TARGET_NAME, AUTHOR, date, 200, 10, 3_000, 3_200))
        database.readRecordDao.insertDetail(ReadRecordDetail(sourceDevice, TARGET_NAME, AUTHOR, date, 200, 10, 3_000, 3_200))

        assertEquals(1, repository.repairDuplicateSessions())

        assertEquals(200L, database.readRecordDao.getReadRecord(targetDevice, TARGET_NAME, AUTHOR)?.readTime)
        assertNull(database.readRecordDao.getReadRecord(sourceDevice, TARGET_NAME, AUTHOR))
        assertEquals(200L, database.readRecordDao.getDetail(targetDevice, TARGET_NAME, AUTHOR, date)?.readTime)
        assertNull(database.readRecordDao.getDetail(sourceDevice, TARGET_NAME, AUTHOR, date))
    }

    @Test
    fun `repair duplicate sessions keeps legacy duration on orphaned device`() = runBlocking {
        val targetDevice = "target-device"
        val sourceDevice = "source-device"
        val date = "1970-01-01"
        val session = ReadRecordSession(
            deviceId = targetDevice,
            bookName = TARGET_NAME,
            bookAuthor = AUTHOR,
            startTime = 3_000,
            endTime = 3_200,
            words = 10,
        )
        // sourceDevice 的汇总时长 = 副本 session 200 + 旧版历史 legacy 100，
        // 修复去重后 session 副本被保留在 targetDevice，sourceDevice 应只剩 legacy。
        database.readRecordDao.insert(ReadRecord(targetDevice, TARGET_NAME, AUTHOR, 200, 2_000))
        database.readRecordDao.insert(ReadRecord(sourceDevice, TARGET_NAME, AUTHOR, 300, 2_000))
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertSession(session.copy(deviceId = sourceDevice))
        database.readRecordDao.insertDetail(ReadRecordDetail(targetDevice, TARGET_NAME, AUTHOR, date, 200, 10, 3_000, 3_200))
        database.readRecordDao.insertDetail(ReadRecordDetail(sourceDevice, TARGET_NAME, AUTHOR, date, 300, 10, 3_000, 3_200))

        assertEquals(1, repository.repairDuplicateSessions())

        assertEquals(200L, database.readRecordDao.getReadRecord(targetDevice, TARGET_NAME, AUTHOR)?.readTime)
        assertEquals(100L, database.readRecordDao.getReadRecord(sourceDevice, TARGET_NAME, AUTHOR)?.readTime)
        assertEquals(200L, database.readRecordDao.getDetail(targetDevice, TARGET_NAME, AUTHOR, date)?.readTime)
        assertEquals(100L, database.readRecordDao.getDetail(sourceDevice, TARGET_NAME, AUTHOR, date)?.readTime)
    }

    @Test
    fun `deleting the last aggregate detail removes session-backed total`() = runBlocking {
        val date = "1970-01-01"
        val record = ReadRecord(DEVICE_ID, TARGET_NAME, AUTHOR, 200, 1_200)
        val session = ReadRecordSession(
            deviceId = DEVICE_ID,
            bookName = TARGET_NAME,
            bookAuthor = AUTHOR,
            startTime = 1_000,
            endTime = 1_200,
            words = 10,
        )
        database.readRecordDao.insert(record)
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertDetail(ReadRecordDetail(DEVICE_ID, TARGET_NAME, AUTHOR, date, 200, 10, 1_000, 1_200))

        repository.deleteDetail(database.readRecordDao.getDetail(DEVICE_ID, TARGET_NAME, AUTHOR, date)!!)

        assertNull(database.readRecordDao.getReadRecord(DEVICE_ID, TARGET_NAME, AUTHOR))
        assertNull(database.readRecordDao.getDetail(DEVICE_ID, TARGET_NAME, AUTHOR, date))
        assertEquals(0, database.readRecordDao.getSessionsByBook(DEVICE_ID, TARGET_NAME, AUTHOR).size)
    }

    @Test
    fun `deleting the last session preserves legacy total only`() = runBlocking {
        val date = "1970-01-01"
        val record = ReadRecord(DEVICE_ID, TARGET_NAME, AUTHOR, 300, 1_200)
        val session = ReadRecordSession(
            deviceId = DEVICE_ID,
            bookName = TARGET_NAME,
            bookAuthor = AUTHOR,
            startTime = 1_000,
            endTime = 1_200,
            words = 10,
        )
        database.readRecordDao.insert(record)
        database.readRecordDao.insertSession(session)
        database.readRecordDao.insertDetail(ReadRecordDetail(DEVICE_ID, TARGET_NAME, AUTHOR, date, 200, 10, 1_000, 1_200))

        repository.deleteSession(session)

        assertEquals(100L, database.readRecordDao.getReadRecord(DEVICE_ID, TARGET_NAME, AUTHOR)?.readTime)
        assertNull(database.readRecordDao.getDetail(DEVICE_ID, TARGET_NAME, AUTHOR, date))
        assertEquals(0, database.readRecordDao.getSessionsByBook(DEVICE_ID, TARGET_NAME, AUTHOR).size)
    }

    private suspend fun mergeAndAssert(targetSessionDuration: Long = 0, sourceSessionDuration: Long = 0, targetLegacyTime: Long = 0, sourceLegacyTime: Long = 0) {
        val source = insertRecord(SOURCE_NAME, sourceSessionDuration + sourceLegacyTime, sourceSessionDuration)
        insertRecord(TARGET_NAME, targetSessionDuration + targetLegacyTime, targetSessionDuration)
        repository.mergeIndependentReadRecordsInto(targetRecord(), listOf(source))
        assertEquals(300L, targetReadTime())
        assertNull(database.readRecordDao.getReadRecord(DEVICE_ID, SOURCE_NAME, AUTHOR))
    }

    private suspend fun insertRecord(name: String, readTime: Long, sessionDuration: Long = 0): ReadRecord {
        val record = ReadRecord(DEVICE_ID, name, AUTHOR, readTime, 1_000)
        database.readRecordDao.insert(record)
        if (sessionDuration > 0) {
            val start = if (name == SOURCE_NAME) 2_000L else 1_000L
            database.readRecordDao.insertSession(ReadRecordSession(deviceId = DEVICE_ID, bookName = name, bookAuthor = AUTHOR, startTime = start, endTime = start + sessionDuration, words = 10))
        }
        return record
    }

    private suspend fun targetReadTime() = database.readRecordDao.getReadRecord(DEVICE_ID, TARGET_NAME, AUTHOR)?.readTime ?: -1
    private fun targetRecord() = ReadRecord(DEVICE_ID, TARGET_NAME, AUTHOR)

    private companion object {
        const val DEVICE_ID = "device"
        const val AUTHOR = "author"
        const val TARGET_NAME = "target"
        const val SOURCE_NAME = "source"
    }
}
