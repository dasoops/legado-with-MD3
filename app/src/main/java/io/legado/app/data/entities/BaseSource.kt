package io.legado.app.data.entities

import android.webkit.JavascriptInterface
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.help.CacheManager
import io.legado.app.help.ConcurrentRateLimiter.Companion.updateConcurrentRate
import io.legado.app.help.crypto.SymmetricCryptoAndroid
import io.legado.app.help.http.CookieStore
import io.legado.app.utils.GSON
import io.legado.app.utils.GSONStrict
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.has

/**
 * 可在js里调用,source.xxx()
 */
@Suppress("unused")
interface BaseSource {
    /**
     * 并发率
     */
    var concurrentRate: String?

    /**
     * 登录地址
     */
    var loginUrl: String?

    /**
     * 登录UI
     */
    var loginUi: String?

    /**
     * 请求头
     */
    var header: String?

    /**
     * 启用cookieJar
     */
    var enabledCookieJar: Boolean?

    /**
     * js库
     */
    var jsLib: String?

    fun getTag(): String

    fun getKey(): String

    fun getSource(): BaseSource? {
        return this
    }

    /**
     * 解析header规则
     */
    fun getHeaderMap(userAgent: String, hasLoginHeader: Boolean = false) = HashMap<String, String>().apply {
        header?.let {
            try {
                GSONStrict.fromJsonObject<Map<String, String>>(it).getOrNull()?.let { map ->
                    putAll(map)
                } ?: GSON.fromJsonObject<Map<String, String>>(it).getOrNull()?.let { map ->
                    AppLog.putDebug("请求头规则 JSON 格式不规范，请改为规范格式")
                    putAll(map)
                }
            } catch (e: Exception) {
                AppLog.put("解析请求头规则出错\n$e", e)
            }
        }
        if (!has(AppConst.UA_NAME, true)) {
            put(AppConst.UA_NAME, userAgent)
        }
        if (hasLoginHeader) {
            getLoginHeaderMap()?.let {
                putAll(it)
            }
        }
    }

    /**
     * 获取用于登录的头部信息
     */
    @JavascriptInterface
    fun getLoginHeader(): String? {
        return CacheManager.get("loginHeader_${getKey()}")
    }

    fun getLoginHeaderMap(): Map<String, String>? {
        val cache = getLoginHeader() ?: return null
        return GSON.fromJsonObject<Map<String, String>>(cache).getOrNull()
    }

    /**
     * 保存登录头部信息,map格式,访问时自动添加
     */
    fun putLoginHeader(header: String) {
        val headerMap = GSON.fromJsonObject<Map<String, String>>(header).getOrNull()
        val cookie = headerMap?.get("Cookie") ?: headerMap?.get("cookie")
        cookie?.let {
            CookieStore.replaceCookie(getKey(), it)
        }
        CacheManager.put("loginHeader_${getKey()}", header)
    }

    fun removeLoginHeader() {
        CacheManager.delete("loginHeader_${getKey()}")
        CookieStore.removeCookie(getKey())
    }

    /**
     * 获取用户信息,可以用来登录
     * 用户信息采用aes加密存储
     */
    @JavascriptInterface
    fun getLoginInfo(): String? {
        try {
            val key = AppConst.androidId.encodeToByteArray(0, 16)
            val cache = CacheManager.get("userInfo_${getKey()}") ?: return null
            return SymmetricCryptoAndroid("AES", key).decryptStr(cache)
        } catch (e: Exception) {
            AppLog.put("获取登陆信息出错", e)
            return null
        }
    }

    /**
     * 保存用户信息,aes加密
     */
    @JavascriptInterface
    fun putLoginInfo(info: String): Boolean {
        return try {
            val key = (AppConst.androidId).encodeToByteArray(0, 16)
            val encodeStr = SymmetricCryptoAndroid("AES", key).encryptBase64(info)
            CacheManager.put("userInfo_${getKey()}", encodeStr)
            true
        } catch (e: Exception) {
            AppLog.put("保存登陆信息出错", e)
            false
        }
    }

    @JavascriptInterface
    fun removeLoginInfo() {
        CacheManager.delete("userInfo_${getKey()}")
    }

    /**
     * 设置自定义变量
     * @param variable 变量内容
     */
    fun setVariable(variable: String?) {
        if (variable != null) {
            CacheManager.put("sourceVariable_${getKey()}", variable)
        } else {
            CacheManager.delete("sourceVariable_${getKey()}")
        }
    }

    /**
     * 设置自定义变量
     * 新,统一为put名称存变量
     */
    @JavascriptInterface
    fun putVariable(variable: String?) {
        if (variable != null) {
            CacheManager.put("sourceVariable_${getKey()}", variable)
        } else {
            CacheManager.delete("sourceVariable_${getKey()}")
        }
    }

    /**
     * 获取自定义变量
     */
    @JavascriptInterface
    fun getVariable(): String {
        getTemporaryVariable()?.let {
            return it
        }
        return CacheManager.get("sourceVariable_${getKey()}") ?: ""
    }

    fun setTemporaryVariable(variable: String?) {
    }

    fun getTemporaryVariable(): String? {
        return null
    }

    /**
     * 保存数据
     */
    @JavascriptInterface
    fun put(key: String, value: String): String {
        CacheManager.put("v_${getKey()}_${key}", value)
        return value
    }

    /**
     * 获取保存的数据
     */
    @JavascriptInterface
    fun get(key: String): String {
        return CacheManager.get("v_${getKey()}_${key}") ?: ""
    }

    /**
     * 设置并发率
     */
    fun putConcurrent(value: String) {
        updateConcurrentRate(getKey(), value)
    }
}
