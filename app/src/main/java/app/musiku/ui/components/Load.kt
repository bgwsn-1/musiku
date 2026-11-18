package app.musiku.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val error: Throwable) : Load<Nothing>
}

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): State<Load<T>> =
    produceState<Load<T>>(Load.Loading, key) {
        value = Load.Loading
        value = runCatching { block() }.fold({ Load.Ok(it) }, { Load.Err(it) })
    }
