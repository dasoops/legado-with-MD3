package io.legado.app.help

import androidx.annotation.Keep
import androidx.collection.LruCache
import io.legado.app.data.appDb
import io.legado.app.data.entities.Cache
import io.legado.app.utils.ACache
import io.legado.app.utils.memorySize

/**
 * 最多只缓存50M的数据,防止OOM
 */
private val memoryLruCache =
    object : LruCache<String, Any>(1024 * 1024 * 50) {
        override fun sizeOf(
            key: String,
            value: Any,
        ): Int = value.toString().memorySize()
    }

@Keep
@Suppress("unused")
object CacheManager {
    /**
     * saveTime 单位为秒
     */
    @JvmOverloads
    fun put(
        key: String,
        value: Any,
        saveTime: Int = 0,
    ) {
        val deadline =
            if (saveTime == 0) 0 else System.currentTimeMillis() + saveTime * 1000
        when (value) {
            is ByteArray -> {
                ACache.get().put(key, value, saveTime)
            }
            else -> {
                val valueStr = value.toString()
                putMemory(key, valueStr)
                val cache = Cache(key, valueStr, deadline)
                appDb.cacheDao.insert(cache)
            }
        }
    }

    fun putMemory(
        key: String,
        value: Any,
    ) {
        memoryLruCache.put(key, value)
    }

    // 从内存中获取数据 使用lruCache
    fun getFromMemory(key: String): Any? = memoryLruCache[key]

    fun deleteMemory(key: String) {
        memoryLruCache.remove(key)
    }

    fun get(key: String): String? {
        getFromMemory(key)?.let {
            if (it is String) return it
        }
        val cache = appDb.cacheDao.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value?.also {
                putMemory(key, it)
            }
        }
        return null
    }

    fun get(
        key: String,
        onlyDisk: Boolean,
    ): String? {
        if (!onlyDisk) {
            return get(key)
        }
        val cache = appDb.cacheDao.get(key)
        if (cache != null && (cache.deadline == 0L || cache.deadline > System.currentTimeMillis())) {
            return cache.value
        }
        return null
    }

    fun getInt(key: String): Int? {
        getFromMemory(key)?.let {
            if (it is Int) return it
        }
        return get(key, true)?.toIntOrNull()
    }

    fun getLong(key: String): Long? {
        getFromMemory(key)?.let {
            if (it is Long) return it
        }
        return get(key, true)?.toLongOrNull()
    }

    fun getDouble(key: String): Double? {
        getFromMemory(key)?.let {
            if (it is Double) return it
        }
        return get(key, true)?.toDoubleOrNull()
    }

    fun getFloat(key: String): Float? {
        getFromMemory(key)?.let {
            if (it is Float) return it
        }
        return get(key, true)?.toFloatOrNull()
    }

    fun getByteArray(key: String): ByteArray? = ACache.get().getAsBinary(key)

    fun putFile(
        key: String,
        value: String,
        saveTime: Int = 0,
    ) {
        ACache.get().put(key, value, saveTime)
    }

    fun getFile(key: String): String? = ACache.get().getAsString(key)

    fun delete(key: String) {
        appDb.cacheDao.delete(key)
        deleteMemory(key)
        ACache.get().remove(key)
    }
}
