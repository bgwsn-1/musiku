package app.musiku.ui.screens.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.musiku.ui.component.SheetPillButton

@Composable
fun SheetPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SheetPillButton(text = text, onClick = onClick, modifier = modifier, enabled = enabled)
}
