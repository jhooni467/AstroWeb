# AstroWeb 🌌

AstroWeb is a minimalist Android browser powered by Mozilla GeckoView.

## Current version

- Version: **0.16**
- Android application, built with Java 17 and Gradle
- GeckoView 160
- Debug APKs built separately for ARM64 and ARMv7 through GitHub Actions

## Available browser features

- Multi-tab browsing with top-aligned page previews
- Floating compact search control that expands into the address bar
- Private tabs
- Address suggestions from bookmarks and history
- Bookmarks and browsing history
- PDF export and a saved-downloads list
- Site permission prompts and per-site permission preferences
- Mobile and desktop display modes
- Find in page and basic browser navigation controls

## Build

GitHub Actions runs `:app:assembleDebug` on pushes to `main` and uploads the architecture-specific APKs as workflow artifacts.

AstroWeb is actively developing. A successful build confirms compilation and artifact upload; it does not replace testing on a physical Android device.
