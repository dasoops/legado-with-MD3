package io.legado.app.help.webView

import android.webkit.JavascriptInterface
import androidx.appcompat.app.AppCompatActivity
import io.legado.app.data.entities.BaseSource
import io.legado.app.help.JsExtensions
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.showDialogFragment
import java.lang.ref.WeakReference

@Suppress("unused")
open class JsExtensionsBase(activity: AppCompatActivity?, source: BaseSource?) : JsExtensions {

    val activityRef: WeakReference<AppCompatActivity> = WeakReference(activity)
    val sourceRef: WeakReference<BaseSource?> = WeakReference(source)

    override fun getSource(): BaseSource? {
        return sourceRef.get()
    }

    override fun getTag(): String? {
        return getSource()?.getTag()
    }

    @JavascriptInterface
    fun put(key: String, value: String): String {
        getSource()?.put(key, value)
        return value
    }

    @JavascriptInterface
    fun get(key: String): String {
        return getSource()?.get(key) ?: ""
    }

    fun showPhoto(src: String) {
        activityRef.get()?.showDialogFragment(PhotoDialog(src, getSource()?.getKey()))
    }

    @JvmOverloads
    fun getStringList(rule: String?, mContent: Any? = null, isUrl: Boolean = false): List<String>? {
        return AnalyzeRule(null, source = getSource()).getStringList(rule, mContent, isUrl)
    }

    @JvmOverloads
    fun getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): String {
        return AnalyzeRule(null, source = getSource()).getString(ruleStr, mContent, isUrl)
    }

}
