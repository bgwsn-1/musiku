package app.musiku.ui

import androidx.compose.runtime.compositionLocalOf
import app.musiku.MusikuApp

val LocalApp = compositionLocalOf<MusikuApp> { error("MusikuApp not provided") }
