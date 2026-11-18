package app.musiku.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(
    source: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = combinedClickable(
    interactionSource = source,
    indication = null,
    role = Role.Button,
    onClick = onClick,
    onLongClick = onLongClick,
)
