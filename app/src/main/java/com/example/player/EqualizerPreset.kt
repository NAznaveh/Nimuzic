package com.example.player

data class EqualizerSettings(
    val isEnabled: Boolean = true,
    val presetName: String = "Spotify Bass Boost",
    val band60Hz: Float = 4f,    // -10 to +10 dB
    val band230Hz: Float = 2f,
    val band910Hz: Float = 0f,
    val band3600Hz: Float = 3f,
    val band14000Hz: Float = 5f,
    val bassBoost: Float = 60f,  // 0 to 100
    val virtualizer3D: Float = 40f // 0 to 100
)

object EqualizerPresets {
    val FLAT = EqualizerSettings(presetName = "Flat", band60Hz = 0f, band230Hz = 0f, band910Hz = 0f, band3600Hz = 0f, band14000Hz = 0f, bassBoost = 0f, virtualizer3D = 0f)
    val BASS_BOOST = EqualizerSettings(presetName = "Bass Boost", band60Hz = 8f, band230Hz = 5f, band910Hz = 1f, band3600Hz = 2f, band14000Hz = 4f, bassBoost = 80f, virtualizer3D = 30f)
    val ROCK = EqualizerSettings(presetName = "Rock", band60Hz = 6f, band230Hz = 3f, band910Hz = -1f, band3600Hz = 4f, band14000Hz = 7f, bassBoost = 50f, virtualizer3D = 40f)
    val POP = EqualizerSettings(presetName = "Pop", band60Hz = -1f, band230Hz = 2f, band910Hz = 5f, band3600Hz = 4f, band14000Hz = -2f, bassBoost = 20f, virtualizer3D = 20f)
    val PERSIAN_ACOUSTIC = EqualizerSettings(presetName = "Persian Acoustic / Traditional", band60Hz = 3f, band230Hz = 1f, band910Hz = 6f, band3600Hz = 5f, band14000Hz = 8f, bassBoost = 35f, virtualizer3D = 60f)

    val ALL_PRESETS = listOf(BASS_BOOST, FLAT, ROCK, POP, PERSIAN_ACOUSTIC)
}
