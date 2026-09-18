package io.legado.app.model

import android.annotation.SuppressLint
import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.utils.HtmlFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Debug {

    @SuppressLint("ConstantLocale")
    private val debugTimeFormat = SimpleDateFormat("[mm:ss.SSS]", Locale.getDefault())

    @Synchronized
    fun log(
        sourceUrl: String?,
        msg: String = "",
        print: Boolean = true,
        isHtml: Boolean = false,
        showTime: Boolean = true,
        state: Int = 1
    ) {
        if (BuildConfig.DEBUG) {
            Log.d("sourceDebug", if (isHtml) HtmlFormatter.format(msg) else msg)
        }
    }

    @Synchronized
    fun log(msg: String?) {
        log(null, if (msg == null) "" else msg, true)
    }

}
