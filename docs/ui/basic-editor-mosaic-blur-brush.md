# Basic Editor DRAW, MOSAIC, and BLUR brushes

## Scope and implementation status

This change extends the existing XML Basic Editor drawing panel. The primary Filter / Crop & Rotate / Draw navigation remains unchanged. Brush modes share the existing size control and a single ordered stroke history. No editor framework or image-processing dependency is added.

## UI

The draw row remains 64dp tall. It contains the existing seek bar, color brush, 48dp mosaic selector, 48dp blur selector, and undo control. The two new previews are drawn in `EditorBrushButton`: a stable pixel-block sample for Mosaic and soft nested grayscale circles for Blur. Selection is exclusive and outlined using the editor's readable user accent. The control row allocates remaining width to the seek bar, so the four fixed 48dp controls retain their touch targets on narrow layouts.

## Data and gestures

`EditorBrushMode` names DRAW, MOSAIC, and BLUR explicitly. Each committed `EditorStroke` captures a defensive `Path` copy, mode, width, and color/effect parameters in one ordered list. Brush changes only affect future paths. A completed single-finger gesture is one history item. Pointer-down cancels the current gesture; ACTION_CANCEL discards it. Undo removes exactly the most recent stroke, regardless of mode.

Stroke coordinates use the fitted, orientation-corrected preview bitmap's pixels. The same ordered paths are scaled independently in X and Y when rendered onto a full-resolution decoded source bitmap. This preserves locations and proportions when export dimensions differ from the preview. The source is not painted white first, so PNG transparency is retained.

## Compositing

The renderer starts with a mutable copy of its source and replays strokes in user order:

- DRAW uses an anti-aliased, round-capped colored stroke.
- MOSAIC snapshots the already-composited result at a fixed image-anchored grid, downsamples with nearest-neighbor sampling, then uses that snapshot as a shader along the round path. Later strokes therefore process earlier draw and effect marks.
- BLUR snapshots the already-composited image at one-quarter dimensions and applies three horizontal/vertical box-blur convolutions as a bounded-memory Gaussian approximation. The filtered snapshot is sampled only under the local round stroke. It works on API 26+ and does not blur editor controls or the whole View.

The preview result is cached after a completed stroke. During a gesture only the active stroke is replayed over that cached preview. Undo invalidates and rebuilds the preview. Export replays the same operations at the decoded original's dimensions, rather than scaling a finished preview bitmap.

## Save and share paths

DRAW-panel save, Save As, overwrite, and share decode the original with Glide at its original dimensions, render the drawing history on that bitmap, then continue through the existing compression, destination, and EXIF-writing paths. Existing crop, filter, output-format, and file-path behavior is unchanged. Rendering errors and `OutOfMemoryError` are reported through the existing editor error handling.

## Automated coverage

`EditorDrawCanvasTest` checks localized mosaic changes, untouched pixels, export dimensions, preview-to-output scaling, mixed mode replay, reverse-order undo, and empty-history safety. Build and test results are recorded with the delivery.

## Device acceptance checklist

- [ ] Pixel 8 Pro / Android 16: no white flash when opening the editor.
- [ ] Draw row shows all three brushes, with 48dp selector targets and no overlap at 320dp, 360dp, 393dp, or 411dp.
- [ ] Accent selection and Ripple remain visible; color picker still selects DRAW and remembers its color.
- [ ] Test taps, slow and fast strokes, tool switching, multi-touch cancellation, and repeated undo.
- [ ] Compare DRAW → MOSAIC → BLUR overlap and preview against the saved and shared output.
- [ ] Check portrait EXIF-oriented and transparent PNG images, Save As, overwrite, and sharing.
- [ ] Exercise a high-resolution image while watching responsiveness and memory.
- [ ] Rotate or resize the editor and verify path alignment.

## Limits and references

The preview still uses the existing Glide fit-center decode and therefore is intentionally lower resolution than the source. Export is full-resolution but creates a source copy plus a working bitmap; very large images can still exceed the device's available heap, in which case the operation reports an out-of-memory error. Blur is an efficient three-box Gaussian approximation, not an exact floating-point Gaussian kernel. Automated layout checks do not replace on-device width and gesture testing.

Reference review: `robeshell/compose-image-editor` describes a downsampled BitmapShader mosaic approach and identifies its project license as MIT. `T8RIN/ImageToolbox` identifies its license as Apache-2.0 and includes pixelation and privacy-blur tools. No source code was copied from either project, so their attribution files are not added and no third-party dependency is introduced.
