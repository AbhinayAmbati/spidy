# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Fully on-device inference with the Spidey model (fine-tuned from Cactus Needle 3), downloaded on first launch with resume,
  SHA-256 verification and a metered-network check; JNI bridge to the vendor engine.
- Whistle speech-to-text model download and status in Settings.
- Redesigned chat-style interface: home, task, history, routines and settings screens.
- GitHub Actions workflow that builds an installable APK (`Release APK`).
- A feasibility spike (`tools/spidey_eval/`) for measuring accuracy on a phone.

### Changed
- The agent runtime takes ports (model source, screen, journal, clock) and the loop guards
  live in `LoopGuard`, so the loop is unit-testable with fakes.
- The model is offered at most five tools per turn.
- Removed the provider and model selection screens from the UI.

### Fixed
- Tool calls skipped by a stopped batch are answered, so providers stop rejecting the next
  request.
- `read_screen` returns the screen listing.
- Stop during a model call ends the task as cancelled, not failed.
- Taps on key-targeted elements are checked against the element's real label, whole words only.
- Routine recording keeps only successful actions; pause no longer races.
- Provider error bodies are surfaced; a Gemini 400 is no longer reported as a bad key and its
  key is sent in a header, not the URL.
- The status overlay and its collector no longer leak on a second service start.
- Opening Notification access no longer crashes when the settings page is missing.
- "Open X" tasks complete once the app opens, and a model that always gives the same answer is
  no longer retried with nudges it cannot see.
