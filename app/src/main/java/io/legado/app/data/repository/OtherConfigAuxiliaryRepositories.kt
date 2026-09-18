package io.legado.app.data.repository

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.legado.app.data.local.preferences.LocalPreferencesKeys
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.help.config.AppConfigStore
import io.legado.app.help.webView.WebViewDataCleaner
import io.legado.app.receiver.SharedReceiverActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalPasswordRepository : LocalPasswordGateway {

    override suspend fun setPassword(password: String?) {
        AppConfigStore.putAllAndAwait(
            mapOf(LocalPreferencesKeys.PASSWORD.name to password.orEmpty())
        )
    }
}

class OtherConfigSystemRepository(
    private val context: Context,
) : OtherConfigSystemGateway {

    private val componentName = ComponentName(
        context,
        SharedReceiverActivity::class.java.name,
    )

    override fun isProcessTextEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(componentName) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    override suspend fun setProcessTextEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        context.packageManager.setComponentEnabledSetting(
            componentName,
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }

    override suspend fun clearWebViewData() {
        WebViewDataCleaner.clear(context)
    }
}
