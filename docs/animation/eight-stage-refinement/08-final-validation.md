# Stage 8: final integration and acceptance handoff

Date: 2026-11-13 (Hong Kong time)

## Integrated engineering validation

- Code-bearing source SHA: `2f3941121287dfcf3b8c2b9abb319ee262c6a3ca` on `perf/home-gallery-sorting`.
- Final isolated-worktree command: `:app:testFossDebugUnitTest :app:compileFossDebugAndroidTestKotlin :app:assembleFossRelease -PABI_SPLITS=true -PABI_TARGET=arm64-v8a`.
- Full JVM suite: 1,111/1,111 passed across 163 suites; 0 failures, 0 skipped.
- Android instrumentation sources compile. The instrumentation suite itself was not run because `adb devices -l` returned an empty device list.
- ARM64 FOSS Release build passed from the committed source in `/private/tmp/Gallery-final-validation`.
- Build outputs identify `org.fossify.gallery`, version `1.14.1` / code `32`, compile SDK 36, and `arm64-v8a` native libraries only.
- The Gradle APK is unsigned because no release signing config is available. A separate locally signed copy was produced with a temporary self-signed certificate and verified with APK Signature Scheme v2 and v3. This copy can be installed as a fresh app, but cannot update an existing Gallery installed under a different signing certificate. Do not uninstall or clear an existing Gallery without backing up its data first.
- APK source is the code-bearing SHA above; this final-validation report commit changes only documentation. The temporary signing keystore and its password remain under `/private/tmp`, not in the repository.

## Regression coverage and limits

JVM coverage exercises directory/media ordering, scan publication, sorting, search, permissions and access sessions, viewer page identity, media deletion recovery, `.nomedia` inclusion rules, favorites, profile catalogs, transition completion, and player frame handoff. The full suite passed against the final code-bearing source.

No runtime instrumentation, manual interaction, real-device video playback, FrameTimeline/Perfetto capture, or P50/P90/P95/P99 frame measurement could be collected here. These are pending acceptance items, not passing results. No Pixel 8 Pro or other Android device is connected.

## Pixel 8 Pro acceptance checklist

Install only in a way that preserves the user's existing Gallery data and signing identity. Record device model, Android build, refresh rate, animator scale, APK SHA-256, and recording source for each run.

1. At animator scales 0×, 0.5×, 1×, and 1.5×, repeat bottom navigation, folder enter/return, photo open/return, viewer horizontal swipes, drag cancel/close, system Back, toolbar Back, and edge Back. Inspect first frames, intermediate frames, and settled states for blank/white/black frames, double animation, opacity changes, wrong cover identity, or page stalls.
2. Repeat on Camera, Screenshots, WeChat, `.nomedia`, favorites, custom profiles, search, sorted folders, and large media directories. Add and delete media, revoke and restore access, change sort order, background/resume, and rotate/recreate the Activity. Check counts, order, cover updates, and return thumbnails.
3. Test photo/video mixed browsing and video play/pause, slow first frame, playback error/retry, and exit. Confirm the correct preview remains until the first decoded frame and no overlay remains afterward.
4. Repeat horizontal browsing for 20 images and rapid reverse swipes; perform 10 dismiss cancels and 10 confirmed dismisses; perform 10 each of system, toolbar, and edge Back; test play/pause five times and slow first frame five times.
5. Capture Perfetto or FrameMetrics for first root content, Camera first batch and full scan, list refresh, and transitions. Include refresh rate and compute P50/P90/P95/P99 frame time, frame-budget misses, longest consecutive late-frame run, and relevant GC/main-thread stalls.

Overall engineering state: `ENGINEERING_COMPLETE / DEVICE_ACCEPTANCE_PENDING`. Do not mark the visual or performance matrix accepted until actual device evidence is attached.
