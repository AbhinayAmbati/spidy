#!/usr/bin/env python3
"""Builds the golden replay set used by GoldenReplayTest.

Each row keeps the model's RECORDED answer next to the gold answer, so the unit test can
replay it through Kotlin code without the model. The sample is deterministic:

  * every test_v4 row the model got wrong (the hard cases),
  * a stratified sample of the test_v4 rows it got right (by category),
  * a stratified sample of dev_natural (by category), plus its two misses.

Usage: python tools/spidey_eval/build_golden.py [--logs DIR] [--out FILE]
The recorded answers come from the training-side logs eval_v4_on_v4_all.jsonl and
eval_v4_dev_all.jsonl (the model that became spidey.cact).
"""
import argparse
import collections
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import run_eval as ev  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "test", "resources", "golden", "spidey_golden.jsonl")
SOURCES = (("test_v4", "eval_v4_on_v4_all.jsonl", 36), ("dev_natural", "eval_v4_dev_all.jsonl", 58))


def stratified(rows, quota):
    """Round-robin over categories in a stable order until quota rows are taken."""
    by_cat = collections.defaultdict(list)
    for row in rows:
        by_cat[row["cat"]].append(row)
    cats = sorted(by_cat)
    picked, index = [], 0
    while len(picked) < min(quota, len(rows)):
        for cat in cats:
            if index < len(by_cat[cat]) and len(picked) < quota:
                # spread the pick through the category rather than taking its head
                step = max(1, len(by_cat[cat]) // max(1, quota // len(cats)))
                picked.append(by_cat[cat][(index * step) % len(by_cat[cat])])
        index += 1
        if index > max(len(v) for v in by_cat.values()):
            break
    seen, unique = set(), []
    for row in picked:
        if row["query"] not in seen:
            seen.add(row["query"])
            unique.append(row)
    return unique


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--logs", default="F:/needle-finetune/logs")
    parser.add_argument("--out", default=OUT)
    args = parser.parse_args()

    out_rows = []
    for source, log, quota in SOURCES:
        rows = ev.load_rows(os.path.join(args.logs, log))
        wrong = [r for r in rows if not ev.is_exact(r["pred"] or [], r["gold"])]
        right = [r for r in rows if ev.is_exact(r["pred"] or [], r["gold"])]
        for row in wrong + stratified(right, quota):
            pred = row["pred"] or []
            out_rows.append({
                "id": f"{source}-{len(out_rows):03d}", "source": source, "cat": row["cat"],
                "query": row["query"], "gold": row["gold"], "recorded": pred,
                "failure": ev.failure_category(pred, row["gold"], row["query"]),
            })
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8", newline="\n") as handle:
        for row in out_rows:
            handle.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
    size = os.path.getsize(args.out)
    print(f"{len(out_rows)} rows, {size} bytes -> {args.out}")
    print(collections.Counter((r["source"], r["failure"]) for r in out_rows))


if __name__ == "__main__":
    main()
