# Implementation Plan - BadGuy AR Bicycle Tactical App (Center-Weighted Priority & Debounced Audio)

Implementing center-weighted target selection (prioritizing objects closest to camera screen center) with debounced focus beeps upon target entry.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Center-Weighted Target Selection**: Sorts detected objects by distance from their center point to the camera frame center, prioritizing objects directly in front of the bicycle/camera.
- **Debounced Focus Beep**: Plays a single subtle camera focus beep when a new target enters the central crosshairs (debounced every 3 seconds to avoid sound spam).
- **Target Lock & Firing**: 1st tap locks onto the center-detected object ("Bersaglio tracciato"), 2nd tap fires missiles at target coordinates.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test center-weighted object detection, focus beep on target entry, tap-to-lock, and tap-to-fire.
