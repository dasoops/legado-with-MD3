package io.legado.app.utils

import androidx.core.os.postDelayed
import io.legado.app.exception.RegexTimeoutException
import io.legado.app.help.CrashHandler
import io.legado.app.help.coroutine.Coroutine
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import splitties.init.appCtx
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val handler by lazy { buildMainHandler() }

/**
 * 带有超时检测的正则替换
 */
fun CharSequence.replace(
    regex: Regex,
    replacement: String,
    timeout: Long
): String {
    val charSequence = this@replace
    // 规则原本是 JS 替换的已不再支持，保持原文避免把 JS 源码当作替换文本插入
    if (replacement.startsWith("@js:") || replacement.startsWith("<js>")) {
        return charSequence.toString()
    }
    return runBlocking {
        suspendCancellableCoroutine { block ->
            val coroutine = Coroutine.async(executeContext = IO) {
                try {
                    val pattern = regex.toPattern()
                    val matcher = pattern.matcher(charSequence)
                    val stringBuffer = StringBuffer()
                    while (matcher.find()) {
                        matcher.appendReplacement(stringBuffer, replacement)
                    }
                    matcher.appendTail(stringBuffer)
                    block.resume(stringBuffer.toString())
                } catch (e: Exception) {
                    block.resumeWithException(e)
                }
            }
            val timeoutRunnable = handler.postDelayed(timeout) {
                if (coroutine.isActive) {
                    val timeoutMsg =
                        "替换超时,3秒后还未结束将重启应用\n替换规则$regex\n替换内容:$charSequence"
                    val exception = RegexTimeoutException(timeoutMsg)
                    block.cancel(exception)
                    appCtx.longToastOnUi(timeoutMsg)
                    CrashHandler.saveCrashInfo2File(exception)
                    handler.postDelayed(3000) {
                        if (coroutine.isActive) {
                            appCtx.restart()
                        }
                    }
                }
            }
            // 正则结束后立即撤掉看门狗,否则它会在主线程消息队列里存活整个 timeout 窗口,
            // 并钉住整段正文拷贝;规则数量多时会在 3 秒内堆积成百上千份拷贝导致 OOM。
            coroutine.invokeOnCompletion {
                handler.removeCallbacks(timeoutRunnable)
            }
        }
    }
}
