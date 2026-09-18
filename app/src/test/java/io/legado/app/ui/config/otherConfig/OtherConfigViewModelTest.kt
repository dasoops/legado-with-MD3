package io.legado.app.ui.config.otherConfig

import android.app.Application
import android.os.Looper
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import io.legado.app.domain.model.settings.DownloadCacheSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class OtherConfigViewModelTest {

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun languageChanged_updatesLocaleGatewayAndUiState() = runBlocking {
        val otherSettingsGateway = FakeOtherSettingsGateway()
        val appLocaleGateway = FakeAppLocaleGateway()
        val viewModel = OtherConfigViewModel(
            appLocaleGateway = appLocaleGateway,
            otherSettingsGateway = otherSettingsGateway,
            downloadCacheSettingsGateway = FakeDownloadCacheSettingsGateway(),
            localPasswordGateway = FakeLocalPasswordGateway(),
            systemGateway = FakeOtherConfigSystemGateway(),
            initialState = OtherConfigUiState(),
        )

        viewModel.onIntent(OtherConfigIntent.LanguageChanged("en"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("en", appLocaleGateway.currentLanguage)
        assertEquals("en", viewModel.uiState.value.language)
    }

    @Test
    fun settingsFailures_areQueuedUntilUiAcknowledgesThem() = runBlocking {
        val otherSettingsGateway = FakeOtherSettingsGateway()
        val viewModel = createViewModel(otherSettingsGateway = otherSettingsGateway)
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        otherSettingsGateway.failure = IllegalStateException("boom")

        viewModel.onIntent(OtherConfigIntent.AutoRefreshChanged(true))
        viewModel.onIntent(OtherConfigIntent.DefaultToReadChanged(true))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val messages = viewModel.uiState.value.pendingMessages
        assertEquals(2, messages.size)
        val message = messages.first()
        assertEquals("boom", message.text)

        viewModel.onIntent(OtherConfigIntent.MessageShown(message.id))

        assertEquals(1, viewModel.uiState.value.pendingMessages.size)
    }

    @Test
    fun localPassword_writesThroughGateway() = runBlocking {
        val localPasswordGateway = FakeLocalPasswordGateway()
        val viewModel = createViewModel(
            localPasswordGateway = localPasswordGateway,
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        viewModel.onIntent(OtherConfigIntent.SaveLocalPassword("secret"))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals("secret", localPasswordGateway.savedPassword)
    }

    @Test
    fun processTextSettingFailure_rollsBackSystemComponent() = runBlocking {
        val otherSettingsGateway = FakeOtherSettingsGateway()
        val systemGateway = FakeOtherConfigSystemGateway()
        val viewModel = createViewModel(
            otherSettingsGateway = otherSettingsGateway,
            systemGateway = systemGateway,
        )
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        otherSettingsGateway.failure = IllegalStateException("boom")

        viewModel.onIntent(OtherConfigIntent.ProcessTextChanged(false))
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertTrue(systemGateway.enabled)
    }

    private fun createViewModel(
        otherSettingsGateway: FakeOtherSettingsGateway = FakeOtherSettingsGateway(),
        localPasswordGateway: FakeLocalPasswordGateway = FakeLocalPasswordGateway(),
        systemGateway: FakeOtherConfigSystemGateway = FakeOtherConfigSystemGateway(),
    ) = OtherConfigViewModel(
        appLocaleGateway = FakeAppLocaleGateway(),
        otherSettingsGateway = otherSettingsGateway,
        downloadCacheSettingsGateway = FakeDownloadCacheSettingsGateway(),
        localPasswordGateway = localPasswordGateway,
        systemGateway = systemGateway,
        initialState = OtherConfigUiState(),
    )

    private class FakeAppLocaleGateway : AppLocaleGateway {
        private val state = MutableStateFlow("auto")
        override val currentLanguage: String
            get() = state.value
        override val language = state.asStateFlow()

        override fun setLanguage(language: String) {
            state.value = language
        }

        override fun synchronizeFromPlatform() = Unit

        override fun migrateLegacyLanguage(language: String) {
            setLanguage(language)
        }
    }

    private class FakeOtherSettingsGateway : OtherSettingsGateway {
        private val state = MutableStateFlow(OtherSettings())
        var failure: Throwable? = null

        override val currentSettings: OtherSettings
            get() = state.value
        override val settings: Flow<OtherSettings> = state

        override suspend fun update(transform: (OtherSettings) -> OtherSettings) {
            failure?.let { throw it }
            state.value = transform(state.value)
        }
    }


    private class FakeDownloadCacheSettingsGateway : DownloadCacheSettingsGateway {
        private val state = MutableStateFlow(DownloadCacheSettings())

        override val currentSettings: DownloadCacheSettings
            get() = state.value
        override val settings: Flow<DownloadCacheSettings> = state

        override suspend fun update(
            transform: (DownloadCacheSettings) -> DownloadCacheSettings,
        ) {
            state.value = transform(state.value)
        }
    }

    private class FakeLocalPasswordGateway : LocalPasswordGateway {
        var savedPassword: String? = null

        override suspend fun setPassword(password: String?) {
            savedPassword = password
        }
    }

    private class FakeOtherConfigSystemGateway : OtherConfigSystemGateway {
        var enabled = true

        override fun isProcessTextEnabled(): Boolean = enabled
        override suspend fun setProcessTextEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }
}
