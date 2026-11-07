# OxygenOS Classic outlined text fields

## Baseline and scope

- Base branch: `refactor/media-pipeline-stability`
- Base remote HEAD used to create this branch: `6a44b51ca525a356f14a229004f2690cfc5b5f67`
- Work branch: `refactor/oxygenos-classic-outlined-inputs`
- Historical prompt SHA `faf38ceabc1d01bf570faa682ca25c9d972cec25` was not used as a checkout baseline.
- The parallel checkout at `/workspace/scratch/dc5b5fd1c84e/Gallery` contained two unrelated uncommitted files and was left untouched. This implementation uses a clean checkout.

This phase standardizes editable form fields as Material Components (Material 2) outlined fields. It leaves password/PIN/pattern/biometric UI, the top toolbar's collapsing search mode, and standard read-only text displays outside the migration.

## Input audit

| Entry | Baseline structure | Change / disposition |
|---|---|---|
| Photo profile create/edit name | Plain `EditText` with a tinted platform underline and a separate label | Wrapped in `TextInputLayout` + `TextInputEditText`, preserving `photo_profile_edit_name`; outline hint is `Enter profile name`, with the existing external label retained and duplicate floating hint disabled. |
| Photo profile rename | Programmatic plain `EditText`, previously styled as an underline | Uses `TextInputLayout` + `TextInputEditText`, prefilled value and end selection retained, with inline component error state. |
| Custom theme HEX value | Programmatic `EditText` with direct accent `backgroundTintList` | Uses an outlined `TextInputLayout`; HEX preview, parsing, all-caps filtering, select-all behavior and existing inline accessible error text are retained. Invalid values change the outline to semantic error red. |
| Folder picker search | Commons `MySearchMenu` is a `MyAppBarLayout` with `searchBarContainer` → `toolbarContainer` → leading icon, search `EditText`, and an internal `MaterialToolbar`; the picker's 48dp overflow control is a sibling in `dialog_directory_picker.xml`. `setupMenu()` posts its focus listener. | Kept Commons search and its EditText instance. The inner search-control container receives one 4dp outlined `GradientDrawable`; the picker overflow action remains outside the outline. The Gallery focus listener is posted after Commons' listener, wraps it, and is installed once so opening/closing/search callbacks remain intact. |
| `dialog_create_new_folder.xml` | Two Commons `MyTextInputLayout` fields | Preserved. The path remains read-only/selectable; the name remains editable. Dialog styling now delegates to the shared outline helper. |
| `dialog_custom_aspect_ratio.xml` | Two outlined numeric fields, 100dp each | Preserved compact paired geometry and validation. Shared radius, focus/idle stroke and field-background ownership now apply. |
| `dialog_export_favorites.xml` | One outlined filename field | Preserved dynamic extension hint and filename behavior. |
| `dialog_export_settings.xml` | Outlined read-only path plus filename | Preserved path read-only behavior and filename hint. |
| `dialog_resize_image.xml` | Two outlined numeric fields, 100dp each | Preserved integer and aspect-ratio behavior. |
| `dialog_resize_image_with_path.xml` | Outlined numeric, path, filename and extension fields | Preserved numeric validation and read-only path style. |
| `dialog_resize_multiple_images.xml` | Outlined percentage field with helper/error text | Preserved suffix, helper/error text and resize-factor validation. |
| `dialog_save_as.xml` | Outlined path, filename and extension fields | Preserved read-only path and file operation semantics. |
| `dialog_slideshow.xml` | Outlined interval field | Preserved numeric input, interval label and validation. |
| Collapsing toolbar search in Main/Media/Search activities | Commons toolbar search | Excluded from form migration; it is a distinct compact toolbar interaction. The dialog-wide styling excludes its search EditText. |
| `SettingsActivity.setupRefreshDiagnostics()` dynamic field | No matching current implementation found in the full Gallery Kotlin/XML search. | No migration; the previously removed diagnostics flow was not restored. |
| Widget configure tint, switch/slider drawables, shortcut drawable layers | Search terms matched non-input controls. | Excluded as unrelated visual tint/drawable code. |

## Shared implementation

`OxygenClassicPageInput` now styles `TextInputLayout` with Material's public outlined mode: 4dp corners, 1dp idle and 2dp focused strokes, semantic neutral/accent/disabled colors, readable hint/text colors, cursor tint and 56dp minimum field height. Dialogs resolve tokens against the dialog surface. TextInputLayout owns its child EditText background; `OxygenClassicDialog.apply()` no longer places the platform underline beneath it. Plain EditTexts continue through the legacy path only when they are not children of an outlined TextInputLayout. `ClassicSettingsText` follows the same distinction.

## Tests

Added/updated Robolectric coverage in `PhotoProfileEditUiTest`, `ThemeDialogTest`, and `ClassicDialogLayoutRegressionTest`. It checks the preserved binding ID, page field hint/height, outlined mode, stroke widths, absence of the child EditText background, dynamic HEX validation and all nine existing form layouts.

Local command attempted:

```text
./gradlew :app:compileFossReleaseKotlin :app:processFossReleaseResources --stacktrace
```

It did not start because Gradle 9.5.1 was not cached and the environment could not reach `services.gradle.org` (`Network is unreachable`). `git diff --check` passed before the Gradle invocation. Robolectric tests and device-only rendering/IME behavior remain unverified locally until CI can run.

First remote run (`42381146294`) reached `:app:compileFossReleaseKotlin` and found two Kotlin errors: the dynamic fields used inaccessible synthetic property `hintEnabled`. Both call sites now use the public `setHintEnabled(false)` API. The corrected source is being committed and the workflow rerun; the first run did not reach unit tests or APK assembly.

## Delivery tracking

- Commit SHA: `c8089b98e3e693b0ade91e2f462315ea29684b25` (source tree matches local commit `0101f73d8ef354ffe2eca5a81d5f760d6ae22016`)
- GitHub Actions run: [42381146294](https://github.com/Pew2018/Gallery/actions/runs/42381146294) — failed at Kotlin compilation; correction in progress
- ARM64 FOSS Release APK artifact: pending
- Pixel 8 Pro verification: pending. Validate light/dark themes, bright and dark custom accents, 1.5x font, portrait/landscape, both navigation modes, Chinese/English IMEs, focus/selection retention, and folder search filtering.
