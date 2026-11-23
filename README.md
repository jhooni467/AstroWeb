# AstroWeb 🌌

AstroWeb is a minimalist Android browser powered by Mozilla GeckoView.

## Current version

- Version: **0.19 (diagnostic build)**
- Android application, built with Java 17 and Gradle
- GeckoView 160
- Architecture-specific debug APKs for ARM64 and ARMv7

## Browser features

- Multi-tab browsing with on-demand viewport previews to reduce memory use
- Floating compact search control with an animated expansion into the address bar
- Address suggestions grouped by global trends, typo-tolerant previous searches, related searches, bookmarks and visited pages
- Personalized search history stored locally on the device as soon as a search is started
- Global discovery trends gathered from Google Trends feeds for Mexico, the United States, Spain, Brazil and Japan
- Address suggestions update after a short typing pause; private tabs do not save personalized searches
- Bookmarks and browsing history
- PDF export and saved-downloads list
- Site permission prompts and per-site permission preferences
- Mobile and desktop display modes
- Manual tab recovery after a browser content-process stop, preventing silent automatic reloads
- Copyable, privacy-conscious diagnostics when GeckoView reports a content-process crash or kill
- Lightweight local start page with nonessential entrance animations removed
- Find in page and browser navigation controls

## Privacy and recommendations

The browser stores search-query history locally in app preferences. It is not uploaded to AstroWeb servers. Global trend feeds contain general public trend terms; only those feeds are fetched. Clearing browsing history also clears the personalized search history. Private tabs do not record searches.

## Build

GitHub Actions runs `:app:assembleDebug` on pushes to `main` and uploads architecture-specific APKs as workflow artifacts.

A successful build confirms compilation and artifact upload; it does not replace testing on a physical Android device.

## v0.19 diagnostic focus

This build adds a copyable diagnostic summary for the recurring GeckoView content-process stop. It distinguishes the `onCrash` and `onKill` callbacks and records Android/device and memory measurements. It deliberately excludes visited URLs and browsing history. This is a diagnostic step, not confirmation that the underlying stop has been fixed.
