"""Read-only explorer: open an app, look around, go back, and record redacted rows.

Allowed: launch an app, scroll, tap a navigation control (tab, bottom/top bar item, search or
menu icon, and settings list rows), press back. Never: send, post, like, follow, comment,
delete, pay, record, upload, compose, type text, or change a setting. Every tap is checked
on the real label and resource id (in memory) against the deny list first; when a control is
not clearly navigation, it is skipped. Depth is capped and each app has a step cap.

    python tools/ui_recorder/explore.py settings google telegram whatsapp instagram twitter
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from parse_dump import Node  # noqa: E402
from recorder import Adb, Capture, Recorder, capture, settle  # noqa: E402
from redact import DENY_TOKENS, Allowlist  # noqa: E402
from serialize import role  # noqa: E402

APPS = {
    "settings": "com.android.settings",
    "google": "com.google.android.googlequicksearchbox",
    "telegram": "org.telegram.messenger",
    "whatsapp": "com.whatsapp",
    "instagram": "com.instagram.android",
    "twitter": "com.twitter.android",
}

NAV_ID_TOKENS = {"tab", "nav", "navigation", "bottom", "search", "menu", "back", "home", "explore",
                 "toolbar", "drawer", "up", "overflow", "more"}
DIALOG_WORDS = {"ok", "confirm", "delete", "allow", "deny", "cancel", "yes", "no", "remove",
                "continue", "accept", "agree", "turn on", "turn off", "discard", "leave"}
LIST_ROW_APPS = {"settings"}   # list rows are only opened where that has no side effects
MAX_DEPTH = 3
_TOKEN = re.compile(r"[a-z0-9]+")


def tokens(s: str | None) -> set[str]:
    return set(_TOKEN.findall((s or "").casefold()))


def denied(node: Node, deny_labels: set[str]) -> bool:
    label = (node.label or "").strip().casefold()
    if label in deny_labels:
        return True
    toks = tokens(node.label if node.label != "<item>" else "") | tokens(node.id_leaf)
    return node.risky or bool(toks & DENY_TOKENS)


def is_dialog(cap: Capture) -> bool:
    if not cap.dump:
        return False
    return any(n.clickable and (n.label or "").strip().casefold() in DIALOG_WORDS
               for n in cap.dump.nodes)


def tap_decision(node: Node, w: int, h: int, app: str, nav: set[str], deny_labels: set[str],
                 switches_in_row: bool = False) -> bool:
    """True only for a clearly navigational control that cannot change anything."""
    if denied(node, deny_labels) or node.editable or node.password or not node.enabled:
        return False
    r = role(node)
    if r in ("switch", "edit"):
        return False
    label = (node.label or "").strip().casefold()
    cy = (node.bounds[1] + node.bounds[3]) // 2
    in_bar = cy > h * 0.88 or cy < h * 0.14
    known_label = label in nav
    nav_id = bool(tokens(node.id_leaf) & NAV_ID_TOKENS)
    if r == "tab" and (known_label or nav_id or label != "<item>"):
        return True
    if known_label and (node.clickable or in_bar):
        return True
    if in_bar and nav_id and (node.clickable or known_label):
        return True
    if app in LIST_ROW_APPS and node.clickable and r == "button" and not switches_in_row \
            and not in_bar and node.bounds[2] - node.bounds[0] > w * 0.5:
        return True
    return False


def candidates(cap: Capture, w: int, h: int, app: str, nav: set[str], deny: set[str],
               seen: set[str]) -> list[tuple[int, Node]]:
    if not cap.ser:
        return []
    nodes = cap.ser.id_to_node
    sw = [n for n in nodes.values() if role(n) == "switch"]
    out = []
    for i, n in nodes.items():
        key = f"{n.label or ''}|{n.id_leaf or ''}|{n.bounds[1] // 40}"
        if key in seen:
            continue
        row_has_switch = any(n.bounds[1] <= (s.bounds[1] + s.bounds[3]) // 2 <= n.bounds[3]
                             for s in sw)
        if tap_decision(n, w, h, app, nav, deny, row_has_switch):
            out.append((i, n))
    return out


class Explorer:
    def __init__(self, adb: Adb, rec: Recorder, name: str, package: str, allow: Allowlist,
                 max_steps: int):
        self.adb, self.rec, self.name, self.package = adb, rec, name, package
        self.allowed = allow.for_app(name) | allow.for_app("global")
        self.nav, self.deny = allow.nav_tap(), allow.deny_tap()
        self.max_steps = max_steps
        self.step = 0
        self.seen: set[str] = set()
        self.w, self.h = adb.screen_size()

    def cap(self) -> Capture:
        return capture(self.adb, self.allowed)

    def in_app(self, c: Capture) -> bool:
        return bool(c.dump and c.dump.packages and c.dump.packages[0] == self.package)

    def relaunch(self) -> Capture:
        self.adb.launch(self.package)
        settle(2.2)
        return self.cap()

    def go_back(self, from_cap: Capture, goal: str) -> Capture:
        self.step += 1
        self.adb.back()
        settle()
        c = self.cap()
        self.rec.log_step(self.name, self.step, goal, from_cap,
                          {"type": "back", "target_id": None, "target_label": None}, c)
        return c

    def explore(self, cur: Capture, depth: int) -> None:
        while self.step < self.max_steps and not self.adb.input_blocked:
            cands = candidates(cur, self.w, self.h, self.name, self.nav, self.deny, self.seen)
            if not cands:
                return
            idx, node = cands[0]
            label = node.label if node.label and node.label != "<item>" else (node.id_leaf or "item")
            self.seen.add(f"{node.label or ''}|{node.id_leaf or ''}|{node.bounds[1] // 40}")
            self.step += 1
            x, y = (node.bounds[0] + node.bounds[2]) // 2, (node.bounds[1] + node.bounds[3]) // 2
            if not self.adb.tap(x, y):
                return
            settle()
            nxt = self.cap()
            self.rec.log_step(self.name, self.step, f"go to {label}", cur,
                              {"type": "tap", "target_id": idx, "target_label": node.label
                               if node.label != "<item>" else None}, nxt)
            self.rec._stat(self.name)["navigated"].append(label)
            if nxt.blocked:
                self.rec.log_blocked(self.name, self.step, nxt.blocked)
            left_app = not nxt.blocked and not self.in_app(nxt)
            if not nxt.blocked and not left_app and not is_dialog(nxt) and depth + 1 < MAX_DEPTH:
                self.explore(nxt, depth + 1)
                nxt_now = self.cap()
            else:
                nxt_now = nxt
            back = self.go_back(nxt_now, "go back")
            tries = 0
            while (back.blocked or back.text != cur.text) and tries < 2 and self.in_app(back):
                back = self.go_back(back, "go back")
                tries += 1
                if back.text == cur.text:
                    break
            if back.blocked or back.text != cur.text:
                back = self.relaunch()
                if back.blocked:
                    return
            cur = back
            if depth > 0 and cur.text != "" and not self.in_app(cur):
                return


def explore_app(adb: Adb, rec: Recorder, name: str, package: str, max_steps: int,
                allow: Allowlist) -> None:
    ex = Explorer(adb, rec, name, package, allow, max_steps)
    adb.home()
    settle()
    home = ex.cap()
    if not adb.launch(package):
        rec.log_blocked(name, 0, "not_installed_or_no_launcher")
        return
    settle(2.5)
    cur = ex.cap()
    if cur.blocked:
        rec.log_blocked(name, 0, cur.blocked)
        return
    rec.log_step(name, 0, f"open {name}", home,
                 {"type": "open_app", "target_id": None, "target_label": None}, cur)
    w, h = ex.w, ex.h
    for goal, kind, y1, y2 in (("scroll down", "scroll_down", int(h * 0.7), int(h * 0.3)),
                               ("scroll up", "scroll_up", int(h * 0.3), int(h * 0.7))):
        if adb.input_blocked:
            return
        ex.step += 1
        if not adb.swipe(w // 2, y1, w // 2, y2):
            return
        settle()
        nxt = ex.cap()
        rec.log_step(name, ex.step, goal, cur,
                     {"type": kind, "target_id": None, "target_label": None}, nxt)
        if nxt.blocked:
            return
        cur = nxt
    ex.explore(cur, 0)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apps", nargs="*", default=list(APPS))
    ap.add_argument("--serial")
    ap.add_argument("--max-steps", type=int, default=40)
    args = ap.parse_args()

    adb = Adb(args.serial)
    devices = adb.devices()
    if not devices:
        print("no device connected")
        return 2
    if not args.serial and len(devices) > 1:
        print("several devices, pass --serial")
        return 2
    allow = Allowlist()
    rec = Recorder()
    for name in args.apps:
        if name not in APPS:
            print(f"unknown app {name}")
            continue
        print(f"== {name}")
        explore_app(adb, rec, name, APPS[name], args.max_steps, allow)
        rec.write_summary()
        if adb.input_blocked:
            print("input rejected by the phone; only the launch screen was recorded")
    rec.write_summary()
    print("done; run scanner.py to audit the output")
    return 0


if __name__ == "__main__":
    sys.exit(main())
