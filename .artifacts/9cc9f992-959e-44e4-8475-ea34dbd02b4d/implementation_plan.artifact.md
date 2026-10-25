# Implementation Plan - BadGuy AR Bicycle Tactical App (Intelligent Target Lock & Clean Audio)

Fixing repetitive false-positive beeping and static bounding boxes by using ML Kit stream object detection without repeating audio triggers, combined with smart target locking and gesture controls.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Eliminated Repetitive Audio**: Removed automatic repeating audio loops during scanning to keep scanning mode clean and quiet.
- **ML Kit Stream Object Detection**: Uses ML Kit `STREAM_MODE` with `enableMultipleObjects()` to return live object bounding boxes when present, or `null` when no object is detected.
- **Smart Target Lock**: Tapping during `SEARCHING` locks onto the detected object (or center reticle if unclassified object), tracks it dynamically in `LOCKED` state ("Bersaglio tracciato"), and second tap fires missiles.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify quiet scanning, tap-to-lock, tracking, and tap-to-fire.
