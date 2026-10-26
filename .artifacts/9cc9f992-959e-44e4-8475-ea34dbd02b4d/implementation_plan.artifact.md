# Implementation Plan - BadGuy AR Bicycle Tactical App (Fixed Impact Coordinates & One-Shot GIF Playback)

Fixing explosion GIF positioning and loop behavior:
1. **Fixed Impact Epicenter**: Captures the target's exact screen coordinates `(impactX, impactY)` at the moment of impact so the explosion GIF remains rock-solid in place and never shifts while playing.
2. **One-Shot Non-Looping Playback**: Adjusted animation display duration to 1.3 seconds to match the new GIF's exact length, preventing it from repeating/looping.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Frozen Impact Position**: Stored `impactXPx` and `impactYPx` upon firing to prevent the GIF from shifting as camera/bounds move.
- **One-Shot Timing**: Set explosion display delay to 1300ms so the GIF plays once completely and vanishes without looping.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify that firing detonates the GIF fixed in place without shifting or repeating.
