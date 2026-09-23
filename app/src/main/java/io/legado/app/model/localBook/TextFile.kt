package io.legado.app.model.localBook

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.exception.EmptyFileException
import io.legado.app.help.DefaultData
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.book.upKind
import io.legado.app.utils.EncodingDetect
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.Utf8BomUtils
import java.io.FileNotFoundException
import java.nio.charset.Charset
import java.util.regex.PatternSyntaxException
import kotlin.math.min

class TextFile(private var book: Book) {


    @Suppress("ConstPropertyName")
    companion object {
        private val padRegex = "^[\\n\\s]+".toRegex()
        private const val txtBufferSize = 8 * 1024 * 1024
        private var textFile: TextFile? = null

        @Synchronized
        private fun getTextFile(book: Book): TextFile {
            if (textFile == null || textFile?.book?.bookUrl != book.bookUrl || book.isLocalModified()) {
                textFile = TextFile(book)
                return textFile!!
            }
            textFile?.book = book
            return textFile!!
        }

        @Throws(FileNotFoundException::class)
        fun getChapterList(book: Book): ArrayList<BookChapter> {
            return getTextFile(book).getChapterList()
        }

        @Synchronized
        @Throws(FileNotFoundException::class)
        fun getContent(book: Book, bookChapter: BookChapter): String {
            return getTextFile(book).getContent(bookChapter)
        }

        fun clear() {
            textFile = null
        }

    }

    private val blank: Byte = 0x0a

    //默认从文件中获取数据的长度
    private val bufferSize = 512000


    private var charset: Charset = book.fileCharset()

    private var txtBuffer: ByteArray? = null
    private var bufferStart = -1L
    private var bufferEnd = -1L

    private fun refreshCharset() {
        charset = book.fileCharset()
    }

    /**
     * 获取目录
     */
    @Throws(FileNotFoundException::class, SecurityException::class, EmptyFileException::class)
    fun getChapterList(): ArrayList<BookChapter> {
        refreshCharset()
        val modified = book.isLocalModified()
        if (book.charset == null || book.tocUrl.isBlank() || modified) {
            LocalBook.getBookInputStream(book).use { bis ->
                val buffer = ByteArray(bufferSize)
                val length = bis.read(buffer)
                if (length == -1) throw EmptyFileException("Unexpected Empty Txt File")
                if (book.charset.isNullOrBlank() || modified) {
                    book.charset = EncodingDetect.getEncode(buffer.copyOf(length))
                }
                charset = book.fileCharset()
                if (book.tocUrl.isBlank() || modified) {
                    val blockContent = String(buffer, 0, length, charset)
                    book.tocUrl = getTocRule(blockContent)?.chapterRule ?: ""
                }
            }
        }
        val volumePattern = getVolumePattern(book.tocUrl)
        val (toc, wordCount) = analyze(Regex(book.tocUrl, RegexOption.MULTILINE), volumePattern)
        book.wordCount = StringUtils.wordCountFormat(wordCount)
        book.upKind()
        toc.forEachIndexed { index, bookChapter ->
            bookChapter.index = index
            bookChapter.bookUrl = book.bookUrl
            bookChapter.url = MD5Utils.md5Encode16(book.originName + index + bookChapter.title)
        }
        var tocLevel = 0
        toc.forEach { chapter ->
            if (chapter.isVolume) tocLevel = 0
            chapter.tocLevel = tocLevel
            if (chapter.isVolume) tocLevel = 1
        }
        return toc
    }

    fun getContent(chapter: BookChapter): String {
        refreshCharset()
        val start = chapter.start!!
        val end = chapter.end!!
        if (txtBuffer == null || start > bufferEnd || end < bufferStart) {
            LocalBook.getBookInputStream(book).use { bis ->
                bufferStart = txtBufferSize * (start / txtBufferSize)
                txtBuffer = ByteArray(min(txtBufferSize, bis.available() - bufferStart.toInt()))
                bufferEnd = bufferStart + txtBuffer!!.size
                bis.skip(bufferStart)
                bis.read(txtBuffer)
            }
        }

        val count = (end - start).toInt()
        val buffer = ByteArray(count)

        @Suppress("ConvertTwoComparisonsToRangeCheck")
        if (start < bufferEnd && end > bufferEnd || start < bufferStart && end > bufferStart) {
            /** 章节内容在缓冲区交界处 */
            LocalBook.getBookInputStream(book).use { bis ->
                bis.skip(start)
                bis.read(buffer)
            }
        } else {
            /** 章节内容在缓冲区内 */
            txtBuffer!!.copyInto(
                buffer,
                0,
                (start - bufferStart).toInt(),
                (end - bufferStart).toInt()
            )
        }

        val content = String(buffer, charset)
        // 无规则时的目录标题是占位名称, 不能据此截掉原文.
        return (if (book.tocUrl.isBlank()) content else content.substringAfter(chapter.title))
            .replace(padRegex, "　　")
    }

    /**
     * 按规则解析目录
     */
    private fun analyze(pattern: Regex?, volumePattern: Regex? = null): Pair<ArrayList<BookChapter>, Int> {
        if (pattern == null || pattern.pattern.isEmpty()) {
            return analyzeWholeText()
        }
        val toc = arrayListOf<BookChapter>()
        var bookWordCount = 0
        LocalBook.getBookInputStream(book).use { bis ->
            var blockContent: String
            //加载章节
            var curOffset: Long = 0
            //读取的长度
            var length: Int
            var lastChapterWordCount = 0
            val buffer = ByteArray(bufferSize)
            var bufferStart = 3
            bis.read(buffer, 0, 3)
            if (Utf8BomUtils.hasBom(buffer)) {
                bufferStart = 0
                curOffset = 3
            }
            //获取文件中的数据到buffer，直到没有数据为止
            while (bis.read(
                    buffer, bufferStart, bufferSize - bufferStart
                ).also { length = it } > 0
            ) {
                var end = bufferStart + length
                if (end == bufferSize) {
                    for (i in bufferStart + length - 1 downTo 0) {
                        if (buffer[i] == blank) {
                            end = i
                            break
                        }
                    }
                }
                //将数据转换成String, 不能超过length
                blockContent = String(buffer, 0, end, charset)
                buffer.copyInto(buffer, 0, end, bufferStart + length)
                bufferStart = bufferStart + length - end
                length = end
                //当前Block下使过的String的指针
                var seekPos = 0
                //进行正则匹配
                for (m in pattern.findAll(blockContent)) { //获取匹配到的字符在字符串中的起始位置
                    val chapterStart = m.range.first
                    //获取章节内容
                    val chapterContent = blockContent.substring(seekPos, chapterStart)
                    val chapterLength = chapterContent.toByteArray(charset).size.toLong()
                    if (seekPos == 0 && chapterStart != 0) {
                        /**
                         * 如果 seekPos == 0 && chapterStart != 0 表示当前block处前面有一段内容
                         * 第一种情况一定是序章 第二种情况是上一个章节的内容
                         */
                        if (toc.isEmpty()) { //如果当前没有章节，那么就是序章
                            //加入简介
                            if (chapterContent.isNotBlank()) {
                                val qyChapter = BookChapter()
                                qyChapter.title = "前言"
                                qyChapter.start = curOffset
                                qyChapter.end = curOffset + chapterLength
                                qyChapter.wordCount =
                                    StringUtils.wordCountFormat(chapterContent.length)
                                toc.add(qyChapter)
                                book.intro = if (chapterContent.length <= 500) {
                                    chapterContent
                                } else {
                                    chapterContent.substring(0, 500)
                                }
                            }
                            //创建当前章节
                            val curChapter = BookChapter()
                            curChapter.title = m.value
                            curChapter.start = curOffset + chapterLength
                            curChapter.end = curChapter.start
                            toc.add(curChapter)
                        } else { //否则就block分割之后，上一个章节的剩余内容
                            //获取上一章节
                            val lastChapter = toc.last()
                            if (volumePattern == null) {
                                lastChapter.isVolume =
                                    chapterContent.substringAfter(lastChapter.title).isBlank()
                            } else {
                                lastChapter.isVolume = volumePattern.containsMatchIn(lastChapter.title)
                            }
                            //将当前段落添加上一章去
                            lastChapter.end = lastChapter.end!! + chapterLength
                            lastChapterWordCount += chapterContent.length
                            lastChapter.wordCount =
                                StringUtils.wordCountFormat(lastChapterWordCount)
                            //创建当前章节
                            val curChapter = BookChapter()
                            curChapter.title = m.value
                            curChapter.start = lastChapter.end
                            curChapter.end = curChapter.start
                            toc.add(curChapter)
                        }
                        bookWordCount += chapterContent.length
                        lastChapterWordCount = 0
                    } else {
                        if (toc.isNotEmpty()) { //获取章节内容
                            //获取上一章节
                            val lastChapter = toc.last()
                            if (volumePattern == null) {
                                lastChapter.isVolume =
                                    chapterContent.substringAfter(lastChapter.title).isBlank()
                            } else {
                                lastChapter.isVolume = volumePattern.containsMatchIn(lastChapter.title)
                            }
                            lastChapter.end =
                                lastChapter.start!! + chapterLength
                            lastChapter.wordCount =
                                StringUtils.wordCountFormat(chapterContent.length)
                            //创建当前章节
                            val curChapter = BookChapter()
                            curChapter.title = m.value
                            curChapter.start = lastChapter.end
                            curChapter.end = curChapter.start
                            toc.add(curChapter)
                        } else { //如果章节不存在则创建章节
                            val curChapter = BookChapter()
                            curChapter.title = m.value
                            curChapter.start = curOffset
                            curChapter.end = curOffset
                            curChapter.wordCount =
                                StringUtils.wordCountFormat(chapterContent.length)
                            toc.add(curChapter)
                        }
                        bookWordCount += chapterContent.length
                        lastChapterWordCount = 0
                    }
                    //设置指针偏移
                    seekPos += chapterContent.length
                }
                val wordCount = blockContent.length - seekPos
                bookWordCount += wordCount
                lastChapterWordCount += wordCount
                //block的偏移点
                curOffset += length.toLong()
                //设置上一章的结尾
                toc.lastOrNull()?.let {
                    it.end = curOffset
                    it.wordCount = StringUtils.wordCountFormat(lastChapterWordCount)
                }
            }
            // 最后一章没有后继标题触发分卷判定.
            toc.lastOrNull()?.let { chapter ->
                if (volumePattern != null) {
                    chapter.isVolume = volumePattern.containsMatchIn(chapter.title)
                }
            }
        }
        System.gc()
        System.runFinalization()
        return toc to bookWordCount
    }

    private fun analyzeWholeText(): Pair<ArrayList<BookChapter>, Int> {
        val bytes = LocalBook.getBookInputStream(book).use { it.readBytes() }
        if (bytes.isEmpty()) throw EmptyFileException("Unexpected Empty Txt File")
        val start = if (Utf8BomUtils.hasBom(bytes)) 3 else 0
        val wordCount = String(bytes, start, bytes.size - start, charset).length
        val chapter = BookChapter().apply {
            title = "正文"
            this.start = start.toLong()
            end = bytes.size.toLong()
            this.wordCount = StringUtils.wordCountFormat(wordCount)
        }
        return arrayListOf(chapter) to wordCount
    }

    /**
     * 获取合适的目录规则
     */
    private fun getTocRule(content: String): TxtTocRule? {
        val rules = getTocRules().reversed()
        var maxNum = 1
        var bestRule: TxtTocRule? = null
        for (tocRule in rules) {
            val pattern = try {
                Regex(tocRule.chapterRule, RegexOption.MULTILINE)
            } catch (e: PatternSyntaxException) {
                AppLog.put("TXT目录规则正则语法错误:${tocRule.name}\n$e", e)
                continue
            }
            var start = 0
            var num = 0
            for (m in pattern.findAll(content)) {
                if (start == 0 || m.range.first - start > 1000) {
                    num++
                    start = m.range.last + 1
                }
            }
            if (num >= maxNum) {
                maxNum = num
                bestRule = tocRule
            }
        }
        return bestRule
    }

    /**
     * 根据章节正则查找对应的分卷正则（搜索全部规则，含已禁用的）
     */
    private fun getVolumePattern(chapterPattern: String): Regex? {
        if (chapterPattern.isBlank()) return null
        val rule = appDb.txtTocRuleDao.all.find { it.chapterRule == chapterPattern }
        val volumeRule = rule?.volumeRule
        if (volumeRule.isNullOrBlank()) return null
        return try {
            Regex(volumeRule, RegexOption.MULTILINE)
        } catch (e: PatternSyntaxException) {
            AppLog.put("TXT分卷规则正则语法错误:${rule.name}\n$e", e)
            null
        }
    }

    /**
     * 获取启用的目录规则
     */
    private fun getTocRules(): List<TxtTocRule> {
        var rules = appDb.txtTocRuleDao.enabled
        if (appDb.txtTocRuleDao.count == 0) {
            rules = DefaultData.txtTocRules.apply {
                appDb.txtTocRuleDao.insert(*this.toTypedArray())
            }.filter {
                it.enable
            }
        }
        return rules
    }

}
