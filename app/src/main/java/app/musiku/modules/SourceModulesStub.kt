package app.musiku.modules

import app.musiku.module.AccountState
import app.musiku.module.ModuleInfo
import app.musiku.module.MusicModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SourceEntry(
    val module: MusicModule,
    val enabled: Boolean = false,
    val account: AccountState = AccountState.SignedOut,
)

class SourceModules {
    companion object {
        const val YouTubeId = "youtube"
    }

    val order: StateFlow<List<String>> = MutableStateFlow(listOf(YouTubeId))
    val entries: StateFlow<List<SourceEntry>> = MutableStateFlow(emptyList())
    val youTubeEnabled: StateFlow<Boolean> = MutableStateFlow(true)
    val downloadOrder: StateFlow<List<String>> = MutableStateFlow(listOf(YouTubeId))

    fun refresh() = Unit
    fun setYouTubeEnabled(enabled: Boolean) = Unit
    fun setOrder(order: List<String>) = Unit
    fun setDownloadOrder(order: List<String>) = Unit
    fun setModuleEnabled(id: String, enabled: Boolean) = Unit
}

object DeviceDecoders {
    fun canPlay(format: String): Boolean = true
}
