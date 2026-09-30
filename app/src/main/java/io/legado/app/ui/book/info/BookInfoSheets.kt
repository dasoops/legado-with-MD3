package io.legado.app.ui.book.info

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.image.cover.CoilBookCover
import io.legado.app.ui.widget.components.modalBottomSheet.AppModalBottomSheet
import io.legado.app.ui.widget.components.text.AppText

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChangeCoverSheet(
    show: Boolean,
    name: String,
    author: String,
    onDismissRequest: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AppModalBottomSheet(
        show = show,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.change_cover_source),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onSelect("use_default_cover") }
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CoilBookCover(
                        name = name,
                        author = author,
                        path = "use_default_cover",
                        modifier = Modifier
                            .width(112.dp)
                            .aspectRatio(5f / 7f),
                    )
                    AppText(
                        text = stringResource(R.string.default_cover),
                        style = LegadoTheme.typography.labelSmallEmphasized,
                        maxLines = 2,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
