# Implementation Plan - BadGuy AR Bicycle Tactical App (Robust Shape/Object Recognition & Center Fallback)

Enhancing shape and object recognition by relaxing ML Kit filtering and adding a smart center-reticle fallback for unclassified objects/obstacles.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Robust Object Detection**: Enabled multiple objects detection (`enableMultipleObjects()`).
- **Smart Center-Reticle Fallback**: If ML Kit's base model doesn't return a labeled category, the system automatically uses a dynamic center-reticle bounding box so any object, person, or vehicle placed in the crosshairs can be locked and tracked Iron Man style.

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and test object/shape locking.
