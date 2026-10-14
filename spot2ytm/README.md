# Spot2YTM (native Android)

Copy a Spotify playlist to YouTube Music. Fully native Kotlin app — Jetpack Compose UI,
coroutines, OkHttp. No WebView, no bundled HTML, no CORS proxies.

## How it works

1. **Pull the playlist** — paste a link, or tap *Share → Spot2YTM* in the Spotify app.
   Uses the Spotify Web API (client credentials) and falls back to the public embed page.
   `spotify.link` short links are expanded automatically.
2. **Match on YouTube** — each track is looked up via YouTube Music search (songs first),
   then regular YouTube search. Four lookups run in parallel; no API key or quota needed.
3. **Export** — open instant queue links (no login), or paste a Google OAuth access token
   (`ya29…`) to create a private playlist on your account.

## Project layout

```
app/src/main/java/com/spot2ytm/app/
  MainActivity.kt          entry point + share-intent handling
  data/                    networking: Spotify, YouTube, link parsing, settings
  ui/MainViewModel.kt      app state and background jobs
  ui/MainScreen.kt         Compose UI
  ui/theme/Theme.kt        colors
```

## Build

```
./gradlew testDebugUnitTest assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. GitHub Actions builds it on every push/PR
(see the *Spot2YTM-release-APK* artifact). Default Spotify credentials come from
`gradle.properties` and can be changed in the app's Settings sheet.
