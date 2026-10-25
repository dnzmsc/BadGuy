# Implementation Plan - BadGuy AR Bicycle Tactical App (Hands-Free Handlebar Aiming & Feature Lock)

Implementing a safe, bicycle-optimized AR tactical system:
1. **Fixed Center Aiming Zone**: The user steers the bicycle to align the central Iron Man targeting reticle with the person or obstacle ahead.
2. **Headphone Button Control**: Pressing the headphone Play/Pause button captures and locks onto whatever is inside the central aiming zone, initializing real-time target tracking.
3. **Voice & Audio Feedback**: TTS announces "Bersaglio tracciato", camera lock beeps play.
4. **Engagement**: Second headphone button press fires missiles, detonating explosions on target coordinates.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Central Aiming Zone (ROI)**: Permanent central Iron Man targeting reticle for bicycle handlebar aiming.
- **Headphone Button / Tap Trigger**: Captures and locks onto the ROI contents on 1st click/tap ("Bersaglio tracciato").
- **Dynamic Feature Tracking**: Smoothly tracks locked target position across frames as the bicycle moves.
- **Target Engagement**: 2nd click/tap fires missiles at locked target with cinematic explosion.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test aiming with central reticle, headphone button locking, tracking, and firing.
