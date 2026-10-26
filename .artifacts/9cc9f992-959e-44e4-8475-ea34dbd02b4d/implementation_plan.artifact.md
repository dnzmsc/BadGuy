# Implementation Plan - BadGuy AR Bicycle Tactical App (Space Movie Reticle Lock & Accurate Coordinate Mapping)

Implementing exact space-movie style central reticle locking and dynamic object tracking with accurate screen coordinate matrix scaling:
1. **Fixed Central Reticle**: Turns RED and beeps when an object enters the central aiming square.
2. **1st Click/Tap (Lock & Detach)**: Locks onto the object inside the central square. The red reticle detaches from screen center and sticks onto the object, tracking its position across the screen as the phone moves.
3. **Target Loss**: Announces "Bersaglio perso" if the tracked object leaves the camera view.
4. **2nd Click/Tap (Fire)**: Fires missiles at the tracked reticle's live coordinates with cinematic explosion.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Accurate Screen Matrix Scaling**: Bounding box coordinates converted normalized to screen dimensions (`screenWidth`, `screenHeight`), eliminating offset errors.
- **Central Reticle Red Shift & Beep**: Central reticle turns RED and beeps when an object enters the central square.
- **Space Movie Object Tracking**: 1st click/tap locks target; red reticle detaches from center and tracks object position across the screen.
- **Target Engagement**: 2nd click/tap detonates explosions on live reticle location.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test centering an object in reticle (turns red + beeps), tapping to lock/track across screen as phone moves, and tapping to fire.
