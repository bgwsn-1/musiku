package app.musiku.ui.screens.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.musiku.ui.component.PopupSheetHandle
import app.musiku.ui.component.PopupSheetState
import app.musiku.ui.component.SheetPillButton

fun PopupSheetState.showConfirm(
    title: String,
    message: String = "",
    action: String = "Delete",
    cancelLabel: String = "Cancel",
    onConfirm: () -> Unit,
) {
    lateinit var handle: PopupSheetHandle
    handle = showWithHandle {
        Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            SheetPillButton(text = cancelLabel, onClick = { handle.dismiss() }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            SheetPillButton(text = action, onClick = { onConfirm(); handle.dismiss() }, modifier = Modifier.weight(1f))
        }
    }
}
