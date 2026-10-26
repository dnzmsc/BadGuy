# Implementation Plan - BadGuy AR Bicycle Tactical App (User Real Explosion GIF Asset Integration)

Integrating the user's real explosion GIF asset (`R.drawable.explosion`) placed in `res/drawable/` using Coil Compose and native Android `ImageDecoderDecoder` / `GifDecoder`.

## Proposed Changes

### [Component Name]

#### [MODIFY] [build.gradle.kts](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/build.gradle.kts)
- Added Coil Compose and Coil Gif dependencies (`io.coil-kt:coil-compose`, `io.coil-kt:coil-gif`).

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- Integrated Coil `ImageLoader` with `ImageDecoderDecoder` / `GifDecoder`.
- Rendered the user's real `R.drawable.explosion` GIF asset dynamically positioned at target epicenter `(epicenterX, epicenterY)` upon missile impact.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test missile impact rendering the user's real explosion GIF asset.
