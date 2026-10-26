# Implementation Plan - BadGuy AR Bicycle Tactical App (Dual Homing Missiles & Cinematic Impact)

Upgrading weapon engagement to launch dual homing missiles from the bottom corners of the smartphone, traveling along curved ballistic trajectories to impact the tracked target with realistic explosion FX and multi-channel audio.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Dual Homing Missiles**: Missiles launch from bottom-left `(0, height)` and bottom-right `(width, height)` corners when firing.
- **Ballistic Trajectories & Fire Trails**: Missiles travel along curved homing arcs towards the live tracked reticle position, drawing glowing fire/smoke trails behind them.
- **Target Impact & Explosion**: Explosions detonate at `(epicenterX, epicenterY)` upon missile impact.
- **Enhanced Audio**: Added missile launch whoosh sound before the heavy impact explosion.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test missile launch from bottom corners, homing trajectory, impact explosion, and audio.
