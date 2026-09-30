package io.legado.app.help

import io.legado.app.constant.AppConst
import io.legado.app.data.appDb
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfigStore
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.printOnDebug
import java.io.File
import splitties.init.appCtx

object DefaultData {
    fun upVersion() {
        if (LocalConfig.versionCode < AppConst.appInfo.versionCode) {
            Coroutine
                .async {
                    if (LocalConfig.needUpTxtTocRule) {
                        importDefaultTocRules()
                    }
                }.onError {
                    it.printOnDebug()
                }
        }
    }

    val readConfigs: List<ReadBookConfig.Config> by lazy {
        val json =
            String(
                appCtx.assets
                    .open("defaultData${File.separator}${ReadBookConfig.CONFIG_FILE_NAME}")
                    .readBytes(),
            )
        GSON.fromJsonArray<ReadBookConfig.Config>(json).getOrNull()
            ?: emptyList()
    }

    val txtTocRules: List<TxtTocRule> by lazy {
        val json =
            String(
                appCtx.assets
                    .open("defaultData${File.separator}txtTocRule.json")
                    .readBytes(),
            )
        GSON.fromJsonArray<TxtTocRule>(json).getOrNull() ?: emptyList()
    }

    val themeConfigs: List<ThemeConfigStore.Config> by lazy {
        val json =
            String(
                appCtx.assets
                    .open("defaultData${File.separator}${ThemeConfigStore.CONFIG_FILE_NAME}")
                    .readBytes(),
            )
        GSON.fromJsonArray<ThemeConfigStore.Config>(json).getOrNull() ?: emptyList()
    }

    val keyboardAssists: List<KeyboardAssist> by lazy {
        val json =
            String(
                appCtx.assets
                    .open("defaultData${File.separator}keyboardAssists.json")
                    .readBytes(),
            )
        GSON.fromJsonArray<KeyboardAssist>(json).getOrThrow()
    }

    fun importDefaultTocRules() {
        appDb.txtTocRuleDao.deleteDefault()
        appDb.txtTocRuleDao.insert(*txtTocRules.toTypedArray())
    }
}
