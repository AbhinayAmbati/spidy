# Evaluating the Spidey intent model

`spidey.cact` maps one sentence to zero or more of five calls (`open_app`, `send_message`,
`call_contact`, `set_alarm`, `set_timer`; see `core/intent/SpideyIntent.kt`). It was
fine-tuned with no system prompt, so the input is the raw sentence and exactly those five tools.

This page says what the reported numbers mean, whether they reproduce, and where they
overstate. Everything below was recomputed from the training-side data and logs
(`needle-finetune/data/*.jsonl`, `logs/eval_v4_*_all.jsonl`, and the scripts `eval.py`,
`guard_sim.py`, `report.py`) with `tools/spidey_eval/run_eval.py --replay`.

## Verdict

| Reported | Recomputed | Reproduces? |
|---|---|---|
| 81.4% on test_v4 | 281 / 345 = **81.4%** | Yes, arithmetically |
| 84.6% on test_v4 with guards | 292 / 345 = **84.6%** | Yes, but only with a guard whose contact and app list is the test set's own vocabulary (point 2 below) |
| 98.7% on dev_natural | 150 / 152 = **98.7%** | Yes, arithmetically, but it is not a held-out number (point 1 below) |

The logs match the datasets row for row (same queries, same gold). Strict and lenient scoring
give identical counts on both sets, so no case or punctuation slack inflates anything.

**None of these figures was measured on a phone.** They come from the Python `needle` runtime
on a Linux box (`needle.Needle(..., auto_date=False)`). The on-device engine, the shipped
build and the Android path are not covered by them; `run_eval.py` in adb mode is what measures
that (accuracy, latency, peak RAM). No phone was attached when this page was written, so there
are no latency or RAM figures yet.

## How a match is defined

From `scripts/eval.py`, reimplemented in `run_eval.py`:

* **Exact** (`ok`, the headline): the predicted call list equals the gold list. Order matters.
  Tool names must be equal. Every argument must be equal as a string, character for character
  (case and punctuation included); `minutes` must be the same integer. A refusal is correct
  only when gold is empty and nothing was predicted.
* **Lenient** (`lenient_ok`): the same after `strip()`, removing trailing `. ! ?` and
  `casefold()` on string values.
* The 81.4% and 98.7% are **exact**. The guard figure (`guard_sim.py`) compares lower-cased,
  stripped values after the guard has rewritten entity names.

## Where the numbers overstate

1. **dev_natural was used to choose the model.** `logs/` holds dev runs of the 8, 12 and
   16-layer checkpoints (78, 114 and 133 of 152 correct), v3 (145) and the shipped v4 (150).
   Picking the best on the same set makes 98.7% an upper estimate, not a held-out result. It is
   also 152 hand-written sentences by the model's author, with only easy negatives (36
   `neg_misc`, no idioms or chat traps), 7 of its 23 contacts also appear in training, and one
   query is also in test_v4.
2. **The 84.6% guard is fitted to test_v4.** `guard_sim.py` hard-codes 16 contacts and 70 apps.
   All 16 distinct test_v4 contacts and all 13 test_v4 apps are in that list, and none of the 104
   training contacts are. It models a phone whose address book is exactly the test set's. The
   same guard on dev_natural, where most contacts are not in the list, falls from 98.7% to
   **62.5%** (95/152): it throws away 55 correct answers and, through fuzzy matching, turns 7
   into wrong actions. The real guard (`IntentGuard`, against the phone's actual contacts and
   apps) will behave differently. Treat 84.6% as a best case for a phone that knows every name.
3. **A list-free guard does nothing.** Dropping calls whose contact or app is not in the
   sentence (`grounded()` in `run_eval.py`) leaves test_v4 at 281 (81.4%): the model's inventions
   are usually words that are in the sentence ("open the curtains" gives `curtains`). Gains
   need a roster (`stone`, `case` and `telugu` are not installed apps or contacts).
4. **test_v4 is the honest set**, with caveats: 0 of its 16 contacts and 0 of its 13 apps occur
   in train_v4 (names are genuinely unseen) and no query is shared with train_v4, but v3 and v4
   were compared on it (259 vs 281 correct), and 2 of its 345 queries are duplicates.

## Recomputed results

### test_v4 (n = 345, raw model output)

| Gold intent | n | exact | % |
|---|---:|---:|---:|
| `none` (must decline) | 105 | 94 | 89.5 |
| `send_message` | 88 | 73 | 83.0 |
| `open_app` | 31 | 21 | 67.7 |
| `call_contact` | 33 | 22 | 66.7 |
| `set_alarm` | 32 | 29 | 90.6 |
| `set_timer` | 24 | 23 | 95.8 |
| chain (2+ calls) | 32 | 19 | 59.4 |
| **all** | **345** | **281** | **81.4** |

The 64 failures by category (`failure_category()` in `run_eval.py`):

| Failure | n | Meaning |
|---|---:|---|
| wrong tool | 28 | different tool names or call count (11 of them chains) |
| wrong argument | 10 | right tools, wrong value (typo'd name, extra words) |
| hallucinated contact/app | 6 | the contact or app is not in the sentence (`zhouya`, `mesho`) |
| missed unsupported | 11 | gold is "decline", model acted (`set in stone` gives `open_app("stone")`) |
| refused (safe miss) | 9 | model declined something it could do |

Weakest slices: `lower` 22/32, `noisy` 22/32, `multi` 19/32, `neg_idiom` 2/6.

### dev_natural (n = 152)

150 correct. The two misses are refusals (an `open_app` and a `set_alarm`); no wrong actions.
Per intent: `call_contact` 20/20, `send_message` 30/30, `set_timer` 14/14, chains 12/12,
`none` 36/36, `open_app` 21/22, `set_alarm` 17/18.

### What the guard changes (`guard_sim.py`, test_v4)

| | correct | wrong actions | safe refusals |
|---|---:|---:|---:|
| raw | 281 | 55 | 9 |
| guard (test-set roster) | 292 | 31 | 22 |

The guard repairs 11 answers (typos resolved to the roster name, `mesho` and `zhouya`; invented
entities dropped, `stone`, `toss up` and `telugu`) and turns 13 more wrong actions into
refusals, 24 fewer wrong actions in all. What remains: wrong tool 20, refused 22, missed
unsupported 6, wrong argument 5. On dev_natural see point 2: raw 0 wrong actions, guarded 7.

No confidence head ships with these weights (`confidence: null` in every log), so confidence
thresholding cannot serve as a second guard.

## Safety reading

For an assistant that acts, **wrong actions** matter more than accuracy: 55 of 345 test_v4
answers (16%) took an action that was not asked for before any guard. The app should keep the
confirmation sheet and the device-grounded `IntentGuard`, and 81% or 98.7% should not be quoted
as the rate of correct actions.

## Reproducing and extending

```bash
# recompute from recorded logs (no phone)
python tools/spidey_eval/run_eval.py --replay F:/needle-finetune/logs/eval_v4_on_v4_all.jsonl
python tools/spidey_eval/run_eval.py --replay F:/needle-finetune/logs/eval_v4_dev_all.jsonl

# measure the real on-device engine (arm64 phone, adb): accuracy, latency, peak RAM
python tools/spidey_eval/run_eval.py --data F:/needle-finetune/data/test_v4.jsonl --cleanup
```

Adb mode downloads `spidey.cact` from the URL in `ModelSpec.kt` and checks its size and SHA-256
against that file's pins, takes the vendor runner from `Cactus-Compute/needle3`
(`android-arm64/needle`, hash pinned in the script), pushes both to
`/data/local/tmp/spidey_eval` and installs nothing. Latency is one process per query, so it
includes loading the model (an upper bound); peak RAM is sampled `VmHWM` (a lower bound).
Run it on test_v4 as well as dev_natural: test_v4 is the one to believe.

Offline regression tests (no model needed):

* `GoldenReplayTest` replays 154 recorded answers (every test_v4 miss plus stratified samples,
  `app/src/test/resources/golden/spidey_golden.jsonl`, rebuilt by
  `tools/spidey_eval/build_golden.py`) through a strict decoder into `SpideyIntent` and a
  stand-in `IntentGuard`. One test is `@Ignore`d until `spidey-router` merges; then point
  `parserUnderTest` at `SpideyResponseParser` and remove the annotation.
* `EvalModelIntegrityTest` shows that a corrupt, truncated, dropped, mis-sized or poisoned
  download never replaces an installed good model.

Finding for the owners of `ModelDownloader`: `verifyAndPublish` deletes the installed file
before `renameTo`. If the rename then fails (full disk, permissions) no model is left. Renaming
over the target, or moving the old file aside and restoring it on failure, would close that. No
test reaches this path with the current API, so it is untested.
