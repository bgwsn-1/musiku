package app.musiku.ui.nowplaying

/**
 * Convx's `PlayerSlot` (`ui/player/PlayerLayoutRegistry.kt`): the five bands the player stacks.
 * The stored order decides which is drawn first, and [hideable] decides whether the layout builder
 * offers a switch for it.
 */
enum class PlayerSlot(val label: String, val hideable: Boolean) {
    AlbumArt("Sampul", true),
    TrackInfo("Judul lagu", true),
    SeekBar("Seek bar", true),
    Controls("Kontrol", false),
    ActionRow("Tombol aksi", true),
    ;

    companion object {
        val DefaultOrder: List<PlayerSlot> = entries.toList()

        /** Parses a stored comma list; unknown names are dropped and missing slots appended. */
        fun orderFrom(raw: String?): List<PlayerSlot> = normalize(
            raw?.split(',')?.mapNotNull { name ->
                entries.firstOrNull { it.name == name.trim() }
            }.orEmpty(),
        )

        /** Parses a stored comma list of hidden slots; only [hideable] ones survive. */
        fun hiddenFrom(raw: String?): Set<PlayerSlot> =
            raw?.split(',')?.mapNotNull { name ->
                entries.firstOrNull { it.name == name.trim() }
            }?.filter { it.hideable }?.toSet().orEmpty()

        /** Convx's `PlayerCustomization.normalized()`: dedupe, append what is missing, never hide Controls. */
        fun normalize(order: List<PlayerSlot>): List<PlayerSlot> =
            order.distinct() + entries.filter { it !in order }

        fun serializeOrder(order: List<PlayerSlot>): String = normalize(order).joinToString(",") { it.name }

        fun serializeHidden(hidden: Set<PlayerSlot>): String =
            entries.filter { it in hidden && it.hideable }.joinToString(",") { it.name }
    }
}

enum class PlayerLook(val label: String) {
    Squared("Kotak"),
    Hero("Full bleed"),
}

enum class PlayerArtworkStyle(val label: String) {
    Card("Kartu"),
    Vinyl("Vinyl"),
    Clover("Clover"),
    ;

    companion object {
        fun from(raw: Int): PlayerArtworkStyle = entries.getOrElse(raw) { Card }
    }
}

enum class PlayerBackgroundStyle(val label: String) {
    Default("Tanpa latar"),
    Gradient("Gradient"),
    Blur("Blur"),
    Glow("Glow"),
    AppleMusic("Apple Music"),
    ;

    companion object {
        fun from(raw: Int): PlayerBackgroundStyle = entries.getOrElse(raw) { Gradient }
    }
}
