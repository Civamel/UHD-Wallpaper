# Ultra HD Wallpaper

Native Android wallpaper app foundation in Kotlin.

## Implemented
- Android application shell and first home screen.
- Wallpaper cards open a dedicated full-screen preview.
- Still wallpaper apply dialog: Home Screen, Lock Screen, or Both on Android 7.0+.
- Live wallpaper preview handoff to the Android system wallpaper picker.
- Initial animated live wallpaper service with lifecycle-aware frame rendering.
- Internet permission reserved for the remote wallpaper catalog.

## Current prototype limitations
- Home-screen wallpaper cards still use generated gradient placeholders, not the final photo collection.
- The live wallpaper is an animated gradient proof of the Android service pipeline, not the final live-video/image catalog.
- The remote 5,000+ wallpaper catalog, search, category filtering, favorites, download cache, and production media delivery are not connected yet.
- No build or physical-device test has been run; no APK is available yet.

## Planned lightweight delivery
Keep the APK small by serving catalog metadata and thumbnails remotely, downloading full-resolution images only when selected, and caching them locally. Live wallpaper assets should be streamed or downloaded on demand rather than bundled into the APK.

## Android configuration
- Application ID: `com.civamel.ultrahdwallpaper`
- Minimum SDK: 23
- Target SDK: 35
- Main branch: `main`


## Wallpaper download
Selected wallpapers can now be exported as JPEG using Android's document picker.
