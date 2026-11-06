# Delete confirmation across app navigation

Baseline: `85fe25f68b21b14d14f09cd2be4da1748f1bc270`, branch
`refactor/media-pipeline-stability`.

## Root cause

The two Config properties already delegated to the in-memory DeleteSessionState,
but MainActivity cleared them on every fresh Activity creation and teardown.
MainActivity and MediaActivity also cleared them using the hidden-file timeout;
MediaActivity cleared them when destroying the All Photos page. Root tab navigation
replaces Activities, so returning to a custom profile lost the remembered decision.

## Change and session boundary

- Remove deletion-choice resets from both pages and their hidden-file timers.
- Register DeleteSessionLifecycle in the existing Application lifecycle callbacks.
- Track created/destroyed Activities, including stopped pages, independently of
  the existing foreground authentication revocation.
- Keep both choices through root tab replacement, profile changes, viewer return,
  Home/resume, external permission prompts, and configuration recreation.
- Reset both choices when the last Activity is destroyed without a configuration
  change, and at the first Activity creation in a new session. Process death also
  clears the in-memory state. No preferences are written.
- A Home press alone is backgrounding, not an explicit end of the session. A cold
  launch after process death, or reopening after all pages finish, asks again.

GalleryBottomBar.open starts the target before finishing the outgoing root (first
draw or fallback). The old root's destruction therefore leaves the new Activity
in the session. All existing grid and viewer deletion consumers still read the
same Config properties and retain their per-operation recycle-bin choice.

## Verification

DeleteSessionLifecycleTest covers root/profile replacement, viewer return while
remembering move-to-bin, configuration recreation in both callback orders, last
page exit/reopen while the process survives, initial launch, and unrelated teardown.

Local Gradle execution was attempted but could not download Gradle 9.5.1 because
the environment could not reach services.gradle.org. The branch's existing ARM64
FOSS workflow runs the complete Release unit suite before assembling the APK.

Pixel 8 Pro acceptance: remember a deletion decision in a profile, switch to
Albums and Favorites, return to that profile and delete again; repeat across grid
and viewer, with and without skip-recycle-bin. Verify no renewed prompt and the
correct recycle-bin destination. Press Home and resume; choices remain. Exit all
Gallery pages and reopen; the prompt returns. Repeat after rotation/theme recreation.
