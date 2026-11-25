package app.musiku.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.musiku.constants.audioLabDataStore
import app.musiku.constants.toEnum
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(key) {
        context.audioLabDataStore.data.map { it[key] ?: defaultValue }
    }.collectAsState(initial = defaultValue)

    return remember(key, state) {
        object : MutableState<T> {
            override var value: T
                get() = state
                set(newValue) {
                    scope.launch {
                        context.audioLabDataStore.edit { it[key] = newValue }
                    }
                }
            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by remember(key) {
        context.audioLabDataStore.data.map { it[key].toEnum(defaultValue) }
    }.collectAsState(initial = defaultValue)

    return remember(key, state) {
        object : MutableState<T> {
            override var value: T
                get() = state
                set(newValue) {
                    scope.launch {
                        context.audioLabDataStore.edit { it[key] = newValue.name }
                    }
                }
            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
