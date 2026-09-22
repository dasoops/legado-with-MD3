package io.legado.app.feature.booktags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.domain.model.BookTags
import io.legado.app.ui.widget.components.AppTextField
import io.legado.app.ui.widget.components.button.series.MediumTonalButton
import io.legado.app.ui.widget.components.card.SelectionItemCard
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import kotlinx.collections.immutable.ImmutableList

@Composable
fun TagSelectSheet(
    show: Boolean,
    tags: ImmutableList<String>,
    onDismissRequest: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    if (!show) return
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var newTags by remember { mutableStateOf("") }
    AppModalBottomSheet(show = true, onDismissRequest = onDismissRequest, title = stringResource(R.string.add_book_tags)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = newTags,
                onValueChange = { newTags = it },
                label = stringResource(R.string.new_book_tags),
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tags, key = { it }) { tag ->
                    SelectionItemCard(
                        title = tag,
                        isSelected = tag in selected,
                        inSelectionMode = true,
                        onToggleSelection = { selected = if (tag in selected) selected - tag else selected + tag }
                    )
                }
            }
            MediumTonalButton(
                text = stringResource(R.string.add_book_tags),
                enabled = selected.isNotEmpty() || BookTags.editable(listOf(newTags)).isNotEmpty(),
                onClick = { onConfirm(selected + BookTags.editable(listOf(newTags))) }
            )
        }
    }
}
