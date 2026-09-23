package io.legado.app.domain.model.settings

data class CoverSettings(
    val loadOnlyOnWifi: Boolean = false,
    val useDefaultCover: Boolean = false,
    val showShadow: Boolean = false,
    val showStroke: Boolean = true,
    val useDefaultColor: Boolean = true,
    val textColor: Int = -16777216,
    val shadowColor: Int = -16777216,
    val textColorDark: Int = -1,
    val shadowColorDark: Int = -1,
    val infoOrientation: String = "0",
    val exploreFilterState: Int = 0,
    val defaultCover: String = "",
    val defaultCoverDark: String = "",
)
