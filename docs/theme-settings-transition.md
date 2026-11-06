# Theme settings navigation transition

Baseline: remote `refactor/media-pipeline-stability` at `fbe2e8641702803c4065711be3d74f13f9e79c7e`.

## Cause and scope

Settings categories used a viewport snapshot transition (180 ms, 8 dp, cubic 0.4/0/0.2/1),
while theme/icon settings launched another Activity with the platform window animation.
More colors reused the viewport transition but changed its toolbar title synchronously,
and restored scroll before the rebuilt page's layout.

## Implementation

- `ClassicSettingsPageTransition` remains the sole animation implementation. Its direction
  function accounts for forward/back and RTL, including a destination not yet attached.
  It accepts an owned viewport snapshot and waits for target pre-draw. Detachment,
  pause, destruction, replacement and normal completion restore alpha/X and remove
  overlay, pre-draw/attach listeners, animator and bitmap. Easing comes from existing
  `OxygenClassicMotion.easing()`.
- `ClassicSettingsActivityTransition` transfers only viewport pixels and source title,
  with a destination/session key and five-second expiry. No Activity/View is retained
  in the pending handoff. Ownership is consumed once or disposed on replacement/expiry.
- `SettingsActivity` guards repeated launches, cancels prior motion, captures the current
  viewport, and launches theme/icon settings with zero window animation. Settings'
  own root return-to-gallery animation is unchanged. The session survives saved state.
- `OxygenThemeActivity` suppresses its open/close window animation and consumes an entry
  frame only on a fresh explicit launch. Back dispatcher and toolbar use the same return
  handoff. More colors retains internal navigation and restores scroll at layout.
- Only list content moves; target page is opaque and the outgoing viewport fades above
  it. Toolbar/system bars stay fixed. Source title is retained until the same 78% commit
  threshold used by categories, then the target title is committed. No toolbar or
  system-bar bitmap is transferred. Appearance and icon business logic is untouched.
- Recreation does not stage navigation. A settings return that needs resource recreation
  leaves the explicit return handoff for the new settings instance to consume. Ordinary
  theme recreation restores saved subpage/scroll without replaying an entry frame.
- Animation-disabled, absent/expired snapshot and capture-failure paths show the target
  immediately. Process death intentionally discards transient frames.

## Regression coverage and validation boundaries

`ClassicSettingsActivityTransitionTest` checks source snapshot ownership, forward/back
and RTL signs, title commit, single consumption, animation-disabled receiving/staging,
replacement, expiry, interrupted run and pre-draw, detachment and normalized Views.
`SettingsPageMotionTest` checks both real settings launch listeners, duplicate launch
prevention and session extras; more-colors tests assert both directions, RTL, disabled
animations, title synchronization and cancellation. Existing category motion and
snapshot alpha/source pixel tests remain in place.

These are deterministic Robolectric state/source-pixel assertions. They do not prove
composited Activity frames or gesture performance on a device. Local Gradle execution
was blocked before compilation by an unreachable Gradle distribution download; the
commit's Actions workflow runs the complete FOSS Release suite then arm64 Release build.

Device acceptance is still required: theme -> more colors -> back -> settings; icon ->
settings; rapid enter/back including interruption before first target draw; animations
off; RTL; light/dark recreation on both main/more-colors pages; system back gesture
completion/cancellation; no white flash, outgoing title residue or shifted content.
