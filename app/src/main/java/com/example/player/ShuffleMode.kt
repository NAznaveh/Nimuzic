package com.example.player

enum class ShuffleMode {
    OFF,
    STANDARD_RANDOM,
    FAIR_NON_REPEAT, // Fair Shuffle: guarantees no track repeats until all tracks in queue have been played once
    SMART_SHUFFLE   // Smart Shuffle: Dynamic behavioral & similarity weighted shuffle
}

enum class RepeatMode {
    OFF,
    REPEAT_ALL,
    REPEAT_ONE
}
