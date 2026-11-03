# Hero Train Video Asset Guidelines

This directory (`frontend/public/media/`) hosts optional local video assets for the **RAILFLOW ENGINE** scroll-scrubbed hero landing.

If no video is present, the landing page automatically falls back to a procedural, high-performance dark-theme SVG railway track animation with zero layout shifts or errors.

---

## Video Specifications

* **Content**: 10–15 second clip of a train arriving at a platform or traveling along a corridor.
* **Resolution**: 1080p maximum (1920x1080 or responsive aspect ratio).
* **Audio**: None (must be muted / audio-stripped).
* **File Size**: Under 20 MB.
* **Target Filename**: `hero-train.mp4`

---

## Recommended Encoding (FFmpeg)

For smooth frame-by-frame scrub response on scroll wheel and touch interactions, every frame should be an intra-frame (`-g 1` keyframe interval):

```bash
ffmpeg -i input.mp4 -vf "scale=1920:-2" -an -c:v libx264 -g 1 -crf 23 -pix_fmt yuv420p -movflags +faststart hero-train.mp4
```

### Parameter Breakdown:
* `-vf "scale=1920:-2"`: Scales width to 1920px while preserving aspect ratio.
* `-an`: Strips audio tracks completely.
* `-c:v libx264`: Standard H.264 video codec for universal browser compatibility.
* `-g 1`: Sets Group of Pictures (GOP) size to 1 (all I-frames / keyframes), allowing instant seeking without stutter.
* `-crf 23`: High visual quality with compact compression.
* `-pix_fmt yuv420p`: Standard 8-bit color format supported by all browser decoders.
* `-movflags +faststart`: Places the MP4 `moov` atom at the start of the file for instant playback start.

---

## Git Policy

> [!WARNING]
> Do NOT commit video files over 20 MB to version control. Large media files are excluded by `.gitignore` (`*.mp4`). Place `hero-train.mp4` directly in this directory for local testing.

