package io.legado.app.lib.webdav

open class WebDavException(
    msg: String,
) : Exception(msg) {
    override fun fillInStackTrace(): Throwable = this
}

class ObjectNotFoundException(
    msg: String,
) : WebDavException(msg)
