# Expanded CI test matrix — 2026-11-04

This work is in fix/photo-viewer-five-item-boundary only. No production media
scanner, pager, protection, signing or backup logic is modified.

## Suites

1. smoke (64 PNG per A/N/P): 15 loader sorting flags; seven folder combos,
   real Favorites, explicit .nomedia, selected thumbnail and 2+2 swipe.
2. sort_ui (64 PNG per A/N/P): 15 modes **each separately opened in MediaActivity**
   using profile A+N; each taps a committed RecyclerView row, swipes 2 pages
   forward + 2 backward and checks pager index, fragment path, rendered PNG
   pixel signature. For first run, compare the Logcat BEGIN/END per mode.
3. deep (A=12011, N=12013, P=12017): ordinary folder deep scroll, A+N
   profile deep scroll and A+N+P random deep scroll; applies three actual
   touchscreen grid swipes before tapping a visible item.
4. boundary/segment (A=20501) and boundary/ceiling (A=50009): real
   grid + viewer at selected anchors near 10000/20000/30000/49990,
   including segmented catalog behavior. Do not count a queued run as passed.
5. pin: genuine SecurityDialog, wrong/cancel, independent protected P/P2,
   positive source challenge, active-token visibility and post-revoke denial.
   This is not biometric authentication or an injected access token.

## Unique pixel identity

Every 32x32 RGBA PNG has a per-file source/index fingerprint encoded in an
8x8 grid of blocks. The emulator test compares 64 sampling points from the
visible drawable against the decoded real file, with a narrow RGB tolerance.
A non-null Drawable and matching path **alone** cannot pass.

## Faster fixture preflight

The GitHub-hosted runner uses stdlib Python PNG encoding and tar packaging.
Android Emulator unpacks the archive once before tests, avoiding tens of
thousands of individual PNG encodes in the emulator process. Test @BeforeClass
asserts the exact imported scale and counts, then creates real JPEG and SVG
supplements. Local/non-CI tests retain on-device generation as fallback.

## Evidence and limitations

Save JUnit XML, Logcat, source counts/manifest, memory, storage, Windows
dumps and screenshot export diagnostics on failure. Pixel proof is obtained
from the drawable comparison, not by OCR or screenshot file names.

The first completed 15-sort UI run was on Android 14/API34, tested all 15
BEGIN/END cases successfully. Large and PIN runs remain independent.
No real video/RAW content, every Android API, physical phone performance,
encrypted favorites or full per-origin PIN matrix has been established.
