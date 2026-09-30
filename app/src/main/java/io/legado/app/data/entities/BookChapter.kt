package io.legado.app.data.entities

import android.annotation.SuppressLint
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.RuleBigDataHelp
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.analyzeRule.RuleDataInterface
import io.legado.app.utils.ChineseUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.replace
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import splitties.init.appCtx

@Parcelize
@Entity(
    tableName = "chapters",
    primaryKeys = ["url", "bookUrl"],
    indices = [
        (Index(value = ["bookUrl"], unique = false)),
        (Index(value = ["bookUrl", "index"], unique = true)),
    ],
    foreignKeys = [
        (
            ForeignKey(
                entity = Book::class,
                parentColumns = ["bookUrl"],
                childColumns = ["bookUrl"],
                onDelete = ForeignKey.CASCADE,
            )
            ),
    ],
) // 删除书籍时自动删除章节
data class BookChapter(
    var url: String = "", // 章节地址
    var title: String = "", // 章节标题
    var isVolume: Boolean = false, // 是否是卷名
    @ColumnInfo(defaultValue = "0")
    var tocLevel: Int = 0, // 目录层级，0 为顶层
    var baseUrl: String = "",
    var bookUrl: String = "",
    var index: Int = 0,
    var isVip: Boolean = false,
    var isPay: Boolean = false,
    var resourceUrl: String? = null,
    var tag: String? = null,
    var wordCount: String? = null,
    var start: Long? = null,
    var end: Long? = null,
    var startFragmentId: String? = null,
    var endFragmentId: String? = null,
    var variable: String? = null,
    var reviewImg: String? = null,
) : Parcelable, RuleDataInterface {

    @delegate:Transient
    @delegate:Ignore
    @IgnoredOnParcel
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    @Ignore
    @IgnoredOnParcel
    var titleMD5: String? = null

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = GSON.toJson(variableMap)
        }
        return true
    }

    override fun putBigVariable(key: String, value: String?) {
        RuleBigDataHelp.putChapterVariable(bookUrl, url, key, value)
    }

    override fun getBigVariable(key: String): String? = RuleBigDataHelp.getChapterVariable(bookUrl, url, key)

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun primaryStr(): String = bookUrl + url

    fun getDisplayTitle(
        replaceRules: List<ReplaceRule>? = null,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        chineseConverterType: Int,
    ): String {
        var displayTitle = title.replace(AppPattern.rnRegex, "")
        if (chineseConvert) {
            when (chineseConverterType) {
                1 -> displayTitle = ChineseUtils.t2s(displayTitle)
                2 -> displayTitle = ChineseUtils.s2t(displayTitle)
            }
        }
        if (useReplace && replaceRules != null) {
            kotlin.run {
                replaceRules.forEach { item ->
                    if (item.pattern.isNotEmpty()) {
                        try {
                            val mDisplayTitle = if (item.isRegex) {
                                displayTitle.replace(
                                    item.regex,
                                    item.replacement,
                                    item.getValidTimeoutMillisecond(),
                                )
                            } else {
                                displayTitle.replace(item.pattern, item.replacement)
                            }
                            if (mDisplayTitle.isNotBlank()) {
                                displayTitle = mDisplayTitle
                            }
                        } catch (e: RegexTimeoutException) {
                            item.isEnabled = false
                            appDb.replaceRuleDao.update(item)
                        } catch (e: CancellationException) {
                            return@run
                        } catch (e: Exception) {
                            AppLog.put("${item.name}替换出错\n替换内容\n$displayTitle", e)
                            appCtx.toastOnUi("${item.name}替换出错")
                        }
                    }
                }
            }
        }
        return displayTitle
    }

    fun getAbsoluteURL(): String {
        // 二级目录解析的卷链接为空 返回目录页的链接
        if (url.startsWith(title) && isVolume) return baseUrl
        val urlMatch = AnalyzeUrl.paramPattern.find(url)
        val urlBefore = if (urlMatch != null) url.substring(0, urlMatch.range.first) else url
        val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)
        return if (urlMatch == null) {
            urlAbsoluteBefore
        } else {
            "$urlAbsoluteBefore," + url.substring(urlMatch.range.last + 1)
        }
    }

    private fun ensureTitleMD5Init() {
        if (titleMD5 == null) {
            titleMD5 = MD5Utils.md5Encode16(title)
        }
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFileName(suffix: String = "nb"): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.%s", index, titleMD5, suffix)
    }

    @SuppressLint("DefaultLocale")
    @Suppress("unused")
    fun getFontName(): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.ttf", index, titleMD5)
    }
}
