package io.legado.app.utils

import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

fun <I, O> AppCompatActivity.registerForActivityResult(contract: ActivityResultContract<I, O>): ActivityResultLauncherAwait<I, O> {
    lateinit var cout: CancellableContinuation<O>
    val launcher =
        registerForActivityResult(contract) {
            if (cout.isActive) {
                cout.resume(it)
            }
        }
    return object : ActivityResultLauncherAwait<I, O>() {
        override suspend fun launch(
            input: I,
            options: ActivityOptionsCompat?,
        ): O = suspendCancellableCoroutine {
            cout = it
            launcher.launch(input, options)
        }

        override fun unregister() {
            launcher.unregister()
        }

        override fun getContract(): ActivityResultContract<I, *> = launcher.contract
    }
}

abstract class ActivityResultLauncherAwait<I, O> {
    suspend fun launch(input: I): O = launch(input, null)

    abstract suspend fun launch(
        input: I,
        options: ActivityOptionsCompat?,
    ): O

    abstract fun unregister()

    abstract fun getContract(): ActivityResultContract<I, *>
}
