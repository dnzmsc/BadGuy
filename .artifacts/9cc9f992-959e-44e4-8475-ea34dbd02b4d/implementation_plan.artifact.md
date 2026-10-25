# Implementation Plan - BadGuy AR Bicycle Tactical App (Dedicated Central Selection Rectangle)

Implementing an explicit, dedicated Central Selection Rectangle fixed dead-center on the screen for guaranteed, reliable target acquisition and missile engagement.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Central Selection Rectangle**: Explicit HUD selection rectangle fixed at the screen center for aiming and acquiring targets.
- **Acquisition & Lock**: Pressing headphone Play/Pause or tapping locks the Central Selection Rectangle onto the targeted object, turning brackets red ("Bersaglio tracciato").
- **Targeted Firing**: Second click/tap detonates cinematic explosions directly inside the Central Selection Rectangle.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify the Central Selection Rectangle, lock transition, and explosion placement.
