# UI synth (synthetic UI-selector training data)

Generates `(goal, history, serialized screen) -> next action` rows to fine-tune the small on-device
model for a UI-selector skill. Pure Python stdlib, no network, no third-party services.

## Privacy rules (enforced by tests)
- All text is invented: fictional names (`vocab.py` pools), generic messages and queries. No digits,
  emails, URLs or handles anywhere in goals, screens or answers.
- The generator never reads `tools/ui_recorder/out/` (a test greps the sources). Recorded screens are
  for held-out *evaluation* only; a test fails if any generated screen equals a recorded one.
- Output goes to `tools/ui_synth/out/` (git-ignored). Commit code and tests only.

## Run
```
python tools/ui_synth/generate.py --n 6000 --seed 1 --out tools/ui_synth/out
python -m unittest discover -s tools/ui_synth/tests
```
`--n` is the number of trajectories (each gives 2-6 rows, one per step). Same seed, same files.

Outputs in `out/`:
| file | content |
|---|---|
| `train.jsonl` | training rows |
| `dev.jsonl` | in-distribution validation, split by trajectory (5%) |
| `test.jsonl` | held out by **template** (`call_list`, `menu`, `ad_popup`) and by **app** (`maps`, `files`) |
| `*.meta.jsonl` | one line per row: app, template, kind, target label/tag, held-out reason (for analysis; not for training) |

Train and dev contain no held-out template or app; `vocab.TEST_TEMPLATES` / `TEST_APPS` define them.

## Row format
Same shape as the 5-intent Needle data: `{"query", "tools", "answers", "reasoning"}`. `query` is
```
goal: <typed or spoken-style goal>
history: <last 4 actions, e.g. open_app(instagram); tap(4 "Search")> | none
screen:
app:instagram scroll:yes
1 button "Navigate up" cl
2 edit "Search" cle
...
+3 more
```
The screen uses the exact `ScreenSerializer.kt` grammar (`N role "label" flags`, flags `c l e s x`,
header `app:<name> scroll:<yes|no>`, `+N more`), capped at 350 estimated tokens. Tools: `tap(id)`,
`type(text)`, `scroll(direction)`, `back`, `home`, `open_app(app)`, `finish(status)`; `status` is
`done`, `impossible` (control not on this screen) or `confirm` (destructive action, ask the user).
Total prompt incl. tools is kept under 900 estimated tokens for the 1,024-token window.

## What is covered
Templates: launcher, bottom/top tab bars, feed with Like/Comment/Share, chat list, chat view
(input and Send), search and results, settings list and toggle page, call list, in-call, profile,
overflow menu, dialogs (permission, rating, cookies, confirm), ad popups, inline ads and clutter
(extra containers, disabled items, decorative images, dense screens), scrolling state with `+N more`.
Ten apps: Instagram, WhatsApp, Telegram, Twitter, Google, Settings, YouTube, Files, Gmail, Maps.
Recipes (`recipes.py`): open app, like/comment/share, search (optionally open first result), send
message, toggle a setting (including "already on" and "below the fold"), call, profile, overflow menu,
risky menu item with confirmation, scroll, back/home, dismiss popup, impossible goals.

## Mix with the 5-intent data
```
python tools/ui_synth/mix.py --ui tools/ui_synth/out/train.jsonl \
    --base F:/needle-finetune/data/train_v4.jsonl --share 0.3 \
    --out tools/ui_synth/out/mixed_train.jsonl
```
`--share` is the fraction of the output taken from the 5-intent file (default 0.3); `--total N`
fixes the output size. Rows are copied unchanged and shuffled with the given `--seed`.

## Known limits
- Correctness is by construction against synthetic screens; real apps are messier. Evaluate on the
  recorded redacted screens (held out, never trained on) before trusting any number.
- "Impossible" rows use explicit missing controls on detail screens; real ambiguity is harder.
- Token counts use the app's 3.5 chars/token estimate, not the model's tokenizer.
