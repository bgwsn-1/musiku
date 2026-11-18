/** Adapted from Flamingo by Yos-X, GPL-3.0. See NOTICE. */
package app.musiku.ui.lyrics.flamingo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable

@Composable
@NonRestartableComposable
internal fun YosWrapper(content: @Composable () -> Unit) = content()
