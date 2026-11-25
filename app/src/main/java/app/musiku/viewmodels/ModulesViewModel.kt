package app.musiku.viewmodels

import app.musiku.modules.SourceModules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ModulesViewModel(val sources: SourceModules) {
    val entries = sources.entries
    val order = sources.order
    val downloadOrder = sources.downloadOrder
    val youTubeEnabled = sources.youTubeEnabled
    val busy: StateFlow<Boolean> = MutableStateFlow(false)

    fun refresh() = sources.refresh()
    fun setYouTubeEnabled(enabled: Boolean) = sources.setYouTubeEnabled(enabled)
    fun setOrder(order: List<String>) = sources.setOrder(order)
    fun setDownloadOrder(order: List<String>) = sources.setDownloadOrder(order)
    fun setEnabled(id: String, enabled: Boolean) = sources.setModuleEnabled(id, enabled)
}
