# Implementation Plan - BadGuy AR Bicycle Tactical App (3-Step Camera Tap Lock Workflow)

Implementing a precise 3-step camera-style tap workflow:
1. **First tap**: Enters Acquisition mode (scanning sound).
2. **Second tap**: Locks onto the target in view, plays camera focus beeps, and TTS says "Bersaglio tracciato".
3. **Third tap**: Fires at the locked target coordinates with cinematic explosion.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- Added explicit state machine for tap workflow: `SEARCHING` -> `ACQUIRING` -> `LOCKED` -> `FIRING`.
- Camera-style focus and lock audio feedback via `AudioTrack`.
- Voice announcement "Bersaglio tracciato" strictly upon locking.
- Explosions detonate precisely at the locked target coordinates.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test the 3-step tap sequence.
