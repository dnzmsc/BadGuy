# Implementation Plan - BadGuy AR Bicycle Tactical App (Target-Locked Explosions)

Fixing AR weapon firing to detonate explosions directly at the acquired target's screen coordinates rather than screen center.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Target-Locked Explosions**: When firing at an acquired target, explosions (flash, shockwaves, sparks) originate at the center of the target's bounding box (`targetBounds`) instead of screen center.
- Maintains fallback to screen center if no target is active.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify that firing detonates precisely where the target is located in the AR camera feed.
