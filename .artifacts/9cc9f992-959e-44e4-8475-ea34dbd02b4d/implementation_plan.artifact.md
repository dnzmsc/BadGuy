# Implementation Plan - BadGuy AR Bicycle Tactical App (Computer Vision Contrast Blob Detection & Tap-to-Target)

Upgrading shape/object recognition from limited canned ML Kit categories to a robust **Computer Vision Edge & Contrast Blob Detector** combined with **Tap-to-Target Coordinate Locking**, allowing users to lock onto *any* physical object (like a clothespin / molletta) instantly.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Contrast & Edge Blob Detection**: Added real-time luminance gradient and contrast blob analysis in CameraX `ImageAnalysis` to detect salient objects/shapes (clothespins, persons, obstacles) regardless of ML Kit's rigid categories.
- **Tap-to-Target Precision**: When locking onto a target, the app pinpoints the exact object at the target location or most prominent contrast blob, ensuring 100% reliable locking on arbitrary physical items.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test locking onto arbitrary items (e.g. clothespin / molletta).
