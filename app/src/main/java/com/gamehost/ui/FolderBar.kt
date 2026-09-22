package com.gamehost.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gamehost.R

/**
 * Breadcrumb navigation across the campaign tree (§7).
 *
 * Every crumb is tappable, so any ancestor — and the campaign root in particular — is
 * one tap away from any depth. Horizontally scrollable, so a deep tree stays navigable
 * without eating vertical space that the browser and the preview need more.
 */
@Composable
fun FolderBar(
    stack: List<FolderCrumb>,
    onJumpTo: (Int) -> Unit,
    onChangeFolder: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            stack.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val isCurrent = index == stack.lastIndex
                TextButton(
                    onClick = { onJumpTo(index) },
                    enabled = !isCurrent,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        // The root's label is a resource, not a stored name, so no
                        // French leaks into the model (§21.1).
                        text = crumb.name ?: stringResource(R.string.browser_root),
                        maxLines = 1,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }

        TextButton(onClick = onRefresh, modifier = Modifier.padding(start = 4.dp)) {
            Text(stringResource(R.string.browser_refresh))
        }
        TextButton(onClick = onChangeFolder) {
            Text(stringResource(R.string.browser_change_folder))
        }
    }
}
