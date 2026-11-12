# Eight-stage acceptance matrix

Device and continuous-visual checks must record the actual device, Android version, refresh rate, APK SHA-256, recording source, and result. No emulator result may be reported as Pixel 8 Pro evidence.

| Stage | Code/test evidence | Pixel 8 Pro | Overall |
|---|---|---|---|
| 1. Root navigation first frame | 12 targeted and 1,110 full JVM tests pass; ARM64 build pass; see [stage 1](01-root-navigation.md) | `NOT TESTED` | `DEVICE_PENDING` |
| 2. Loading feedback and scan state | 22 targeted and 1,111 full JVM tests pass; ARM64 build pass; see [stage 2](02-loading-feedback.md) | `NOT TESTED` | `DEVICE_PENDING` |
| 3. Grid stability | 35 targeted and 1,111 full JVM tests pass; ARM64 build pass; see [stage 3](03-grid-stability.md) | `NOT TESTED` | `DEVICE_PENDING` |
| 4. Photo open/close | 11 targeted tests pass; ARM64 build pass; see [stage 4](04-photo-transition.md) | `NOT TESTED` | `DEVICE_PENDING` |
| 5. Album transition | Five-image opacity regression assertion compiles; 1,111 full JVM tests pass; ARM64 build pass; see [stage 5](05-album-transition.md) | `NOT TESTED` | `DEVICE_PENDING` |
| 6. Viewer gestures/video | Pending audit | `NOT TESTED` | `NOT_STARTED` |
| 7. Oxygen Classic motion | Pending audit | `NOT TESTED` | `NOT_STARTED` |
| 8. Integration and final validation | Pending audit | `NOT TESTED` | `NOT_STARTED` |

