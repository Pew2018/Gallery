# Phase D — Shared classic Dialog and Picker window motion

## Baseline and implementation

- Phase baseline: remote HEAD `6dbdece0d1de312b46a396fa01d575542a94070b`, after Phase C passed.
- Implementation commit `62076762d2afc4434e93746f16315ba010bb4f88` added a single public Window animation style and called it from `OxygenClassicDialog.prepareWindow()`, before each shared AlertDialog window is attached.
- Gallery and Commons dialogs that use the existing `ClassicCommonsHooks.beforeDialogShow()` path share this presentation contract, including directory/media Pickers. Existing dialog layout dimensions, Insets, IME adjustment, input controls, click listeners, cancel listeners, and data callbacks are not replaced.
- Enter motion: 170ms alpha fade with a 6dp downward-to-rest movement. Exit: 130ms fade with a 6dp return movement. These are engineering starting values based on the static UI guide, not measured OEM timings.
- The durations and distance are XML resource tokens in `oxygenos_classic.xml`, because Android WindowManager resolves window-animation resources. No view-level Animator competes with the Window transition and the system animation scale remains authoritative.
- AppCompat PopupMenu/PopupWindow paths keep their existing native presentation, menu anchors and Insets; this stage does not reflect into private popup state or add a second animation layer. System permission/SAF/biometric windows are outside Gallery ownership.
- No media, query, sorting, authorization, password result, or database paths changed.

## Files changed

- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicDialog.kt`
- `app/src/main/res/values/oxygenos_classic.xml`
- `app/src/main/res/anim/oxygen_classic_dialog_enter.xml`
- `app/src/main/res/anim/oxygen_classic_dialog_exit.xml`
- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicDialogTest.kt`
- This phase document.

## Verification

- Added a stateful Robolectric check that runs the shared prepare-window path, verifies the public window-animation style is assigned, confirms `FLAG_SECURE` is retained, shows and dismisses the dialog, and observes exactly one dismissal callback.
- The first complete Actions run for the code change, [#603](https://github.com/Pew2018/Gallery/actions/runs/42341992732), passed the full FOSS Release unit-test suite, ARM64 Release assembly, package/signature/ABI checks, and APK upload.
- This follow-up centralizes timing values as XML tokens and is awaiting its own full CI result.
- No Pixel 8 Pro/Android 16 visual execution is available: **待真机验收** for Picker scrolling and IME, rapid open/close, cancel/back/outside-tap, security confirmation and dialog motion direction.

## Limits

The system owns Window animation cancellation and scaling; this phase does not implement predictive-back progress. AppCompat Popup behavior is intentionally retained where a shared public Window contract does not apply.
