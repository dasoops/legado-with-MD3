package io.legado.app.data.entities

import android.content.Context
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.legado.app.R
import kotlinx.parcelize.Parcelize

@Suppress("ConstPropertyName")
@Parcelize
@Entity(tableName = "book_groups")
data class BookGroup(
    @PrimaryKey
    val groupId: Long = 0b1,
    var groupName: String = "",
    var cover: String? = null,
    var order: Int = 0,
    @ColumnInfo(defaultValue = "1")
    var enableRefresh: Boolean = true,
    @ColumnInfo(defaultValue = "1")
    var show: Boolean = true,
    @ColumnInfo(defaultValue = "-1")
    var bookSort: Int = -1,
    @ColumnInfo(defaultValue = "0")
    var isPrivate: Boolean = false,
    var localDirectoryUri: String? = null
) : Parcelable {

    val isTag: Boolean get() = groupId < -100 && groupId != Long.MIN_VALUE

    val isLocalDirectory: Boolean get() = !localDirectoryUri.isNullOrBlank()

    companion object {
        const val IdRoot = -100L
        const val IdAll = -1L
        const val IdLocal = -2L
        const val IdLocalNone = -5L
        const val IdText = -8L
        const val IdReading = -20L
        const val IdUnread = -21L
        const val IdReadFinished = -22L
        const val IdReadFinishedUpdate = -23L
        const val IdReadFinishedComplete = -24L
    }

    data class GroupNameInfo(
        val groupName: String,
        val suffix: String? = null
    )

    fun getManageName(context: Context): GroupNameInfo {
        return when (groupId) {
            IdAll -> GroupNameInfo(groupName, context.getString(R.string.all))
            IdLocal -> GroupNameInfo(groupName, context.getString(R.string.local))
            IdLocalNone -> GroupNameInfo(groupName, context.getString(R.string.local_no_group))
            IdText -> GroupNameInfo(groupName, context.getString(R.string.noval))
            IdReading -> GroupNameInfo(groupName, context.getString(R.string.is_reading))
            IdUnread -> GroupNameInfo(groupName, context.getString(R.string.is_unread))
            IdReadFinished -> GroupNameInfo(groupName, context.getString(R.string.is_read_finished))
            IdReadFinishedUpdate -> GroupNameInfo(groupName, context.getString(R.string.is_read_finished_update))
            IdReadFinishedComplete -> GroupNameInfo(groupName, context.getString(R.string.is_read_finished_complete))
            else -> GroupNameInfo(groupName)
        }
    }

    fun getRealBookSort(defaultBookSort: Int): Int {
        if (bookSort < 0) {
            return defaultBookSort
        }
        return bookSort
    }

    override fun hashCode(): Int {
        return 31 * groupId.hashCode() + (localDirectoryUri?.hashCode() ?: 0)
    }

    override fun equals(other: Any?): Boolean {
        if (other is BookGroup) {
            return other.groupId == groupId
                    && other.groupName == groupName
                    && other.cover == cover
                    && other.bookSort == bookSort
                    && other.enableRefresh == enableRefresh
                    && other.show == show
                    && other.order == order
                    && other.isPrivate == isPrivate
                    && other.localDirectoryUri == localDirectoryUri
        }
        return false
    }

}
