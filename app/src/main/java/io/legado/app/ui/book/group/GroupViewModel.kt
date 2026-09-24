package io.legado.app.ui.book.group

import android.app.Application
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.repository.BookGroupRepository
import io.legado.app.domain.gateway.BookGroupMutationGateway
import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.NewBookGroup
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class GroupViewModel(
    application: Application,
    private val bookGroupRepository: BookGroupRepository,
    private val bookGroupMutationGateway: BookGroupMutationGateway,
) : BaseViewModel(application) {

    fun upGroup(vararg bookGroup: BookGroup, finally: (() -> Unit)? = null) {
        execute {
            bookGroupRepository.upsert(*bookGroup)
        }.onFinally {
            finally?.invoke()
        }
    }

    fun addGroup(
        groupName: String,
        bookSort: Int,
        enableRefresh: Boolean,
        isPrivate: Boolean,
        cover: String?,
        localDirectoryUri: String? = null,
        isTag: Boolean = false,
        onError: ((Throwable) -> Unit)? = null,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                bookGroupMutationGateway.addGroup(
                    NewBookGroup(
                        groupName = groupName,
                        bookSort = bookSort,
                        enableRefresh = enableRefresh,
                        isPrivate = isPrivate,
                        cover = cover,
                        localDirectoryUri = localDirectoryUri,
                        isTag = isTag,
                    )
                )
                onSuccess()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                onError?.invoke(error)
            }
        }
    }

    fun saveGroup(
        bookGroup: BookGroup,
        onSuccess: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        viewModelScope.launch {
            try {
                bookGroupMutationGateway.saveGroup(
                    bookGroup = bookGroup.toUpdate(),
                )
                onSuccess()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                onError(error)
            }
        }
    }

    fun delGroup(bookGroup: BookGroup, finally: () -> Unit) {
        execute {
            bookGroupMutationGateway.deleteGroup(bookGroup.groupId)
        }.onFinally {
            finally()
        }
    }

    fun clearCover(bookGroup: BookGroup, finally: () -> Unit) {
        execute {
            bookGroupRepository.clearCover(bookGroup.groupId)
        }.onFinally {
            finally()
        }
    }

    private fun BookGroup.toUpdate() = BookGroupUpdate(
        groupId = groupId,
        groupName = groupName,
        cover = cover,
        order = order,
        enableRefresh = enableRefresh,
        show = show,
        bookSort = bookSort,
        isPrivate = isPrivate,
        localDirectoryUri = localDirectoryUri,
    )

}
