package app.musiku.ui.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable

val WindowInsets.Companion.stableStatusBars: WindowInsets
    @Composable get() = WindowInsets.statusBars

val WindowInsets.Companion.stableSystemBars: WindowInsets
    @Composable get() = WindowInsets.systemBars
