# Phase B — Search, text inputs and IME motion

## Baseline and implementation

- Baseline: remote `a6fa1b04e8ae1a04d5ee037f520b18352929dbb6`, following Phase A.
- The three `MySearchMenu` hosts and `PickDirectoryDialog` retain Commons 7.1.3 as the owner of search focus, text change delivery, keyboard requests and result filtering.
- `OxygenToolbarStyle` now animates only the existing search field's measured width/alpha and the title/action alpha. The field is not replaced; search callbacks remain immediate. It uses a 160ms value from `OxygenClassicMotion`, and cancels an in-flight animator on a reversed open/close request or when the toolbar detaches.
- Width is interpolated only when the toolbar has been measured and system animations are enabled. The final states remain the existing collapsed `1dp` field and expanded `MATCH_PARENT` field; no delayed query or adapter publication was added.
- Ordinary Dialog inputs continue through `OxygenClassicDialog.styleInput()`; page input colors continue through `OxygenClassicPageInput`. Input validation and keyboard ownership were not changed.
- `PhotoProfileEditActivity` already keeps its source FAB visible and follows IME insets via `PhotoProfileFabImePosition`/`WindowInsetsAnimationCompat`. This phase leaves that behavior intact and does not add a second keyboard-height compensation.

## Files changed

- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicMotion.kt`
- `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenToolbarStyle.kt`
- `app/src/test/kotlin/org/fossify/gallery/helpers/ClassicToolbarLayoutTest.kt`
- `docs/ui/oxygenos-classic-refinement/phase-search-ime-input-motion.md`

No Picker selection, filtering, permissions, source configuration, media loading, sorting, Viewer, or database code was changed.

## Tests and build

- Added `searchExpansionPreservesTheExistingInputAndRestoresToolbarState`. It opens and closes the real Commons search view with animator scale set to zero, confirms the EditText identity and immediate query value, and checks the final action/title/width/focus state.
- The Robolectric test drains the main looper after `MySearchMenu.setupMenu()` because Commons posts its search listeners; it also restores the previous animator scale in `finally`.
- Existing Photo Profile FAB/insets and dialog-input tests remain part of the Release unit test suite.
- Local Gradle verification is blocked before task execution: the wrapper requires Gradle 9.5.1 from `services.gradle.org`, which this environment cannot reach. The phase must not be described as locally test-passing.
- Phase implementation commit: `1130bd768597dd932ac7bc422239a3f351d3b7d0`.
- Test-harness correction commit: `bfcf8a68770396989b0b3f16ac55702577c1b6ad`.
- GitHub Actions run [#595](https://github.com/Pew2018/Gallery/actions/runs/42337154798) passed: full FOSS Release unit test suite, ARM64 Release assembly, APK validation, and artifact upload. ARM64 APK artifact: [gallery-foss-arm64-v8a-release](https://github.com/Pew2018/Gallery/actions/runs/42337154798/artifacts/13559807074).
- Pixel 8 Pro input/IME checks are **待真机验收**: first-key entry during expansion; quick open/close/reversal; system Back with keyboard visible; Gboard geometry; Dialog fields; FAB reachability; rotation and navigation modes.

## Limits

The 160ms duration is an engineering starting value, not a measured OxygenOS ROM duration. The system owns the IME animation; no fixed delay synchronizes it with the search transition.

## Search and IME lifecycle follow-up — 2026-11-07

Audited again on `refactor/media-pipeline-stability` at `5100d4f574d67cc754ab7c69c8a41695184150f0`. Commons `MySearchMenu` 7.1.3 remains responsible for inline search focus and text delivery; Gallery keeps the existing EditText instances and does not delay query callbacks.

- Main and media list refresh is disabled for the entire expanded-search state, including an empty query, and returns only after the search is closed and its query is cleared. `SearchRefreshPolicyTest` covers the open/closed and empty/non-empty transitions.
- Root-tab navigation closes an expanded inline search before leaving the current host, clearing focus and the IME through the existing Commons close callback.
- The dedicated SearchActivity explicitly requests the IME after Commons focus, clears focus/hides IME when it stops, and preserves the adapter's current scroll anchor during result publication.
- PickDirectoryDialog clears the Commons search field focus and hides the IME on dismissal, including when dismissed while search is active; its source-selection cancellation callback remains unchanged.
- Insets audit: SearchActivity applies IME/system-bar bottom insets to the grid through Commons `setupEdgeToEdge`; the directory picker remeasures its bounded list viewport on IME visibility changes through `ClassicDialogPickerLayout`.
- Local focused test execution was blocked before Gradle configuration because wrapper distribution `gradle-9.5.1` could not be downloaded in this environment. The branch Actions build remains the required compile and test verification.
