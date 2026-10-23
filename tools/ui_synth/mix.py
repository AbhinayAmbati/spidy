#!/usr/bin/env python3
"""Blend UI-selector rows with the existing 5-intent rows.

    python tools/ui_synth/mix.py --ui tools/ui_synth/out/train.jsonl \
        --base <path to train_v4.jsonl> --share 0.3 --out tools/ui_synth/out/mixed_train.jsonl

--share is the fraction of the OUTPUT that comes from --base (the 5-intent data). Without --total,
all UI rows are kept and base rows are sampled to hit the share; with --total the output has that
many rows. Rows are copied untouched (each already carries its own tools), then shuffled.
"""
from __future__ import annotations

import argparse
import random
from pathlib import Path


def read(path: Path) -> list[str]:
    with open(path, encoding="utf8") as f:
        return [ln.rstrip("\n") for ln in f if ln.strip()]


def mix(ui: list[str], base: list[str], share: float, seed: int, total: int | None = None) -> list[str]:
    if not 0.0 <= share < 1.0:
        raise ValueError("share must be in [0, 1)")
    rng = random.Random(seed)
    if total is None:
        n_base = round(len(ui) * share / (1 - share)) if share else 0
        n_ui = len(ui)
    else:
        n_base = round(total * share)
        n_ui = total - n_base
    if n_base > len(base):                       # not enough base rows: repeat with fresh shuffles
        picked: list[str] = []
        while len(picked) < n_base:
            chunk = list(base)
            rng.shuffle(chunk)
            picked += chunk
        picked = picked[:n_base]
    else:
        picked = rng.sample(base, n_base)
    if n_ui > len(ui):
        raise ValueError(f"need {n_ui} UI rows but only {len(ui)} available; lower --total or generate more")
    out = rng.sample(ui, n_ui) + picked
    rng.shuffle(out)
    return out


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ui", type=Path, required=True)
    ap.add_argument("--base", type=Path, required=True, help="existing 5-intent JSONL (e.g. train_v4.jsonl)")
    ap.add_argument("--share", type=float, default=0.3, help="fraction of output from --base")
    ap.add_argument("--total", type=int, default=None)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--out", type=Path, required=True)
    a = ap.parse_args(argv)
    rows = mix(read(a.ui), read(a.base), a.share, a.seed, a.total)
    a.out.parent.mkdir(parents=True, exist_ok=True)
    with open(a.out, "w", encoding="utf8", newline="\n") as f:
        f.write("\n".join(rows) + "\n")
    print(f"wrote {len(rows)} rows to {a.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
