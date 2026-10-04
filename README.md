# Miuix Reader

A local-first Android reader with a Xiaomi HyperOS-inspired interface.

## Features

- EPUB 2/3, PDF, CBZ, and plain text with UTF-8, UTF-16, and GB18030 decoding
- Multi-select import through the Android Storage Access Framework
- Open EPUB, TXT, PDF, CBZ, and ZIP files shared from other apps
- Persistent local library with duplicate detection and reading progress
- A larger recently-read book card and whole-book progress on every bookshelf card
- EPUB/CBZ cover extraction and EPUB metadata parsing
- Library search by title or author, plus metadata and cover editing
- Tap-to-reveal animated reader controls with page numbers and a draggable progress slider
- Table of contents navigation, EPUB full-text search, and bookmarks
- Shared EPUB/TXT typography controls for font family, size, and weight
- Reader backgrounds: theme, preset or custom solid colors, or imported images with adjustable dimming
- Independent bookshelf image background with automatic dimming
- System, light, and dark appearance modes
- Optional AndroidLiquidGlass surfaces with adjustable opacity on the bookshelf and reader
- Predictive back gestures for the reader chrome and sheets
- Immersive reader navigation that restores system controls while menus are open

Android 13 or newer is required. Building requires JDK 17 and Android SDK Platform 37.

The application uses [Miuix](https://github.com/compose-miuix-ui/miuix) for its UI,
[Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) for publication rendering,
and [Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) for the optional liquid-glass effect.

## Build

```bash
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

GitHub Actions installs Android API 37, runs unit tests and Lint, and uploads a
Debug APK for pushes to `main` and manual runs. Pull requests run verification
without uploading an artifact. Uploaded artifacts are retained for 90 days.

For a fixed CI test signature, configure these repository secrets:

- `CI_KEYSTORE_BASE64`
- `CI_KEYSTORE_PASSWORD`
- `CI_KEY_ALIAS`
- `CI_KEY_PASSWORD`

Without those secrets, CI uses the standard Android Debug keystore.

## License

Apache License 2.0.
