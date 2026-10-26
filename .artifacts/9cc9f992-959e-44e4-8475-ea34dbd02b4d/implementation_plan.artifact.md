# Implementation Plan - BadGuy AR Bicycle Tactical App (Automatic Black/Green Background Removal & Extended Duration)

Implementing real-time **Chromakey / BlendMode Black Background Keying** (`BlendMode.Screen`) so black/green backgrounds on explosion GIFs automatically become 100% transparent, and extending explosion playback duration to 2.2 seconds.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Automatic Black Background Removal**: Applied `colorFilter = ColorFilter.blendMode(Color.Unspecified, BlendMode.Screen)` / `BlendMode.Plus` so black background on GIF assets vanishes, leaving pure photorealistic flames over the camera feed.
- **Extended Explosion Duration**: Increased GIF explosion display time and workflow firing delay to 2200ms (2.2 seconds).

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify that black background on `R.drawable.explosion` vanishes and explosion plays for 2.2 seconds.
