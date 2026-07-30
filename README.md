# Miuix Reader

A local-first Android reader with a Xiaomi HyperOS-inspired interface.

## Features

- EPUB 2/3
- Plain text with UTF-8, UTF-16, and GB18030 decoding
- PDF
- CBZ
- Multi-select import through the Android Storage Access Framework
- Persistent local library with duplicate detection and reading progress
- EPUB/CBZ cover extraction and EPUB metadata parsing
- Tap-to-reveal animated reader controls with progress indicators
- Shared EPUB/TXT typography controls for font, size, and weight
- Independent bookshelf and reader image backgrounds with automatic dimming
- System, light, and dark appearance modes
- Optional AndroidLiquidGlass surfaces on the bookshelf and reader

Android 13 or newer is required.

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
