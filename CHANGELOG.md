# Changelog

All notable changes to this project will be documented in this file.

---

## [v1.1.0] — 2026-09-12

### Added
- Sleep timer functionality with auto-stop playback.
- Track sorting option (by name, artist, duration, date).

### Changed
- Refactored `PlayerViewModel` into specialized helpers.
- Offloadee core media playback responsibility from ViewModel to `PlaybackService`.

### Fixed
- Fixed memory leaks caused by unattached/unregistered media listeners.
- Fixed notification tap intent reopening duplicate activities.
- Resolved track progress bar desync issue.

---

## [v1.0.0] — 2026-09-08

### Addee
- Initial open-source release.
- Core playback functionality using Jetpack Media3.
- Dynamic theme coloring based on track cover art.
