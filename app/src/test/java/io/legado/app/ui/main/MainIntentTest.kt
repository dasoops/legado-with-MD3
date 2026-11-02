package io.legado.app.ui.main

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class MainIntentTest {
    @Test
    fun `regular reader keeps existing activity and navigation semantics`() {
        val context: Application = RuntimeEnvironment.getApplication()

        val intent = MainIntent.createReadBookIntent(context)

        val mediaControlFlags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        assertEquals(0, intent.flags and mediaControlFlags)
        assertFalse(MainIntent.shouldOpenRouteWithHomeParent(intent))
    }

    @Test
    fun `main activity is single top so top reader intents reach onNewIntent`() {
        // 回归: 任务 base intent 与开书意图 filterEquals 时 (extras 不参与比较),
        // 标准 launchMode 会让系统走 recycleTask 的 START_DELIVERED_TO_TOP 分支,
        // 既不新建实例也不回调 onNewIntent, 点击表现为无效.
        val context: Application = RuntimeEnvironment.getApplication()
        val activityInfo =
            context.packageManager.getActivityInfo(
                ComponentName(context, MainActivity::class.java),
                0,
            )

        assertEquals(ActivityInfo.LAUNCH_SINGLE_TOP, activityInfo.launchMode)
    }
}
