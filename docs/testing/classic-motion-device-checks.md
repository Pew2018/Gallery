# Classic motion: device visual acceptance

Automated Robolectric tests validate state, layout, draw acknowledgement and callback
cancellation. They do not validate RippleDrawable GPU output, image decoding, SurfaceFlinger
window composition, or frame-perfect MainActivity/MediaActivity navigation.

## Setup

Use an arm64 FOSS Release APK from the exact commit under test. Record device model,
Android version, screen refresh rate, theme, animation scales, media count and APK SHA.
Enable Show taps. Record at the device's native frame rate; inspect individual frames.
Use a light theme, a dark theme and a custom toolbar/accent theme. Include an empty
profile, a large multi-folder profile, Favorites, and a profile with a protected source.
Never distribute recordings containing private media.

## Navigation matrix

For each row below, test a force-stopped cold start, a warmed cache, rapid repeated taps
(including another tab during the transition), and a large media library. Repeat with
system animator/transition/window animation scales at 0 and at their normal values.

| Source | Destination |
| --- | --- |
| Albums (folders) | Photos |
| Favorites | Photos |
| Photos | Albums (folders) |
| Photos | Favorites |
| Albums (all files) | Photos |
| Photos | Albums (all files) |

Before each all-files case, select that mode in Albums so lastMainViewAll is correct.
For cold cases, force-stop and relaunch; to guarantee a disk-cache miss, use a newly
created profile with different sources rather than deleting user data. Repeat each
case at least 5 times. Capture the transition through the first actual thumbnail.

Observe frame by frame:

- Page surface, toolbar, system bars and bottom selection match the destination theme.
- No full-screen white/black/accent flash or transparent window frame.
- A slow load may legitimately show a themed opaque loading page. Count those frames
  separately from wrong-color flashes; measure tap-to-first-layout and tap-to-thumbnail.
- A laid-out thumbnail placeholder is not proof of decoded image readiness.
- Protected media never appear before authorization or after access is revoked.
- Fresh source/profile/permission scope never displays an earlier grid.
- Same-scope refresh retains its grid/scroll anchor and updates without clearing.
- The source window is bounded in lifetime even if destination startup/query fails.

## Ripple and controls

Tap and release each bottom item normally, hold then release, drag out to cancel,
and rapidly tap two different items. Inspect native pressed expansion and release
fade separately. Confirm one navigation, no retained pressed ink, and correct final
selection. An outgoing Activity remaining alive alone does not prove it was drawn.
Repeat while changing theme. This task leaves RippleDrawable's native timing intact.

In a multi-choice dialog, toggle CheckedTextView on/off, reverse within 140ms, and
move focus/press during selection. In the toolbar actions dialog switch tabs during
checkbox selection and tap a checkbox during a reorder. Check white check ink,
unchanged box geometry, row-aligned ripple, and no intermediate final state.

## Sources, settings and loading

Add several folder/favorite sources. Delete adjacent and nonadjacent rows rapidly
while status checks are pending; navigate away during deletion. Retained rows must
not detach or flash, removed rows fade then neighbors settle, and deleted rows never
return after the worker completes. Repeat with animations disabled.

Enter/return settings pages, reverse before the first phase ends, and destroy/recreate
the Activity mid-animation. Final alpha must be 1 and translation 0; no stale page
callback may replace the latest page.

Exercise cached loads under 120ms, slow loads over 120ms, refresh while loading,
and navigation/destruction during a pending show or hide. Root pages use the header
strip; the central indicator is reserved for an initial non-root folder/picker load.
They must not overlap. Visible indicators have a 180ms minimum while their task ends;
content publication is never delayed for the indicator. Animation scales at 0 remove
these visual delays. Log errors and record any pending loader after leaving the page.

## Results

For every matrix cell record APK commit, cache state, media count, theme, animation
scales, tap-to-layout, tap-to-thumbnail, wrong-color frame count, loading frame count,
Ripple truncation, privacy result and recording filename. Device visual acceptance
remains pending until those recordings have been reviewed.
