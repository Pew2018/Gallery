# .nomedia refresh and trailing menu ticks (2026-11-03)

> **历史缺陷复现及修复记录（非当前任务）**：原文的目标分支、执行规则、完成结果和下一步仅适用于当时提交。当前媒体索引/加载/排序/稳定性工程使用 [18 — 现行路线图](../media-engine-v2/18-current-media-pipeline-roadmap.md)、[05 — 实施细则](../media-engine-v2/05-minimal-scan-sort-display-refactor.md)。保留旧数据、旧修复和测试结果作为证据，不把历史 Actions 通过视作新阶段性能/真机验收。

Baseline: feature/oxygenos-ui-animation-consistency at ddd3e292f75fcf881121dc6acbeae86bcd30eaeb. No new branch, PR or main merge.

## Narrowed evidence

* MainActivity.getDirectories routes special visibility filters to loadCategorizedFolders. That path previously combined cached Directory objects and synthetic rows, without scanning current media or discovering uncached descendants. Ordinary refresh fixes did not reach it.
* Context.getNoMediaFoldersSync only queried MediaStore for .nomedia entries. Missing provider records meant missing marker hints; fresh local files/ancestors were not authoritative.
* String.shouldFolderBeVisible checked an own-directory marker from disk only with broad storage access, but checked ancestor markers directly. The asymmetric test could miss an accessible own marker in scoped mode. Marker checks used exists(), treating a directory named .nomedia as a marker file.
* MediaFetcher could translate null listFiles into a successful empty result in scoped mode. A .nomedia folder cannot rely on stale index rows as a complete substitute for actual enumeration.
* TiebaMenuItemView retained a 24dp boxed checkbox in 28dp/8dp InsetDrawable gutters after the ripple correction. The row did not need a separately clickable checkbox target.

## Changes

NoMediaSnapshot observes actual marker files and ancestors for one request; no long-lived marker cache or changes to .nomedia files. getNoMediaFoldersSync supplements index hints with known/previously seen/included paths and supports cancellation. The shared visibility helper observes accessible local marker files independently of manager permission; a named directory is distinct from a marker file. Exact included-folder precedence remains.

CategorizedFolderScanner discovers allowed child directories under known/standard roots, scans current direct media, and rebuilds counts, cover and dates. NoMedia/dot/excluded/protected switches remain separate; the scanner's dot setting is local, not a global preference write. The existing hidden-password entry gate now also covers opting into the .nomedia category. Protected ancestors stop traversal; protected cards have private cover/count cleared and are never treated as read grants. Failure retains old cards and reports incomplete refresh; cancellation cannot commit partial results. The bounded executor and request-version gates are reused.

Ordinary .nomedia folder scans skip indexed media/folder metadata and use current disk children with user-edited date overrides. Their first-frame folder checkpoint is not prepared from an index that intentionally omits these files. Null enumeration is an error for .nomedia (and strict unindexed scans), not a confirmed empty folder. Accessible indexed media in other scoped directories are retained on a failed optional disk supplement, with read warnings propagated to partial-failure reporting. No blind media reindex and no marker deletion/rename.

Favorites overflow keeps native CheckBox/MenuItem binding and the existing whole-row press selector. Only the two favorites status controls use the existing bare-tick selector in a 24dp slot plus 16dp start margin; no drawable Insets, no child ripple, independent checked state and disabled tint. Menu anchor and 280dp maximum-width rule remain.

## Verification

NoMediaCategoryRefreshTest uses real .nomedia marker files and new JPGs with an empty/stale provider, checks updated card counts/cover and new children, deletion to confirmed empty, ancestor inheritance, marker add/remove, a directory named .nomedia, independent category flags/inclusion, protected-ancestor clearing, failed enumeration fallback and cancellation. UiFollowupTest checks native menu binding, checked drawable state, layout margins, no child background and native Canvas tick/empty pixels without a box outline. Existing full FOSS Release suite remains enabled.

Local Gradle cannot fetch its distribution in this environment; CI is the runtime validation. No physical Android device/emulator is available. Final APK still needs actual .nomedia folder refresh, permission denial, protected category entry, scrolling/return state and menu visual acceptance on-device. Do not equate simulated provider/filesystem/layout tests with a real media library or frame-perfect visual validation.
