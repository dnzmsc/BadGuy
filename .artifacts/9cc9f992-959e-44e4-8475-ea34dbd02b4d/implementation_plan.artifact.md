# Implementation Plan - BadGuy AR Bicycle Tactical App (Iron Man HUD & Advanced ML Filtering)

Refining target acquisition, Iron Man style HUD, closest-object priority filtering, cinematic explosions, and cinematic audio synthesis.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Iron Man HUD**: Removed static center box when no target is present. Dynamic target brackets lock strictly onto the closest/largest detected foreground object (person/obstacle).
- **Smart Filtering**: Prioritizes the largest object (closest to camera on bicycle) above a minimum size threshold, preventing false positives on background noise.
- **Cinematic Explosions**: Multi-layer procedural explosion canvas rendering with blinding flash, glowing multi-color fireball, expanding dual shockwaves, 80+ fiery embers, and smoke clouds.
- **Cinematic Audio**: Upgraded AudioTrack synthesis for deep cinematic explosion rumble + noise burst and layered sci-fi laser sound effects.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify Iron Man HUD tracking, closest-target locking, speech feedback, and cinematic explosion/audio.
