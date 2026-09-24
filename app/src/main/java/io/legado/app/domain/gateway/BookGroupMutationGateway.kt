package io.legado.app.domain.gateway

import io.legado.app.domain.model.BookGroupUpdate
import io.legado.app.domain.model.NewBookGroup

interface BookGroupMutationGateway {

    suspend fun addGroup(group: NewBookGroup)

    suspend fun saveGroup(bookGroup: BookGroupUpdate)

    suspend fun deleteGroup(groupId: Long)
}
