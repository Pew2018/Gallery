# Phase A — Dynamic Oxygen Classic accent coverage

## Baseline and source findings

- Remote baseline: `185dd236ca41722dc17826cbfa725ec5a46c9c55` (`refactor/media-pipeline-stability`), fetched before this phase.
- The supplied entry atlas `Gallery_UI_Interaction_Entry_Atlas_2026-11-07.md` was not present in the repository or attached scratch files. The checked-in UI atlas and source were used instead.
- `OxygenThemeEngine.snapshot(context, config)` is the runtime source of `rawAccent`, `interactiveAccent`, surfaces and text tokens. The checked-in helpers do not maintain a second accent cache.
- Runtime surfaces inspected: `OxygenToolbarStyle`, `OxygenClassicPageInput`, `OxygenClassicDialog`, `ClassicCommonsHooks`, `OxygenClassicMenus`, `OxygenFastScroller`, `OxygenProgressStyle`, `OxygenClassicControls`, `OxygenSelectionMode`, `GalleryBottomBar`, `PhotoProfileEditActivity`, and `OxygenThemeActivity`.
- These entry points already derive their interactive colors from the current theme snapshot or the Commons primary color synchronized by `OxygenThemeEngine.apply()`. Switch thumb/track colors intentionally use `switchPalette(rawAccent)`; readable text, dialog actions, page selection and fast-scroller handles use surface-aware semantic colors. The FAB keeps the existing raw accent plus contrast-selected foreground.
- The fixed `oxygen_classic_accent` resource remains a legacy XML/theme fallback. The reviewed runtime Dialog and Classic control paths replace it with theme-derived drawables/tints. Neutral surfaces, disabled colors, warning/error colors and the black Viewer surface are intentional semantic exceptions.
- The checked-in tests include `OxygenThemeColorsTest`, `OxygenThemePresetsTest`, `ClassicSettingsControlsTest`, `ClassicToolbarLayoutTest`, `ClassicDialogTest`, and `GalleryBottomBarRippleTest`. No fixed blue literal was found in the reviewed runtime helper paths; preset definitions and the legacy XML fallback are not runtime accent sources.

## Phase outcome

No Kotlin or resource change was justified by this audit. Replacing the current color helpers would duplicate existing behavior and risk breaking the Switch raw-accent exception or user-selected theme semantics. No database, media, scan, sort, delete-recovery, Viewer-neighbor, or photo-profile loading path was touched.

## Verification

- Targeted local command attempted: `./gradlew :app:testFossDebugUnitTest --tests org.fossify.gallery.helpers.OxygenThemeColorsTest --tests org.fossify.gallery.helpers.OxygenThemePresetsTest --tests org.fossify.gallery.helpers.ClassicSettingsControlsTest`.
- Local test execution could not start: the wrapper's Gradle 9.5.1 download from `services.gradle.org` failed with `Network is unreachable`.
- Existing ARM64 FOSS Actions run [#592](https://github.com/Pew2018/Gallery/actions/runs/42254061959) passed for source commit `c6328b54796a51959217622860d28f75c757dfb2`. Current remote `185dd236` adds only verification documentation after that source commit; this is supporting evidence, not a replacement for a phase-specific run.
- Pixel 8 Pro color verification remains **待真机验收**: three presets; custom blue/green/purple; pale, dark and alpha-bearing seeds; light/dark mode; rapid theme changes during ripples and active control animations; open Popup/Dialog recoloring.

## Limits

This phase is a source audit, not a claim that every Android widget on every OEM/API level was inspected at runtime. Static defaults and contrast behavior still need device screenshots and interaction checks. The supplied UI interaction entry atlas was not available in this workspace.
