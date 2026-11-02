package io.legado.app.data.repository

import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.HomeDashboardGateway
import io.legado.app.domain.model.DEFAULT_HOME_DASHBOARD_SECTIONS
import io.legado.app.domain.model.HomeDashboardSection
import io.legado.app.domain.model.HomeReadingBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class HomeDashboardRepository(
    private val readRecordDao: ReadRecordDao,
    private val bookDao: BookDao,
    private val localPreferencesRepository: SettingsRepository,
) : HomeDashboardGateway {
    override fun observeTotalReadBooks(): Flow<Int> = readRecordDao.observeTotalReadBookCount()

    override fun observeTotalReadTime(): Flow<Long> = readRecordDao.getTotalReadTime().map { it ?: 0L }

    override fun observeReadTime(date: String): Flow<Long> = readRecordDao.observeReadTimeByDate(date).map { it ?: 0L }

    // 记录身份经过空白归一化，books 表存原始身份，不能在 SQL 里做精确 JOIN；
    // 这里在仓储层用 ReadRecordBookMatcher 按归一化身份解析书名/作者。
    override fun observeRecentBooks(limit: Int): Flow<List<HomeReadingBook>> = combine(
        readRecordDao.observeRecentReadRecords(limit),
        bookDao.flowBookIdentityRefs(),
    ) { records, books ->
        records.map { record ->
            val book = ReadRecordBookMatcher.resolve(
                recordName = record.recordName,
                recordAuthor = record.recordAuthor,
                books = books,
            )
            HomeReadingBook(
                bookUrl = book?.bookUrl,
                name = record.recordName,
                author = record.recordAuthor,
                origin = book?.origin,
                coverPath = book?.displayCover,
                chapterTitle = book?.durChapterTitle,
                chapterProgress = book?.chapterProgress,
            )
        }
    }.flowOn(Dispatchers.Default)

    override fun observeDailyGoal(defaultValue: Int): Flow<Int> = localPreferencesRepository.getPreference(
        LocalPreferencesKeys.DAILY_READING_GOAL_MINUTES,
        defaultValue,
    )

    override fun observeSelectedSourceSetUrl(): Flow<String?> = localPreferencesRepository
        .getPreference(
            LocalPreferencesKeys.HOME_SOURCE_SET_URL,
            "",
        ).map { it.takeIf(String::isNotBlank) }

    override fun observeVisibleSections(): Flow<Set<HomeDashboardSection>> = localPreferencesRepository
        .getPreference(
            LocalPreferencesKeys.HOME_DASHBOARD_SECTIONS,
            DEFAULT_HOME_DASHBOARD_SECTIONS.joinToString(",") { it.storageValue },
        ).map(HomeDashboardSection::fromStorage)

    override suspend fun updateDailyGoal(minutes: Int) {
        localPreferencesRepository.updatePreference(
            LocalPreferencesKeys.DAILY_READING_GOAL_MINUTES,
            minutes,
        )
    }

    override suspend fun updateSelectedSourceSetUrl(sourceUrl: String) {
        localPreferencesRepository.updatePreference(
            LocalPreferencesKeys.HOME_SOURCE_SET_URL,
            sourceUrl,
        )
    }

    override suspend fun updateVisibleSections(sections: Set<HomeDashboardSection>) {
        localPreferencesRepository.updatePreference(
            LocalPreferencesKeys.HOME_DASHBOARD_SECTIONS,
            HomeDashboardSection.entries
                .filter(sections::contains)
                .joinToString(",") { it.storageValue },
        )
    }
}
