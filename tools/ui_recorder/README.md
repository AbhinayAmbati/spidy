# UI recorder (privacy-safe)

PC-side tool that walks an Android app over adb, read-only, and writes training rows:
`goal`, serialized screen, action, next serialized screen. The screen format matches
`core/screen/ScreenSerializer.kt` (numbered, visual order, about 350 tokens max).

## Hard rules (enforced in code and tests)
- No screenshots or images: no `screencap`, no image code; the adb wrapper only runs a fixed list of commands.
- No network: stdlib only, a test fails if a network module is imported.
- Redaction happens in memory in `parse_dump.py`, before anything is written. Default deny: a label survives only if it is on `allowlist.json` (Like, Send, Search, tab names...). Other interactive text becomes `<item>`; other plain text, typed text, hints and passwords are dropped.
- `scanner.py` rejects any row with phone numbers, emails, URLs, @handles, long digit runs, long text, or labels not on the allowlist. Rejected rows are counted, never written.
- Output goes to `tools/ui_recorder/out/` (git-ignored). Never commit it.
- Exploration never sends, posts, likes, follows, comments, deletes, pays, types, or changes settings: only launch, scroll, back, and taps on navigation labels (`nav_tap`), after a `deny_tap` check.
- Secure/blocked/login screens are logged as `{"blocked": true, "reason": ...}` with no content.

## Run
```
python tools/ui_recorder/explore.py settings google telegram whatsapp instagram twitter
python tools/ui_recorder/scanner.py          # audit out/ (exit 1 on any finding)
python -m unittest discover -s tools/ui_recorder/tests
```
Taps need "USB debugging (Security settings)" enabled on Xiaomi phones; without it only scroll-free launch screens are recorded.
