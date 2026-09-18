package io.legado.app.domain.gateway

interface LocalPasswordGateway {
    suspend fun setPassword(password: String?)
}

interface OtherConfigSystemGateway {
    fun isProcessTextEnabled(): Boolean
    suspend fun setProcessTextEnabled(enabled: Boolean)
}
