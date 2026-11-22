# AstroWeb 🌌

AstroWeb is a minimalist Android browser powered by Mozilla GeckoView.

## Current version

- Version: **0.17**
- Android application, built with Java 17 and Gradle
- GeckoView 160
- Architecture-specific debug APKs for ARM64 and ARMv7

## Browser features

- Multi-tab browsing with top-aligned page previews
- Floating compact search control with an animated expansion into the address bar
- Address suggestions grouped by global trends, previous searches, related searches, bookmarks and visited pages
- Personalized search history stored locally on the device
- Global discovery trends gathered from Google Trends feeds for Mexico, the United States, Spain, Brazil and Japan
- Private tabs do not save personalized searches
- Bookmarks and browsing history
- PDF export and saved-downloads list
- Site permission prompts and per-site permission preferences
- Mobile and desktop display modes
- Find in page and browser navigation controls

## Privacy and recommendations

The browser stores search-query history locally in app preferences. It is not uploaded to AstroWeb servers. Global trend feeds contain general public trend terms; only those feeds are fetched. Clearing browsing history also clears the personalized search history. Private tabs do not record searches.

## Build

GitHub Actions runs `:app:assembleDebug` on pushes to `main` and uploads architecture-specific APKs as workflow artifacts.

A successful build confirms compilation and artifact upload; it does not replace testing on a physical Android device.
