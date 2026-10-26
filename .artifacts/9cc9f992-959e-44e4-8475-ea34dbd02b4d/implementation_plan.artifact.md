# Implementation Plan - BadGuy AR Bicycle Tactical App (Real-Time Object Silhouette Contour Outline)

Implementing real-time Sobel edge/contour extraction in CameraX `ImageAnalysis` to draw a glowing neon silhouette outline around the exact physical shape of whatever object or person is being framed.

## Proposed Changes

### [Component Name]

#### [MODIFY] [MainActivity.kt](file:///Users/cammoranesi/AndroidStudioProjects/BadGuy/app/src/main/java/com/example/badguy/MainActivity.kt)
- **Edge Contour Extraction**: Computes real-time Sobel luminance gradients across the detected object's ROI in CameraX `ImageAnalysis` to extract shape edge points.
- **Glowing Silhouette Path Overlay**: Renders a glowing neon outline path (Gold in `SEARCHING`, Red in `LOCKED`) directly tracing the physical silhouette of the object (person, clothespin, obstacle).

## Verification Plan

### Automated Tests
- Gradle build (`app:assembleDebug`)

### Manual Verification
- Deploy to connected device (`RFGL34STWPJ`) and verify that pointing the camera at objects outlines their exact physical shape on screen.
