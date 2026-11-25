package app.musiku.ui.screens.audiolab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import app.musiku.MusikuApp
import app.musiku.modules.SourceModules
import app.musiku.viewmodels.AudioLabViewModel
import app.musiku.viewmodels.MixerViewModel

@Composable
fun rememberAudioLabViewModel(): AudioLabViewModel {
    val app = LocalContext.current.applicationContext as MusikuApp
    val sources = remember(app) { SourceModules() }
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AudioLabViewModel(app.audioLabStore, app.autoEqRepository, app.audioFx, sources) as T
        },
    )
}

@Composable
fun rememberMixerViewModel(): MixerViewModel {
    val app = LocalContext.current.applicationContext as MusikuApp
    return viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                MixerViewModel(app.audioLabStore.mixer, app) as T
        },
    )
}

@Composable
fun rememberModulesViewModel(): app.musiku.viewmodels.ModulesViewModel {
    val sources = remember { SourceModules() }
    return remember(sources) { app.musiku.viewmodels.ModulesViewModel(sources) }
}
