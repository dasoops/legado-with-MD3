package io.legado.app.ui.config.coverConfig

import androidx.compose.runtime.Stable
import io.legado.app.domain.model.settings.CoverSettings

@Stable
data class CoverConfigUiState(
    val settings: CoverSettings = CoverSettings(),
    val albumSelection: CoverAlbumSelectionUiState = CoverAlbumSelectionUiState(),
    val activeSheet: CoverConfigSheet? = null,
)

sealed interface CoverConfigSheet {
    data object Album : CoverConfigSheet
    data class Color(val field: CoverColorField) : CoverConfigSheet
}

enum class CoverColorField {
    Text,
    Shadow,
    TextDark,
    ShadowDark,
}

sealed interface CoverConfigIntent {
    data class SetLoadOnlyOnWifi(val value: Boolean) : CoverConfigIntent
    data class SetUseDefaultCover(val value: Boolean) : CoverConfigIntent
    data class SetShowShadow(val value: Boolean) : CoverConfigIntent
    data class SetShowStroke(val value: Boolean) : CoverConfigIntent
    data class SetUseDefaultColor(val value: Boolean) : CoverConfigIntent
    data class SetInfoOrientation(val value: String) : CoverConfigIntent
    data class SetExploreFilterState(val value: Int) : CoverConfigIntent
    data class SetTextColor(val value: Int) : CoverConfigIntent
    data class SetShadowColor(val value: Int) : CoverConfigIntent
    data class SetTextColorDark(val value: Int) : CoverConfigIntent
    data class SetShadowColorDark(val value: Int) : CoverConfigIntent
    data class ShowSheet(val sheet: CoverConfigSheet) : CoverConfigIntent
    data object DismissSheet : CoverConfigIntent
    data class SelectAlbum(val id: String?) : CoverConfigIntent
}
