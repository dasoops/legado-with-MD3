package io.legado.app.ui.config.otherConfig

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.domain.gateway.AppLocaleGateway
import io.legado.app.domain.gateway.DownloadCacheSettingsGateway
import io.legado.app.domain.gateway.LocalPasswordGateway
import io.legado.app.domain.gateway.OtherConfigSystemGateway
import io.legado.app.domain.gateway.OtherSettingsGateway
import io.legado.app.domain.model.settings.OtherSettings
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OtherConfigViewModel(
    private val appLocaleGateway: AppLocaleGateway,
    private val otherSettingsGateway: OtherSettingsGateway,
    private val downloadCacheSettingsGateway: DownloadCacheSettingsGateway,
    private val localPasswordGateway: LocalPasswordGateway,
    private val systemGateway: OtherConfigSystemGateway,
    initialState: OtherConfigUiState = OtherConfigUiState(),
) : ViewModel() {

    private var clearWebViewDataJob: Job? = null
    private var restartRequested = false
    private var nextMessageId = 0L

    private val _uiState = MutableStateFlow(
        otherSettingsGateway.currentSettings.toUiState(initialState)
    )
    val uiState = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<OtherConfigEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            appLocaleGateway.language.collect { language ->
                _uiState.update { it.copy(language = language) }
            }
        }
        updateOtherSetting { it.copy(processText = systemGateway.isProcessTextEnabled()) }
        viewModelScope.launch {
            otherSettingsGateway.settings.collect { settings ->
                _uiState.update { settings.toUiState(it) }
            }
        }
    }

    fun onIntent(intent: OtherConfigIntent) {
        when (intent) {
            is OtherConfigIntent.LanguageChanged -> appLocaleGateway.setLanguage(intent.value)
            is OtherConfigIntent.UpdateToVariantChanged ->
                updateOtherSetting { it.copy(updateToVariant = intent.value) }
            is OtherConfigIntent.AutoCheckUpdateOnStartChanged ->
                updateOtherSetting { it.copy(autoCheckUpdateOnStart = intent.value) }
            is OtherConfigIntent.AutoRefreshChanged ->
                updateOtherSetting { it.copy(autoRefresh = intent.value) }
            is OtherConfigIntent.DefaultToReadChanged ->
                updateOtherSetting { it.copy(defaultToRead = intent.value) }
            is OtherConfigIntent.FirebaseEnableChanged ->
                updateOtherSetting { it.copy(firebaseEnable = intent.value) }
            is OtherConfigIntent.DefaultBookTreeUriChanged ->
                updateOtherSetting { it.copy(defaultBookTreeUri = intent.value) }
            is OtherConfigIntent.AntiAliasChanged ->
                updateOtherSetting { it.copy(antiAlias = intent.value) }
            is OtherConfigIntent.ReplaceEnableDefaultChanged ->
                updateOtherSetting { it.copy(replaceEnableDefault = intent.value) }
            is OtherConfigIntent.AutoClearExpiredChanged ->
                updateOtherSetting { it.copy(autoClearExpired = intent.value) }
            is OtherConfigIntent.ShowAddToShelfAlertChanged ->
                updateOtherSetting { it.copy(showAddToShelfAlert = intent.value) }
            is OtherConfigIntent.SourceEditMaxLineChanged ->
                updateOtherSetting { it.copy(sourceEditMaxLine = intent.value) }
            is OtherConfigIntent.ProcessTextChanged -> setProcessTextEnable(intent.value)
            is OtherConfigIntent.RecordLogChanged ->
                updateOtherSetting { it.copy(recordLog = intent.value) }
            is OtherConfigIntent.RecordHeapDumpChanged ->
                updateOtherSetting { it.copy(recordHeapDump = intent.value) }
            is OtherConfigIntent.ShowOverlay -> {
                _uiState.update { it.copy(activeOverlay = intent.overlay) }
            }
            OtherConfigIntent.DismissOverlay ->
                _uiState.update { it.copy(activeOverlay = null) }
            OtherConfigIntent.RequestNotificationPermission ->
                _effects.tryEmit(OtherConfigEffect.RequestNotificationPermission)
            OtherConfigIntent.RequestBatteryPermission ->
                _effects.tryEmit(OtherConfigEffect.RequestBatteryPermission)
            OtherConfigIntent.RequestSystemDirectory ->
                _effects.tryEmit(OtherConfigEffect.OpenSystemDirectory)
            OtherConfigIntent.ConfirmClearWebViewData -> {
                _uiState.update { it.copy(activeOverlay = null) }
                clearWebViewData()
            }
            is OtherConfigIntent.SaveLocalPassword -> saveLocalPassword(intent.password)
            is OtherConfigIntent.MessageShown -> {
                _uiState.update { state ->
                    state.copy(
                        pendingMessages = state.pendingMessages
                            .filterNot { it.id == intent.id }
                            .toImmutableList()
                    )
                }
            }
        }
    }

    private fun updateOtherSetting(
        onSuccess: () -> Unit = {},
        transform: (OtherSettings) -> OtherSettings,
    ) {
        viewModelScope.launch {
            runCatching { otherSettingsGateway.update(transform) }
                .onSuccess { onSuccess() }
                .onFailure { error ->
                    showMessage(error.message ?: error.javaClass.simpleName)
                }
        }
    }

    private fun setProcessTextEnable(enable: Boolean) {
        viewModelScope.launch {
            val previous = systemGateway.isProcessTextEnabled()
            runCatching {
                systemGateway.setProcessTextEnabled(enable)
                otherSettingsGateway.update { it.copy(processText = enable) }
            }.onFailure {
                if (previous != enable) {
                    runCatching { systemGateway.setProcessTextEnabled(previous) }
                }
                showMessage(it.localizedMessage ?: "设置失败")
            }
        }
    }

    private fun clearWebViewData() {
        if (clearWebViewDataJob?.isActive == true || restartRequested) return

        clearWebViewDataJob = viewModelScope.launch {
            runCatching { systemGateway.clearWebViewData() }
                .onSuccess {
                    restartRequested = true
                    showMessage(R.string.clear_webview_data_success)
                    _effects.tryEmit(OtherConfigEffect.RestartApp)
                }.onFailure {
                    AppLog.put("清除 WebView 数据失败", it)
                    showMessage(R.string.clear_webview_data_failed)
                }
        }
    }

    private fun saveLocalPassword(password: String) {
        viewModelScope.launch {
            runCatching { localPasswordGateway.setPassword(password) }
                .onSuccess { _uiState.update { it.copy(activeOverlay = null) } }
                .onFailure { showMessage(it.localizedMessage ?: "设置失败") }
        }
    }

    fun saveUserAgent(input: String) {
        viewModelScope.launch {
            runCatching {
                downloadCacheSettingsGateway.update { it.copy(userAgent = input) }
            }.onFailure { showMessage(it.localizedMessage ?: "设置失败") }
        }
    }

    fun updateLocalBookDir(path: String) {
        updateOtherSetting { it.copy(defaultBookTreeUri = path) }
    }

    private fun showMessage(@StringRes resId: Int) {
        _uiState.update {
            it.copy(
                pendingMessages = (
                    it.pendingMessages + OtherConfigMessage.resource(++nextMessageId, resId)
                ).toImmutableList()
            )
        }
    }

    private fun showMessage(message: String) {
        _uiState.update {
            it.copy(
                pendingMessages = (
                    it.pendingMessages + OtherConfigMessage.text(++nextMessageId, message)
                ).toImmutableList()
            )
        }
    }
}

private fun OtherSettings.toUiState(current: OtherConfigUiState): OtherConfigUiState =
    current.copy(
        updateToVariant = updateToVariant,
        autoCheckUpdateOnStart = autoCheckUpdateOnStart,
        autoRefresh = autoRefresh,
        defaultToRead = defaultToRead,
        firebaseEnable = firebaseEnable,
        defaultBookTreeUri = defaultBookTreeUri,
        antiAlias = antiAlias,
        replaceEnableDefault = replaceEnableDefault,
        autoClearExpired = autoClearExpired,
        showAddToShelfAlert = showAddToShelfAlert,
        sourceEditMaxLine = sourceEditMaxLine,
        processText = processText,
        recordLog = recordLog,
        recordHeapDump = recordHeapDump,
    )
