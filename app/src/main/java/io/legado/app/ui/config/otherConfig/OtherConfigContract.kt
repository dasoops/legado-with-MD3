package io.legado.app.ui.config.otherConfig

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class OtherConfigUiState(
    val defaultBookTreeUri: String? = null,
    val language: String = "auto",
    val updateToVariant: String = "official_version",
    val autoCheckUpdateOnStart: Boolean = false,
    val autoRefresh: Boolean = false,
    val defaultToRead: Boolean = false,
    val antiAlias: Boolean = false,
    val replaceEnableDefault: Boolean = true,
    val autoClearExpired: Boolean = true,
    val showAddToShelfAlert: Boolean = true,
    val sourceEditMaxLine: Int = Int.MAX_VALUE,
    val processText: Boolean = true,
    val recordLog: Boolean = false,
    val recordHeapDump: Boolean = false,
    val activeOverlay: OtherConfigOverlay? = null,
    val pendingMessages: ImmutableList<OtherConfigMessage> = persistentListOf(),
)

@Stable
data class OtherConfigMessage(
    val id: Long,
    @StringRes val resId: Int?,
    val text: String?,
) {
    init {
        require((resId == null) != (text == null))
    }

    companion object {
        fun resource(id: Long, @StringRes resId: Int) =
            OtherConfigMessage(id = id, resId = resId, text = null)

        fun text(id: Long, text: String) =
            OtherConfigMessage(id = id, resId = null, text = text)
    }
}

sealed interface OtherConfigOverlay {
    data object FilePicker : OtherConfigOverlay
    data object Password : OtherConfigOverlay
}

sealed interface OtherConfigIntent {
    data class LanguageChanged(val value: String) : OtherConfigIntent
    data class UpdateToVariantChanged(val value: String) : OtherConfigIntent
    data class AutoCheckUpdateOnStartChanged(val value: Boolean) : OtherConfigIntent
    data class AutoRefreshChanged(val value: Boolean) : OtherConfigIntent
    data class DefaultToReadChanged(val value: Boolean) : OtherConfigIntent
    data class DefaultBookTreeUriChanged(val value: String?) : OtherConfigIntent
    data class AntiAliasChanged(val value: Boolean) : OtherConfigIntent
    data class ReplaceEnableDefaultChanged(val value: Boolean) : OtherConfigIntent
    data class AutoClearExpiredChanged(val value: Boolean) : OtherConfigIntent
    data class ShowAddToShelfAlertChanged(val value: Boolean) : OtherConfigIntent
    data class SourceEditMaxLineChanged(val value: Int) : OtherConfigIntent
    data class ProcessTextChanged(val value: Boolean) : OtherConfigIntent
    data class RecordLogChanged(val value: Boolean) : OtherConfigIntent
    data class RecordHeapDumpChanged(val value: Boolean) : OtherConfigIntent
    data class ShowOverlay(val overlay: OtherConfigOverlay) : OtherConfigIntent
    data object DismissOverlay : OtherConfigIntent
    data object RequestNotificationPermission : OtherConfigIntent
    data object RequestBatteryPermission : OtherConfigIntent
    data object RequestSystemDirectory : OtherConfigIntent
    data class SaveLocalPassword(val password: String) : OtherConfigIntent
    data class MessageShown(val id: Long) : OtherConfigIntent
}

sealed interface OtherConfigEffect {
    data object RequestNotificationPermission : OtherConfigEffect
    data object RequestBatteryPermission : OtherConfigEffect
    data object OpenSystemDirectory : OtherConfigEffect
}
