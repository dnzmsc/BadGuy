# Implementation Plan - BadGuy AR Bicycle Tactical App (GraphicsLayer Hardware Screen Blending)

Replacing the white tint filter with `Modifier.graphicsLayer(blendMode = BlendMode.Screen)` for hardware-accelerated additive GIF compositing over the live camera feed.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- Replaced `ColorFilter.tint` with `Modifier.graphicsLayer(blendMode = BlendMode.Screen)`.
- Black background becomes 100% transparent on hardware RenderThread while keeping all full-color GIF flames and sparks intact.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify full-color GIF explosion over live camera feed with zero white tinting.
