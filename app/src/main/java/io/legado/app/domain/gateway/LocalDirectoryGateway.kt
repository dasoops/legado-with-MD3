package io.legado.app.domain.gateway

import io.legado.app.domain.model.LocalBookProgress
import io.legado.app.domain.model.LocalDirectoryEntry
import kotlinx.coroutines.flow.Flow

interface LocalDirectoryGateway {

    suspend fun rootDocument(treeUri: String): LocalDirectoryEntry?

    suspend fun listChildren(dirUri: String): List<LocalDirectoryEntry>

    fun flowLocalBookProgress(): Flow<Map<String, LocalBookProgress>>
}
