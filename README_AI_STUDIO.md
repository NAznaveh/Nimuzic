# NiMusic — Google AI Studio Build import

This is a native Android (Kotlin + Jetpack Compose) project intended for Google AI Studio Build mode / GitHub import.

## Import into Google AI Studio
1. Put this folder into a GitHub repository (the repository root must contain `settings.gradle.kts`, `build.gradle.kts`, `app/`, and `metadata.json`).
2. In Google AI Studio → Build, choose **Add files (+) → Import from GitHub**.
3. Select this repository and let AI Studio load the project.
4. Choose the **Android** platform if prompted.

## Important
Google AI Studio's documented existing-project import path is GitHub. A normal ZIP is not the primary import flow. This ZIP is structured as a clean GitHub-ready repository bundle.
