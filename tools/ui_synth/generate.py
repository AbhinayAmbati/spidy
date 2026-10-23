#!/usr/bin/env python3
"""Generate synthetic (goal, screen, next action) rows for the on-device UI-selector skill.

    python tools/ui_synth/generate.py --n 6000 --seed 1 --out tools/ui_synth/out

Pure stdlib, no network. Never reads recorded device data.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import random
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from recipes import Step, Traj, make_traj  # noqa: E402
from screens import est_tokens, render  # noqa: E402
from vocab import TEST_APPS, TEST_TEMPLATES  # noqa: E402

STATUSES = ["done", "impossible", "confirm"]


def _tool(name, desc, props=None, required=None):
    return {"name": name, "description": desc,
            "parameters": {"type": "object", "properties": props or {}, "required": required or []}}


UI_TOOLS = [
    _tool("tap", "Tap the numbered element on the screen.",
          {"id": {"type": "integer", "description": "Element number from the screen list"}}, ["id"]),
    _tool("type", "Type text into the focused or only text field.",
          {"text": {"type": "string", "description": "Text to type, exactly as it should appear"}}, ["text"]),
    _tool("scroll", "Scroll the current list.",
          {"direction": {"type": "string", "description": "'up' or 'down'"}}, ["direction"]),
    _tool("back", "Press the system back button."),
    _tool("home", "Go to the home screen."),
    _tool("open_app", "Open an installed app by name.",
          {"app": {"type": "string", "description": "App name, lowercase, e.g. 'youtube'"}}, ["app"]),
    _tool("finish", "Stop: the goal is done, cannot be done here, or needs the user to confirm.",
          {"status": {"type": "string", "description": "'done', 'impossible' or 'confirm'"}}, ["status"]),
]


def action_text(name: str, args: dict, label: str = "", tap_id: int | None = None) -> str:
    if name == "tap":
        return f'tap({tap_id} "{label}")'
    if args:
        return f"{name}({', '.join(str(v) for v in args.values())})"
    return name


def build_rows(traj: Traj) -> list[tuple[dict, dict]]:
    """-> [(row, meta)] one per step."""
    out = []
    history: list[str] = []
    for step in traj.steps:
        targets = (step.elem,) if step.elem is not None else ()
        text, idmap = render(step.screen, targets)
        args = dict(step.args)
        label = ""
        tap_id = None
        if step.name == "tap":
            tap_id = next(i for i, e in idmap.items() if e is step.elem)
            args = {"id": tap_id}
            label = step.elem.label
        hist = "; ".join(history[-4:]) if history else "none"
        query = f"goal: {traj.goal}\nhistory: {hist}\nscreen:\n{text}"
        row = {"query": query, "tools": UI_TOOLS,
               "answers": [{"name": step.name, "arguments": args}],
               "reasoning": step.why}
        test = step.screen.template in TEST_TEMPLATES or traj.app in TEST_APPS
        meta = {"app": traj.app, "template": step.screen.template, "kind": traj.kind,
                "action": step.name, "target_id": tap_id, "target_label": label or None,
                "target_tag": step.elem.tag if step.elem is not None else None,
                "step": len(history), "traj_steps": len(traj.steps),
                "heldout": "template" if step.screen.template in TEST_TEMPLATES else
                           ("app" if traj.app in TEST_APPS else None), "test": test}
        out.append((row, meta))
        history.append(action_text(step.name, step.args, label, tap_id))
    return out


def _traj_bucket(goal: str, seed: int) -> float:
    h = hashlib.sha256(f"{seed}|{goal}".encode()).digest()
    return int.from_bytes(h[:4], "big") / 2**32


def generate(n: int, seed: int, dev_share: float = 0.05):
    rng = random.Random(seed)
    train, dev, test = [], [], []
    seen: set[str] = set()
    for t in range(n):
        traj = make_traj(random.Random(rng.getrandbits(64)))
        dev_traj = _traj_bucket(f"{t}|{traj.goal}", seed) < dev_share
        for row, meta in build_rows(traj):
            if row["query"] in seen:
                continue
            seen.add(row["query"])
            if meta["test"]:
                test.append((row, meta))
            elif dev_traj:
                dev.append((row, meta))
            else:
                train.append((row, meta))
    return train, dev, test


def write_split(path: Path, pairs) -> None:
    with open(path, "w", encoding="utf8", newline="\n") as f, \
            open(path.with_suffix(".meta.jsonl"), "w", encoding="utf8", newline="\n") as m:
        for row, meta in pairs:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
            m.write(json.dumps(meta, ensure_ascii=False) + "\n")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--n", type=int, default=6000, help="number of trajectories")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--out", type=Path, default=HERE / "out")
    a = ap.parse_args(argv)
    a.out.mkdir(parents=True, exist_ok=True)
    train, dev, test = generate(a.n, a.seed)
    for name, pairs in (("train", train), ("dev", dev), ("test", test)):
        write_split(a.out / f"{name}.jsonl", pairs)
    allp = train + dev + test
    toks = [est_tokens(r["query"]) for r, _ in allp]
    print(f"train={len(train)} dev={len(dev)} test={len(test)} max_query_tokens~{max(toks)}")
    print("actions:", dict(Counter(m["action"] for _, m in allp)))
    print("test held out:", dict(Counter(m["heldout"] for _, m in test)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
