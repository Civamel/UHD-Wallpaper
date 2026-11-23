# Ultra HD Wallpaper

An Android wallpaper application foundation for UHD/4K still wallpapers and Android live wallpapers.

## Current foundation
- Native Android application written in Kotlin.
- Initial home screen with featured still and live wallpaper sections.
- Still wallpaper apply flow with Home Screen, Lock Screen, and Both choices on Android 7.0+.
- Android Live Wallpaper service registered through the system wallpaper picker.
- Internet permission reserved for the upcoming remote catalog and image delivery layer.

## Important current limitations
- The visible wallpaper cards are temporary local gradient placeholders, not the final 5,000+ online wallpaper catalog.
- The first live wallpaper service is a static gradient proof of integration; animation, preview polish, and live media catalog are not implemented yet.
- The repository has not been built or tested on a physical Android device yet.
- No APK is available yet.

## Planned architecture
Keep the APK lightweight by storing catalog metadata and thumbnails remotely, loading full-resolution files on demand, and caching selected wallpapers locally. The app should not bundle thousands of image files.

## Android project
- Application ID: `com.civamel.ultrahdwallpaper`
- Minimum SDK: 23
- Target SDK: 35
- Main branch: `main`
