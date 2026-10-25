# Implementation Plan - BadGuy AR Bicycle Tactical App (Live Dynamic Target Tracking & Visual Highlight)

Integrating real-time object detection and dynamic frame-by-frame target tracking so the locked red HUD brackets follow the acquired object across the screen as the camera/phone moves.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Live Image Analysis**: Restored CameraX `ImageAnalysis` with ML Kit stream object detector.
- **Clear Visual Scanning Highlight**: Displays prominent pulsing gold HUD brackets around detected objects in the central region during `SEARCHING`.
- **Dynamic Frame-by-Frame Tracking**: In `LOCKED` state, updates `lockedBoundsState` continuously to follow the object's motion across the screen as the phone moves.
- **Target Loss Detection**: Announces "Bersaglio perso" if the locked object leaves the camera view.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test visual scanning highlight, locking onto an object, moving the camera to verify tracking across the screen, and firing.
