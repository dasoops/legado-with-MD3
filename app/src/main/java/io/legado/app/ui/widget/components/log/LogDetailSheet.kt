package io.legado.app.ui.widget.components.log

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.text.AppText

@Composable
fun LogDetailSheet(
    show: Boolean,
    title: String = "Log",
    content: String,
    onDismissRequest: () -> Unit,
) {
    AppAlertDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        content = {
            SelectionContainer {
                Column {
                    AppText(
                        text = content,
                        style = LegadoTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmText = stringResource(android.R.string.ok),
        onConfirm = onDismissRequest
    )
}
