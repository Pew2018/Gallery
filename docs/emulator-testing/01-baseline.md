# Real Android emulator regression — first-stage implementation

The new non-publishing workflow runs real Android instrumentation against the
existing fix/photo-viewer-five-item-boundary branch, independently of arm64
Release tests. Only the emulator job opts into debug testBuildType/x86_64.

Implemented: valid generated PNG files, nonmonotonic mtimes, varying names,
public A, explicit .nomedia N, unprotected control P, seven folder combinations,
15 primary sort modes, 12011/20501/50009-item workloads, real
PhotoProfileMediaLoader scans, exact path membership, dedupe and logcat/JUnit
artifacts. The large workload may exceed standard runner resources; report it.

NOT IMPLEMENTED / NOT PASSED: actual PIN protection and authentication via
SecurityDialog, virtual Favorites, real ViewPager swipes and visual bitmap
identity checks, video and RAW decoders, injected races and physical device
performance. P has no PIN lock in this stage and must not be reported as a
protected-folder success. Fixture pixels repeat every 64 images and thus
cannot prove real screen content identity.

Push to the fix branch runs smoke API34; manual workflow_dispatch offers
30/34/36 and smoke/large/segment/ceiling. No release signing changed.

## Stage 2 added (verify with next CI run)

Real JPEG/SVG fixtures are now generated and included in enumeration expectations.
A real Favorites Room relation is exercised as a virtual source, including overlap
with A/N folders, explicit .nomedia visibility, and dynamic removal. This is not
PIN testing; P remains an unprotected control. Viewer swipe/render and video/RAW
remain unimplemented, and are not reported as passing.
