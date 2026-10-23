#!/usr/bin/env python3
"""Build the pilot training set: five new intents + the original five + the UI-selector rows.

    python tools/intent_extra/build_pilot.py --base F:/needle-finetune/data \
        --ui tools/ui_synth/out --out tools/intent_extra/out

Everything is generated from invented words; no personal data. Output goes to a git-ignored folder.

Rows follow the fine-tune format {query, tools, answers, reasoning}. Intent rows (old and new) all carry
the same ten-tool list so the router sees one consistent tool set; UI rows keep their own seven tools.
"""
from __future__ import annotations

import argparse
import json
import random
import re
from pathlib import Path

NEW_TOOLS = [
    {"name": "play_media", "description": "Play a song, video, podcast or other media.",
     "parameters": {"type": "object", "properties": {
         "query": {"type": "string", "description": "What to play, as the user says it"},
         "app": {"type": "string", "description": "App to play it in, lowercase, e.g. 'youtube'. Omit if not said"}},
         "required": ["query"]}},
    {"name": "web_search", "description": "Search the web for something.",
     "parameters": {"type": "object", "properties": {
         "query": {"type": "string", "description": "What to search for"}}, "required": ["query"]}},
    {"name": "navigate", "description": "Get directions to a place.",
     "parameters": {"type": "object", "properties": {
         "place": {"type": "string", "description": "Destination as the user says it"}}, "required": ["place"]}},
    {"name": "set_reminder", "description": "Create a reminder with a message at a time.",
     "parameters": {"type": "object", "properties": {
         "text": {"type": "string", "description": "What to be reminded about"},
         "time": {"type": "string", "description": "When, as the user says it, e.g. 'tomorrow at 9 am'"}},
         "required": ["text", "time"]}},
    {"name": "toggle_setting", "description": "Turn a device setting on or off.",
     "parameters": {"type": "object", "properties": {
         "setting": {"type": "string", "description": "Setting name, lowercase, e.g. 'wifi', 'bluetooth', 'flashlight'"},
         "state": {"type": "string", "description": "'on' or 'off'"}}, "required": ["setting", "state"]}},
]

TITLES = ["Moonlit Roads", "Paper Kites", "Slow River", "Glass Garden", "Orange Sky", "Winter Radio", "Tiny Engines",
          "Harbor Lights", "Velvet Rain", "Copper Hills", "Night Train", "Soft Static", "Lantern Song", "Blue Meadow"]
GENRES = ["lofi beats", "jazz", "rain sounds", "workout music", "classical piano", "acoustic covers", "study music",
          "focus playlist", "podcast about space", "cooking videos", "travel vlogs", "comedy clips"]
ARTISTS = ["Mira Vale", "Tobin Quill", "The Ember Club", "Lenka Dray", "Orrin Wick", "Suri Hollis"]
MEDIA_APPS = ["youtube", "spotify", "youtube music", "gaana", "jiosaavn"]
SEARCH = ["how to bake sourdough", "best indoor plants", "weather tomorrow", "chess opening ideas", "paper boat steps",
          "cheap train tickets", "what is a black hole", "easy dinner recipes", "guitar chords for beginners",
          "how tall is mount kenya", "tips for better sleep", "latest space news"]
PLACES = ["the airport", "the railway station", "central park", "the city library", "the nearest petrol pump",
          "the main bus stand", "grand market", "the old fort", "riverside cafe", "the nearest pharmacy",
          "harbor road", "the science museum"]
REMIND = ["call the dentist", "buy milk", "water the plants", "pay the electricity bill", "submit the report",
          "take the medicine", "book train tickets", "send the invoice", "pick up the parcel", "renew the passport"]
WHEN = ["at 9 am", "tomorrow at 8 am", "at 6 pm", "tonight at 10", "at 5:30 pm", "tomorrow morning", "on friday at 4 pm",
        "at noon", "in the evening"]
SETTINGS = [("wifi", ["wifi", "wi-fi"]), ("bluetooth", ["bluetooth"]), ("flashlight", ["flashlight", "torch"]),
            ("do not disturb", ["do not disturb", "dnd"]), ("airplane mode", ["airplane mode", "flight mode"]),
            ("hotspot", ["hotspot"]), ("dark mode", ["dark mode"]), ("location", ["location", "gps"])]

PRE = ["", "", "", "please ", "hey ", "can you ", "could you ", "i want to ", "i'd like to ", "just ", "quickly "]
POST = ["", "", "", " please", " now", " for me"]

# Templates are split so the test set uses phrasings the training set never saw.
PLAY_T = [("play {q}", 0), ("play {q} on {a}", 0), ("put on {q}", 0), ("start playing {q}", 0),
          ("play {q} in {a}", 0), ("i want to listen to {q}", 1), ("can you play some {q}", 1),
          ("queue up {q} on {a}", 1), ("play me {q}", 1), ("play the song {q}", 1), ("{a} play {q}", 1)]
SEARCH_T = [("search for {q}", 0), ("google {q}", 0), ("look up {q}", 0), ("search the web for {q}", 0),
            ("find information about {q}", 1), ("what does the internet say about {q}", 1), ("web search {q}", 1)]
NAV_T = [("navigate to {p}", 0), ("take me to {p}", 0), ("directions to {p}", 0), ("how do i get to {p}", 0),
         ("drive to {p}", 1), ("route to {p}", 1), ("guide me to {p}", 1), ("find the way to {p}", 1)]
REM_T = [("remind me to {t} {w}", 0), ("set a reminder to {t} {w}", 0), ("reminder {t} {w}", 0),
         ("don't let me forget to {t} {w}", 1), ("remind me {w} to {t}", 1), ("add a reminder: {t} {w}", 1)]
TOG_T = [("turn {s} {o}", 0), ("switch {o} {s}", 0), ("{s} {o}", 0), ("turn {o} the {s}", 0),
         ("can you turn {s} {o}", 1), ("enable {s}", 1), ("disable {s}", 1), ("{o} {s} please", 1)]


def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9 ]", "", s.lower()).strip()


def sentence(rng: random.Random, tmpl: str, **kw) -> str:
    s = tmpl.format(**kw)
    s = rng.choice(PRE) + s + rng.choice(POST)
    return re.sub(r"\s+", " ", s).strip()


def call(name: str, **args) -> dict:
    return {"name": name, "arguments": args}


def row(query: str, answers: list[dict], tools: list[dict]) -> dict:
    why = "; ".join(f"'{v}' -> {k}" for a in answers for k, v in a["arguments"].items())
    return {"query": query, "tools": tools, "answers": answers, "reasoning": why or "no matching tool"}


def play_row(rng, split, tools):
    t = rng.choice([t for t in PLAY_T if t[1] == split])[0]
    q = rng.choice(TITLES + GENRES + [f"{rng.choice(TITLES)} by {rng.choice(ARTISTS)}", rng.choice(ARTISTS)])
    if "{a}" in t:
        a = rng.choice(MEDIA_APPS)
        return row(sentence(rng, t, q=q.lower(), a=a), [call("play_media", query=q.lower(), app=a)], tools)
    return row(sentence(rng, t, q=q.lower()), [call("play_media", query=q.lower())], tools)


def search_row(rng, split, tools):
    t = rng.choice([t for t in SEARCH_T if t[1] == split])[0]
    q = rng.choice(SEARCH)
    return row(sentence(rng, t, q=q), [call("web_search", query=q)], tools)


def nav_row(rng, split, tools):
    t = rng.choice([t for t in NAV_T if t[1] == split])[0]
    p = rng.choice(PLACES)
    return row(sentence(rng, t, p=p), [call("navigate", place=p)], tools)


def remind_row(rng, split, tools):
    t = rng.choice([t for t in REM_T if t[1] == split])[0]
    x, w = rng.choice(REMIND), rng.choice(WHEN)
    return row(sentence(rng, t, t=x, w=w), [call("set_reminder", text=x, time=w)], tools)


def toggle_row(rng, split, tools):
    t = rng.choice([t for t in TOG_T if t[1] == split])[0]
    canon, spoken = rng.choice(SETTINGS)
    word = rng.choice(spoken)
    if t.startswith("enable") or t.startswith("disable"):
        state = "on" if t.startswith("enable") else "off"
        return row(sentence(rng, t, s=word), [call("toggle_setting", setting=canon, state=state)], tools)
    state = rng.choice(["on", "off"])
    return row(sentence(rng, t, s=word, o=state), [call("toggle_setting", setting=canon, state=state)], tools)


MAKERS = [play_row, search_row, nav_row, remind_row, toggle_row]


def combo_row(rng, split, tools):
    """Two intents in one sentence, one old and one new, joined the way people speak."""
    a = rng.choice(MAKERS)(rng, split, tools)
    mins = rng.choice([5, 10, 15, 20, 30])
    old = call("set_timer", minutes=mins)
    q = f"set a {mins} minute timer and {a['query'][0].lower() + a['query'][1:]}"
    return row(q, [old] + a["answers"], tools)


NEW_PATTERNS = re.compile(r"\b(play|put on|listen|search|google|look up|navigate|directions|route|take me to|"
                          r"remind|reminder|turn (on|off)|switch (on|off)|enable|disable|wifi|wi-fi|bluetooth|"
                          r"flashlight|torch|hotspot|dark mode|airplane|how do i get)\b", re.I)


def load(path: Path) -> list[dict]:
    with open(path, encoding="utf8") as f:
        return [json.loads(ln) for ln in f if ln.strip()]


def write(path: Path, rows: list[dict]) -> None:
    with open(path, "w", encoding="utf8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def build(base: Path, ui: Path, out: Path, seed: int, per_intent: int, ui_rows: int, old_rows: int) -> dict:
    rng = random.Random(seed)
    old_tools = json.load(open(base / "tools.json", encoding="utf8"))
    tools = old_tools + NEW_TOOLS

    def gen(split: int, n: int) -> list[dict]:
        rows = [m(rng, split, tools) for m in MAKERS for _ in range(n)]
        rows += [combo_row(rng, split, tools) for _ in range(max(1, n // 4))]
        return rows

    new_train, new_dev, new_test = gen(0, per_intent), gen(0, per_intent // 8), gen(1, per_intent // 4)
    seen = {norm(r["query"]) for r in new_test}
    new_train = [r for r in new_train if norm(r["query"]) not in seen]
    new_dev = [r for r in new_dev if norm(r["query"]) not in seen]

    def retool(rows: list[dict]) -> list[dict]:
        keep = []
        for r in rows:
            # an old "no tool" row about playing, searching or toggling would now contradict the new tools
            if not r["answers"] and NEW_PATTERNS.search(r["query"]):
                continue
            keep.append({**r, "tools": tools})
        return keep

    old_train = retool(load(base / "train_v4.jsonl"))
    old_test = retool(load(base / "test_v4.jsonl"))
    rng.shuffle(old_train)
    old_train = old_train[:old_rows]

    ui_train = load(ui / "train.jsonl")
    rng.shuffle(ui_train)
    ui_train = ui_train[:ui_rows]

    train = old_train + new_train + ui_train
    rng.shuffle(train)
    out.mkdir(parents=True, exist_ok=True)
    write(out / "pilot_train.jsonl", train)
    write(out / "intents_old_test.jsonl", old_test)
    write(out / "intents_new_test.jsonl", new_test)
    write(out / "intents_new_dev.jsonl", new_dev)
    for name in ("dev.jsonl", "test.jsonl"):
        write(out / f"ui_{name}", load(ui / name))
    return {"train": len(train), "old": len(old_train), "new": len(new_train), "ui": len(ui_train),
            "new_test": len(new_test), "old_test": len(old_test)}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", type=Path, required=True)
    ap.add_argument("--ui", type=Path, required=True)
    ap.add_argument("--out", type=Path, required=True)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--per-intent", type=int, default=320)
    ap.add_argument("--ui-rows", type=int, default=4000)
    ap.add_argument("--old-rows", type=int, default=2400)
    a = ap.parse_args()
    print(build(a.base, a.ui, a.out, a.seed, a.per_intent, a.ui_rows, a.old_rows))


if __name__ == "__main__":
    main()
