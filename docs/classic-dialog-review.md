# Classic dialog coverage and review

Base: `c4032b0c5c97dec3969308085edb749083cba038`; Commons `7.1.3` (upstream tag source `227e7d3cdea2343679eb0863bb597fc7a9200b4d`).

## Adaptation choice

Resource overlays alone cannot win over Commons setupDialogStuff's late Window background or repair Breadcrumbs Kotlin coordinates. Copying FilePickerDialog/PropertiesDialog would duplicate SAF, EXIF and security business code. Instead AGP's supported ASM instrumentation API adds a presentation hook at both show sites in that one fixed Commons method, and five narrow Breadcrumbs lifecycle/geometry methods. A version guard and exact method/show-site counts stop an incompatible dependency update. No reflection, replacement library, runtime class loading or private-field access is used. ASM 9.7.1 is build-time only. All existing business callbacks remain in Commons.

The first pre-draw applies the final neutral surface after Commons background/font/callback passes. Global-layout invalidation handles newly appended EXIF rows; per-view palette tags prevent repeated drawable allocation and ripple resets. The observer removes itself on detach. Gallery direct Builder.show sites use the same hook; existing explicit classic calls remain idempotent.

## Screenshot coverage

| Area | Entry / implementation | Layout / shared adapter | Risks requiring installation |
|---|---|---|---|
| Loading priority, rotation, grid 1–19, all other radio lists | SettingsActivity, MainActivity, MediaActivity, SetWallpaperActivity, SlideshowDialog, thumbnail-spacing selector / Commons RadioGroupDialog | dialog_radio_group, radio_button / ClassicDialogScrollView + OxygenClassicDialog | font scaling, initial selected-row scroll |
| Date/time | SettingsActivity / Commons ChangeDateTimeFormatDialog | dialog_change_date_time_format / shared frame, radio, checkbox, actions | small screen, cancellation/confirm persistence |
| Properties, single/multiple image/video/folder | ViewPagerActivity, PhotoVideoActivity, MediaAdapter, DirectoryAdapter / Commons PropertiesDialog + BasePropertiesDialog | dialog_properties, item_property / shared frame and asynchronous row styling | long EXIF/path, GPS links, EXIF refresh |
| EXIF and all other confirmations | Commons removeEXIFFromPath(s) + Gallery ConfirmationDialog callers | dialog_message / shared first-draw hook | stacked scrim and return to parent |
| Create folder | MainActivity, MediaActivity, FilePickerDialog / Commons CreateNewFolderDialog | dialog_create_new_folder / existing MyTextInputLayout API with classic underline | IME, SAF/SD/OTG, long readonly path |
| Folder and file pickers | All FilePickerDialog callers and PickDirectoryDialog | dialog_filepicker, dialog_directory_picker / ClassicDialogPickerLayout, shared FAB styling | loading/empty/favorites/hidden, permission checks |
| Breadcrumb ripple | All Commons Breadcrumbs instances, including FilePickerDialog | item_breadcrumb + breadcrumb_button_background + five bounded build hooks | real touch/scroll/RTL recording |

## Confirmed source defects and bounded corrections

* Breadcrumbs.onLayout passed the HorizontalScrollView parent-coordinate left into a threshold otherwise measured in its children's coordinates. The hook supplies the actual first child left in the items container, including RTL.
* Original root decoration added an accent stroke to a modern button-radius ripple. Resource overlay and per-item runtime drawable use matching 3dp content/mask, low-alpha neutral press colors, and an opaque neutral root for sticky overlap.
* Rebuild removed pressed views without explicit pending-input/ripple cleanup; scrolling also translated pressed sticky children. Only these transitions now cancel pending presses and settle retiring ripples. Ordinary down/up/long press/cancel remains Android's standard bounded ripple behavior.
* The selected-item scroll method lacked an empty-child guard; the build hook safely returns for an empty path.
* These are source-confirmed defects, not proof that every observed touch artifact had one sole cause. Device recording remains required.

## Additional entry coverage

| Gallery dialog | Creation and theme path | Adapter | Dark / long / input review |
|---|---|---|---|
| AllFilesPermissionDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ChangeFileThumbnailStyleDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ChangeFolderThumbnailStyleDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ChangeGroupingDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ChangeSortingDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ChangeViewTypeDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ConfirmDeleteFolderDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| CustomAspectRatioDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| DeleteWithRememberDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ExcludeFolderDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ExportFavoritesDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| FilterMediaDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| GrantAllFilesDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ManageBottomActionsDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ManageExtendedDetailsDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| OtherAspectRatioDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| PickDirectoryDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| PickMediumDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ResizeDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ResizeMultipleImagesDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| ResizeWithPathDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| SaveAsDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| SlideshowDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |
| StoragePermissionRequiredDialog | Commons getAlertDialogBuilder + setupDialogStuff; activity classic overlay / Commons theme | shared final-draw pass | semantic colors reviewed; scroll/IME needs device |

Other shared Commons dialogs: RenameDialog/Items, FolderLockingNoticeDialog, ColorPickerDialog, SecurityDialog and StoragePickerDialog all reach setupDialogStuff. Security/rename tabs now get the same neutral surface and readable accent. The playback-speed BottomSheet keeps its original behavior but uses a small-radius neutral background and bounded 48dp icon targets. Existing custom theme and favorite-password dialogs continue using OxygenClassicDialog.show.

System permission prompts, system BiometricPrompt, Android SAF document picker and external players are outside this adapter. Compose alert functions in Commons are not called by this Gallery XML/View implementation.

## Validation boundary

New ClassicDialogTest covers first-draw ordering, duplicate observer prevention/cleanup, async property styling without ripple replacement, pale-accent contrast/disabled state, dark contrast, root coordinates/empty items, press cleanup/independent masks, confirm versus cancel and bounded long content. Existing favorites/access/scroll/cover tests remain in the same workflow. APK instrumentation validates expected Commons hook structure at build time. JVM helper tests do not constitute device touch/UI tests.

Local Gradle compilation was blocked before Gradle installation by network restrictions; CI is the compilation/test/package authority. No emulator or device UI validation is claimed.

## Global search inventory

Searched pattern: `AlertDialog|MaterialAlertDialogBuilder|getAlertDialogBuilder|setupDialogStuff|RadioGroupDialog|ChangeDateTimeFormatDialog|ConfirmationDialog|PropertiesDialog|CreateNewFolderDialog|FilePickerDialog|PickDirectoryDialog|StoragePickerDialog|Breadcrumbs|RippleDrawable|selectableItemBackground|colorControlHighlight|MyFloatingActionButton`

* `app/src/main/kotlin/org/fossify/gallery/activities/ExcludedFoldersActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/HiddenFoldersActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/MediaActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/OxygenThemeActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/PhotoVideoActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/SetWallpaperActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/SettingsActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/SimpleActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/VideoPlayerActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/ViewPagerActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/activities/WidgetConfigureActivity.kt`
* `app/src/main/kotlin/org/fossify/gallery/adapters/DirectoryAdapter.kt`
* `app/src/main/kotlin/org/fossify/gallery/adapters/MediaAdapter.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/AllFilesPermissionDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ChangeFileThumbnailStyleDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ChangeFolderThumbnailStyleDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ChangeGroupingDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ChangeSortingDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ChangeViewTypeDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ConfirmDeleteFolderDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/CustomAspectRatioDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/DeleteWithRememberDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ExcludeFolderDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ExportFavoritesDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/FilterMediaDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/GrantAllFilesDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ManageBottomActionsDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ManageExtendedDetailsDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/OtherAspectRatioDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/PickDirectoryDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/PickMediumDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ResizeDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ResizeMultipleImagesDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/ResizeWithPathDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/SaveAsDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/SlideshowDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/dialogs/StoragePermissionRequiredDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/extensions/Activity.kt`
* `app/src/main/kotlin/org/fossify/gallery/extensions/FavoritePassword.kt`
* `app/src/main/kotlin/org/fossify/gallery/helpers/BottomNavigationOrderDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/helpers/ClassicCommonsHooks.kt`
* `app/src/main/kotlin/org/fossify/gallery/helpers/GalleryBottomBar.kt`
* `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenClassicDialog.kt`
* `app/src/main/kotlin/org/fossify/gallery/helpers/OxygenToolbarStyle.kt`

## CI correction history

Initial run 41009677924 stopped during Gradle configuration: a compile-only AGP dependency left AsmClassVisitorFactory unavailable to buildSrc at runtime. The first classpath correction tried the public gradle-api artifact; the following run required the single shared full-plugin classpath described below. Review also replaced a Java synthetic property with its explicit TabLayout setter and guarded redundant FLAG_SECURE updates to avoid repeated window traversals.

Run 41009845076 exposed an AGP plugin marker already present with an unknown version on buildSrc's shared classpath. AGP 9.2.1 is now loaded once by buildSrc, and root/app use the unversioned plugin ID from that classpath; no AGP upgrade. A follow-up review identified the original sticky-root algorithm's LTR-only threshold. Its five-method presentation patch now positions the root against the appropriate viewport edge in both LTR and RTL; a regression covers both directions.

Run 41010070679 passed Gradle configuration and resource processing, then found Kotlin's Java-property access to FAB maxImageSize was private. This now calls the public setMaxImageSize API. The same correction includes scrollable new-folder content (preserving the original LinearLayout root and every binding ID) and stronger secondary/disabled text contrast.

Material 1.14.0 source review also confirmed that MaterialAlertDialogBuilder installs InsetDialogOnTouchListener with its original background inset rectangle. A final window-background replacement alone would leave stale outside-touch borders. A bounded hook at Commons getAlertDialogBuilder return now calls Material's four public inset setters with zero; cancellation rules and builder subtype remain unchanged. ClassicDialogTest checks the resulting public drawable padding. Source: https://github.com/material-components/material-components-android/blob/1.14.0/lib/java/com/google/android/material/dialog/MaterialAlertDialogBuilder.java

Run 41010403168 compiled successfully and ran 49 selected tests; 48 passed. The new observer cleanup assertion failed after dismissing before a normal attach/detach frame. Cleanup now also observes ComponentDialog's ON_DESTROY lifecycle (without replacing any onDismiss/onCancel callback), and the test drains the normal main-thread dismissal queue before checking cleanup.

Run 41010873941 passed the full selected test step. Final source review then found selected breadcrumb labels still inherited Commons' raw accent, which is insufficient for pale custom accents. The shared breadcrumb skin now derives a readable accent against its actual white/#303030 surface and applies the same state colors to the chevron. The independent-mask regression also asserts selected-label contrast. The test workflow now prints totals from its actual JUnit XML reports.

Run 41010873941 subsequently completed release assembly, signature/package/ABI validation and APK upload successfully. Its APK is an intermediate result only: the selected-breadcrumb contrast correction is submitted afterward and requires its own final SHA-matched build.


## Dialog startup geometry correction

The follow-up fixes default-position/size exposure before the final classic geometry is ready. Base: 451c76d6b31c88084c0e29900b723eb58f50181c.

* Gallery's classic helper calls public Dialog.create(), prepares width/soft-input/security/surface, installs the draw guard, then calls show(). Appearance remains synchronously available to callers without changing width after show.
* The pinned Commons visitor now inserts beforeDialogShow before each of its two show calls, retaining its original callbacks, keyboard behavior and final style pass. Both before/after hook counts must equal two.
* Gallery's three direct Builder.show call sites are replaced by a create/prepare/show wrapper. The wrapper contains no Builder.show call and therefore cannot instrument itself recursively.
* Ordinary appearance/global-layout passes no longer own width or soft-input mode. Host window layout changes resolve a stable system/cutout-safe width, excluding IME insets, and write it only if it actually changed. Existing gravity, window animation and wrap-content height are retained.
* A per-dialog pre-draw guard cancels an initial draw only while actual surface/layout mutations remain pending. Appearance is idempotent; no fixed delay, alpha hiding or disabled animation is used. Once released, async property rows update normally. Host geometry changes receive a pending-relayout guard too.
* Surface cache identity now includes the actual decor background, so a late Commons replacement is corrected without unconditional first-draw cache clearing.
* Detach/destroy removes drawing, dialog lifecycle and host-layout listeners without replacing any business listener.

ClassicDialogTest adds coverage for pre-show geometry and input-state bits, canceled-then-stable first frames, repeated appearance/global-layout passes, Builder wrapper/listener behavior, host width versus height-only changes, early dismissal, asynchronous EXIF-like rows and width-policy bounds. Existing confirmation, date persistence, column count, accent/dark and favorite/security regressions remain selected by the existing workflow.

These tests exercise framework simulation and ordering, not real WindowManager/compositor entrance animation. Real-device recording remains necessary for final visual confirmation, particularly Android 16, keyboard transitions, rotation/multi-window and rapid repeated opening.
