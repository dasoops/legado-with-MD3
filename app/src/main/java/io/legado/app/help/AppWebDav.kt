package io.legado.app.help

import io.legado.app.R
import io.legado.app.exception.NoStackTraceException
import io.legado.app.domain.gateway.BackupSettingsGateway
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.storage.Backup
import io.legado.app.help.storage.BackupRestoreLock
import io.legado.app.help.storage.Restore
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavException
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.compress.ZipUtils
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import splitties.init.appCtx
import org.koin.core.context.GlobalContext

/**
 * webDav初始化会访问网络,不要放到主线程
 */
object AppWebDav {

    private const val MAX_BACKUP_COUNT = 10
    private val backupGateway by lazy { GlobalContext.get().get<BackupSettingsGateway>() }

    private const val defaultWebDavUrl = "https://dav.jianguoyun.com/dav/"

    private val configMutex = Mutex()
    private var appliedConfig: AppliedWebDavConfig? = null

    @Volatile
    var authorization: Authorization? = null
        private set

    val isOk get() = authorization != null

    val isJianGuoYun get() = rootWebDavUrl.startsWith(defaultWebDavUrl, true)

    private val rootWebDavUrl: String
        get() {
            val configUrl = backupGateway.currentSettings.webDavUrl.trim()
            var url = if (configUrl.isEmpty()) defaultWebDavUrl else configUrl
            if (!url.endsWith("/")) url = "${url}/"
            backupGateway.currentSettings.webDavDir.trim().let {
                if (it.isNotEmpty()) {
                    url = "${url}${it}/"
                }
            }
            return url
        }

    suspend fun upConfig() {
        configMutex.withLock {
            val config = AppliedWebDavConfig(
                url = backupGateway.currentSettings.webDavUrl,
                account = backupGateway.currentSettings.webDavAccount,
                password = backupGateway.currentSettings.webDavPassword,
                dir = backupGateway.currentSettings.webDavDir,
            )
            if (appliedConfig == config) return

            kotlin.runCatching {
                authorization = null
                if (config.account.isNotEmpty() && config.password.isNotEmpty()) {
                    val mAuthorization = Authorization(config.account, config.password)
                    checkAuthorization(mAuthorization)
                    WebDav(rootWebDavUrl, mAuthorization).makeAsDir()
                    authorization = mAuthorization
                }
                appliedConfig = config
            }
        }
    }

    private data class AppliedWebDavConfig(
        val url: String,
        val account: String,
        val password: String,
        val dir: String,
    )

    @Throws(WebDavException::class)
    private suspend fun checkAuthorization(authorization: Authorization) {
        if (!WebDav(rootWebDavUrl, authorization).check()) {
            //appCtx.removePref(PreferKey.webDavPassword)
            appCtx.toastOnUi(R.string.webdav_application_authorization_error)
            throw WebDavException(appCtx.getString(R.string.webdav_application_authorization_error))
        }
    }

    @Throws(Exception::class)
    suspend fun getBackupNames(): ArrayList<String> {
        val names = arrayListOf<String>()
        authorization?.let {
            var files = WebDav(rootWebDavUrl, it).listFiles()
            files = files.sortedWith { o1, o2 ->
                AlphanumComparator.compare(o1.displayName, o2.displayName)
            }.reversed()
            files.forEach { webDav ->
                val name = webDav.displayName
                if (name.startsWith("backup")) {
                    names.add(name)
                }
            }
        } ?: throw NoStackTraceException("webDav没有配置")
        return names
    }

    @Throws(WebDavException::class)
    suspend fun restoreWebDav(name: String) {
        authorization?.let {
            val webDav = WebDav(rootWebDavUrl + name, it)
            BackupRestoreLock.withLock {
                webDav.downloadTo(Backup.zipFilePath, true)
                FileUtils.delete(Backup.backupPath)
                ZipUtils.unZipToPath(File(Backup.zipFilePath), Backup.backupPath)
                Restore.restoreUnzipped(Backup.backupPath)
                LocalConfig.lastBackup = System.currentTimeMillis()
            }
        }
    }

    suspend fun hasBackUp(backUpName: String): Boolean {
        authorization?.let {
            val url = "$rootWebDavUrl${backUpName}"
            return WebDav(url, it).exists()
        }
        return false
    }

    suspend fun lastBackUp(): Result<WebDavFile?> {
        return kotlin.runCatching {
            authorization?.let {
                var lastBackupFile: WebDavFile? = null
                WebDav(rootWebDavUrl, it).listFiles().reversed().forEach { webDavFile ->
                    if (webDavFile.displayName.startsWith("backup")) {
                        if (lastBackupFile == null
                            || webDavFile.lastModify > lastBackupFile.lastModify
                        ) {
                            lastBackupFile = webDavFile
                        }
                    }
                }
                lastBackupFile
            }
        }
    }

    suspend fun testWebDav(): Boolean {
        return kotlin.runCatching {
            val account = backupGateway.currentSettings.webDavAccount
            val password = backupGateway.currentSettings.webDavPassword
            if (account.isNullOrEmpty() || password.isNullOrEmpty()) {
                appCtx.toastOnUi("账号或密码为空")
                return false
            }

            val auth = Authorization(account, password)
            checkAuthorization(auth)

            appCtx.toastOnUi("WebDAV 服务可用")
            true
        }.getOrElse {
            it.printStackTrace()
            if (it !is WebDavException) {
                appCtx.toastOnUi(it.message ?: "未知错误")
            }
            false
        }
    }



    /**
     * webDav备份
     * @param fileName 备份文件名
     */
    @Throws(Exception::class)
    suspend fun backUpWebDav(fileName: String) {
        if (!NetworkUtils.isAvailable()) return
        authorization?.let {
            val putUrl = "$rootWebDavUrl$fileName"
            WebDav(putUrl, it).upload(Backup.zipFilePath)
            pruneBackups(it)
        }
    }

    private suspend fun pruneBackups(authorization: Authorization) {
        val backups = WebDav(rootWebDavUrl, authorization)
            .listFiles()
            .filter { !it.isDir && it.displayName.matches(Regex("backup.*\\.zip")) }
            .sortedBy { it.lastModify }
        backups.take((backups.size - MAX_BACKUP_COUNT).coerceAtLeast(0)).forEach {
            it.delete()
        }
    }

}
