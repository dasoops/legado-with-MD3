package io.legado.app.ui.book.changecover

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppPattern
import io.legado.app.data.entities.SearchBook
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * 换封面。在线书源封面搜索已移除, 仅保留默认封面选项供本地书籍回退。
 */
class ChangeCoverViewModel(
    application: Application,
) : BaseViewModel(application) {

    val searchStateData = MutableLiveData<Boolean>()
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()
    var name: String = ""
    var author: String = ""

    private val defaultCover by lazy {
        listOf(
            SearchBook(
                originName = "默认封面",
                name = name,
                author = author,
                coverUrl = "use_default_cover"
            )
        )
    }

    val dataFlow = flow { emit(defaultCover) }

    fun initData(arguments: Bundle?) {
        arguments?.let { bundle ->
            bundle.getString("name")?.let {
                name = it
            }
            bundle.getString("author")?.let {
                author = it.replace(AppPattern.authorRegex, "")
            }
        }
    }

    fun initData(name: String, author: String) {
        this.name = name
        this.author = author.replace(AppPattern.authorRegex, "")
    }

    fun startOrStopSearch() {
    }

    fun stopSearch() {
        searchStateData.postValue(false)
        _isSearching.value = false
    }

}
