# Implementation Plan - BadGuy AR Bicycle Tactical App (Continuous Detection & Gesture Workflow)

Refining the tactical workflow based on exact user specifications:
1. **Continuous Detection**: System continuously scans for shapes/objects in camera feed.
2. **Passive Notification**: When detected, plays subtle focus beep and highlights target with pulsing brackets.
3. **Single Tap (Lock)**: Locks onto detected target, starts tracking it, and TTS announces "Bersaglio tracciato".
4. **Single Tap (Fire)**: When locked, tapping again fires missiles at the target coordinates with cinematic explosion.
5. **Double Tap (Unlock)**: Double-tapping while locked unlocks/releases the target and returns to phase 1 (searching).

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Continuous Shape Recognition**: Live object detection highlights objects in real-time during `SEARCHING`.
- **Gesture Detection**: `detectTapGestures` distinguishing single tap (Lock / Fire) and double tap (Unlock).
- **Accurate Audio/Voice Feedback**: Focus beeps on detection, "Bersaglio tracciato" on lock, cinematic explosions on fire.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test continuous detection, tap-to-lock, tap-to-fire, and double-tap-to-unlock.
