package io.legado.app.utils

import io.legado.app.help.coroutine.Coroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class TimeoutCancellationException(
    msg: String,
) : CancellationException(msg)

suspend fun <T> withTimeoutAsync(
    delayMillis: Long,
    block: suspend CoroutineScope.() -> T,
): T = suspendCancellableCoroutine { cout ->
    Coroutine.async(context = cout.context) {
        launch {
            delay(delayMillis)
            if (!cout.isCompleted) {
                cout.resumeWithException(TimeoutCancellationException("Timed out waiting for $delayMillis ms"))
            }
        }
        val result = block()
        if (!cout.isCompleted) {
            cout.resume(result)
        }
    }
}

suspend fun <T> withTimeoutOrNullAsync(
    delayMillis: Long,
    block: suspend CoroutineScope.() -> T,
): T? = try {
    withTimeoutAsync(delayMillis, block)
} catch (e: TimeoutCancellationException) {
    null
}
