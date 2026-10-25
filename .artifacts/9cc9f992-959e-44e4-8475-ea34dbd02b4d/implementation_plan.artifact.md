# Implementation Plan - BadGuy AR Bicycle Tactical App (Dynamic Target Tracking & Loss Detection)

Implementing real-time dynamic tracking of the locked target across frames as the phone/camera moves, with automatic target loss detection ("Bersaglio perso").

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Dynamic Tracking**: In `LOCKED` state, `lockedBoundsState` continuously updates from live object detection frames to follow the target (e.g., clothespin, person, obstacle) as the camera moves.
- **Target Loss Detection**: If the locked target leaves the screen or is lost for more than a few frames, TTS announces "Bersaglio perso" and resets to `SEARCHING` mode.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test locking onto an object (like a clothespin), moving the camera to verify tracking, and moving away to verify "Bersaglio perso".
