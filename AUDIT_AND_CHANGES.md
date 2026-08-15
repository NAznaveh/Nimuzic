# NiMusic functional audit — 2026-08-11

This build focuses on turning UI-present features into real behavior while preserving the existing visual design.

## Implemented fixes

- **Playback/background ownership:** `AudioPlayerController` is now process-wide and is held by `MediaPlaybackService`, so UI/ViewModel recreation does not tear down the player during background playback.
- **Automatic next track:** existing completion flow remains connected to the active playback mode.
- **Queue accuracy:** Standard Random now exposes the actual candidate set; queue removal uses the correct displayed index; `Play Next` is now a real one-shot priority item across playback modes.
- **Queue editing:** added functional move-up/move-down controls in the queue sheet; duplicate queue entries are rejected.
- **Fair Shuffle persistence:** the remaining Fair Shuffle pool is reconstructed and shuffled after app restart instead of appearing empty.
- **Smart Shuffle:** recommendation-window generation now uses weighted probabilistic selection rather than simply taking the highest scores; engine state access is synchronized to avoid races between scoring and playback events.
- **Equalizer FX:** presets, bands, Bass Boost, and Virtualizer now attach to the active MediaPlayer audio session and persist across tracks/restarts. The UI also has a real enable/disable switch.
- **Playback speed:** speed is persisted and restored, then applied to newly prepared tracks.
- **Lyrics:** LRC-style timestamps are now parsed and the current lyric line is highlighted and auto-scrolled during playback.
- **Downloads:** removed the fake timer/random-progress simulation. Downloads now stream the actual URL, report real byte progress/speed, support HTTP range resume when available, reject HTML/JSON pages as audio files, and clean up the saved file when a download is deleted.
- **Library performance:** local-media scanning/file deletion now run on IO; artist rows reuse a precomputed artist map instead of filtering the whole library for every row; embedded artwork cache is bounded by memory size.
- **UI polish:** the Search/Download navigation item now uses a Search icon and the Persian navigation label is corrected to `جستجو`.
- **Playback statistics:** play counts are now driven by actual track starts, including automatic next-track playback, instead of only manual library taps.

## Intentionally not fabricated

The project still contains a `GoogleAccountRepository` with simulated cloud-backup methods, but those methods are not wired to a visible UI action in the current app. They were not left as a claimed working cloud-sync feature. A real Google Drive/Firebase sync should be implemented only after choosing the intended backend/authentication setup.

The online search screen still contains its existing demo catalog. The download engine itself is now real and only accepts direct audio URLs; arbitrary web pages are rejected rather than being saved as fake MP3 files.

## Verification limitation

The uploaded project does not contain a Gradle wrapper, and this execution environment does not have Gradle/Android SDK tooling installed. I therefore could not run a real Android Gradle build or device test here. I did run source-level checks and reviewed the changed code paths; the final ZIP is ready to open in Android Studio/Google AI Studio.
