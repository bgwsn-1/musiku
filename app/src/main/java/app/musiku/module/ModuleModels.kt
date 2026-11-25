package app.musiku.module

sealed interface AccountState {
    data object SignedOut : AccountState
    data object SignedIn : AccountState
    data class NeedsAttention(val message: String = "") : AccountState
}

enum class ModuleFeature { STREAMING, QUALITY_SETTINGS, DOWNLOADS }

data class ModuleInfo(
    val id: String,
    val name: String,
    val features: Set<ModuleFeature> = emptySet(),
)

interface MusicModule {
    val info: ModuleInfo
}
