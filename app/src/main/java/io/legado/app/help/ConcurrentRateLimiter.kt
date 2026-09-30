package io.legado.app.help

import io.legado.app.model.analyzeRule.AnalyzeUrl.ConcurrentRecord
import java.util.concurrent.ConcurrentHashMap
/** 保留旧实体迁移期间解析并发配置所需的共享状态. 阅读链路不再创建限流器. */
class ConcurrentRateLimiter private constructor() {
    companion object {
        val concurrentRecordMap = ConcurrentHashMap<String, ConcurrentRecord>()

        /**
         * 更新并发率
         */
        fun updateConcurrentRate(
            key: String,
            concurrentRate: String,
        ) {
            concurrentRecordMap.compute(key) { _, record ->
                try {
                    val rateIndex = concurrentRate.indexOf("/")
                    when {
                        rateIndex > 0 -> {
                            val accessLimit = concurrentRate.take(rateIndex).toInt()
                            val interval = concurrentRate.substring(rateIndex + 1).toInt()
                            if (accessLimit <= 0 || interval <= 0) throw NumberFormatException()
                            ConcurrentRecord(
                                record?.time ?: System.currentTimeMillis(),
                                accessLimit,
                                interval,
                                record?.frequency ?: 0,
                            )
                        }
                        concurrentRate.toInt() > 0 -> {
                            ConcurrentRecord(
                                record?.time ?: System.currentTimeMillis(),
                                1,
                                concurrentRate.toInt(),
                                record?.frequency ?: 0,
                            )
                        }
                        else -> {
                            record
                        }
                    }
                } catch (_: NumberFormatException) {
                    record
                }
            }
        }
    }
}
