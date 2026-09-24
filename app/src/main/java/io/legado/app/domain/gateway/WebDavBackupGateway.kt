package io.legado.app.domain.gateway

import io.legado.app.domain.model.WebDavBackup
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookProgress

interface WebDavBackupGateway {
    val isConfigured: Boolean
    val isJianGuoYun: Boolean

    suspend fun syncConfig()
    suspend fun test(): Boolean
    suspend fun backup()
    suspend fun getBackupNames(): List<String>
    suspend fun getLatestBackup(): WebDavBackup?
    suspend fun restore(name: String)
    suspend fun getBookProgress(book: Book): BookProgress?
    suspend fun uploadBookProgress(book: Book)
}
