package com.moody.player.domain

/** Decoder modes, as in MX Player: hardware, hardware for everything (incl. rendering), software. */
enum class Decoder(val label: String) { HW("HW"), HW_PLUS("HW+"), SW("SW") }

/** Subtitle sizes from the mockup. [vlcRelSize] is libVLC's freetype-rel-fontsize (smaller = bigger text). */
enum class SubSize(val label: String, val vlcRelSize: Int) { S("S", 20), M("M", 16), L("L", 13), XL("XL", 10) }

enum class SubBg(val label: String) { SHADOW("Shadow"), SOFT("Soft"), SOLID("Solid") }

enum class LoopMode(val label: String) { OFF("Off"), ONE("This video"), FOLDER("Folder") }

enum class Aspect(val label: String, val vlcRatio: String?, val fill: Boolean = false) {
    FIT("Fit", null), FILL("Crop", null, fill = true), STRETCH("Stretch", null), R16_9("16:9", "16:9"), R4_3("4:3", "4:3"), ORIGINAL("Original", null);

    fun next(): Aspect = entries[(ordinal + 1) % entries.size]
}

enum class VideoView { FOLDERS, ALL }
enum class LayoutMode { GRID, LIST }
