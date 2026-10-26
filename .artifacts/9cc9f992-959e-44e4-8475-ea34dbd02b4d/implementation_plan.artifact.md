# Implementation Plan - BadGuy AR Bicycle Tactical App (16-Frame Bitmap Explosion Sprite Engine & Missile Textures)

Upgrading from vector drawing to an authentic **16-Frame Bitmap Explosion Sprite Engine** with **Photorealistic Multi-Layer Fireball Textures**, **Dark Billowing Smoke**, **Sparks**, and **Metallic Rocket Sprites**.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **16-Frame Bitmap Explosion Engine**: Generates 16 high-resolution ARGB_8888 bitmap explosion textures with `RadialGradient` shaders, white-hot cores, crimson flame, and charcoal smoke clouds.
- **Bitmap Native Canvas Rendering**: Draws the active explosion bitmap frame directly onto Compose Canvas (`nativeCanvas.drawBitmap`) at target coordinates `(epicenterX, epicenterY)`.
- **Realistic Missile Warheads & Trails**: Renders metallic rocket warhead sprites with trailing exhaust plumes.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify the 16-frame bitmap explosion sprite sequence, missile flight, and impact audio.
