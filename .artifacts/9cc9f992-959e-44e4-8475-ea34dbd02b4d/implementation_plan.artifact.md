# Implementation Plan - BadGuy AR Bicycle Tactical App (Enhanced)

Upgrade the BadGuy AR app with advanced real-time ML Kit shape/object detection, dynamic target tracking, realistic fire & spark particle explosions, Italian TTS voice feedback ("Bersaglio acquisito"), and Git repository sync.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- Integrated ML Kit Object Detection in CameraX `ImageAnalysis` pipeline.
- Added AI shape/object tracking so "Bersaglio acquisito" is spoken in Italian only when an object/shape is recognized in the camera feed.
- Dynamic targeting box that tracks the recognized object's bounding box across frames.
- Advanced realistic fire and spark particle explosion system with core flash and expanding shockwave rings.
- Updated all TTS prompts and UI telemetry to Italian.

#### [MODIFY] [build.gradle.kts](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/build.gradle.kts)
- Added ML Kit Object Detection dependency (`com.google.mlkit:object-detection`).

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`) to verify compilation.

### Manual Verification
- Deploy to connected Android device (`RFGL34STWPJ`) and verify camera feed, object tracking, Italian voice feedback, and explosion visual effects.
