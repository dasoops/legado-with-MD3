@file:Suppress("unused")

package io.legado.app.ui.widget.code

import android.content.Context
import android.widget.ArrayAdapter
import io.legado.app.R
import splitties.init.appCtx
import splitties.resources.color
import java.util.regex.Pattern

val legadoPattern: Pattern = Pattern.compile("\\|\\||&&|%%|@Json:|@css:|@@|@XPath:")
val jsonPattern: Pattern = Pattern.compile("\"[A-Za-z0-9]*?\"\\:|\"|\\{|\\}|\\[|\\]")

fun CodeView.addLegadoPattern() {
    addSyntaxPattern(legadoPattern, appCtx.color(R.color.md_orange_900))
}

fun CodeView.addJsonPattern() {
    addSyntaxPattern(jsonPattern, appCtx.color(R.color.md_blue_800))
}

fun Context.arrayAdapter(keywords: Array<String>): ArrayAdapter<String> {
    return ArrayAdapter(this, R.layout.item_1line_text_and_del, R.id.text_view, keywords)
}