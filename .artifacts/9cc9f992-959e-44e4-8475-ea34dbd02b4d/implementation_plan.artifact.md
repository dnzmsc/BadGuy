# Implementation Plan - BadGuy AR Bicycle Tactical App (Volumetric Radial Gradient Explosions & Rocket Plumes)

Upgrading visual graphics from simple vector lines/circles to **Volumetric Radial Fireball Gradients**, **Billowing Charcoal Smoke**, **Dual Homing Rockets with Exhaust Trails**, and explaining the asset pipeline.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Volumetric Explosion Fireball**: Uses `Brush.radialGradient` with multi-stop flame colors (White Core -> Bright Yellow -> Burning Crimson -> Charcoal Smoke -> Alpha Fade) for a realistic expanding fireball explosion.
- **Volumetric Rocket Plumes**: Rockets drawn with metallic warheads and linear gradient exhaust plumes (Fire Yellow -> Burning Orange -> Smoke Trail).
- **Explanation**: Provided technical explanation on how sprite sheets / animated GIF assets / volumetric shaders transform vector drawing into realistic game/AR visuals.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify the volumetric explosion fireball, rocket launch trails, and audio.
