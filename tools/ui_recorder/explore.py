"""Read-only explorer: open an app, look around, go back, and record redacted rows.

Allowed: launch an app, scroll, tap a navigation label (tab / search entry), press back.
Never: send, post, like, follow, comment, delete, pay, change settings, type text. Every tap is
checked against the deny list on the real label (in memory) before it is made, and only labels
on the navigation list are ever tapped.

    python tools/ui_recorder/explore.py settings google telegram whatsapp instagram twitter
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from parse_dump import Node  # noqa: E402
from recorder import Adb, Capture, Recorder, capture, settle  # noqa: E402
from redact import Allowlist  # noqa: E402

APPS = {
    "settings": "com.android.settings",
    "google": "com.google.android.googlequicksearchbox",
    "telegram": "org.telegram.messenger",
    "whatsapp": "com.whatsapp",
    "instagram": "com.instagram.android",
    "twitter": "com.twitter.android",
}


def tap_allowed(node: Node, nav: set[str], deny: set[str]) -> bool:
    """True only for a plainly navigational, non-destructive control."""
    label = (node.label or "").strip().casefold()
    if not label or label == "<item>":
        return False
    if label in deny or label not in nav:
        return False
    if not node.clickable or not node.enabled or node.editable:
        return False
    if any(k in node.cls for k in ("Switch", "CheckBox", "Toggle")):
        return False
    return True


def candidates(cap: Capture, nav: set[str], deny: set[str], seen: set[str]) -> list[tuple[int, Node]]:
    if not cap.ser:
        return []
    return [(i, n) for i, n in cap.ser.id_to_node.items()
            if tap_allowed(n, nav, deny) and (n.label or "").casefold() not in seen]


def explore_app(adb: Adb, rec: Recorder, name: str, package: str, max_taps: int,
                allow: Allowlist) -> None:
    allowed = allow.for_app(name) | allow.for_app("global")
    nav, deny = allow.nav_tap(), allow.deny_tap()
    w, h = adb.screen_size()
    step = 0

    adb.home()
    settle()
    home = capture(adb, allowed)
    if not adb.launch(package):
        rec.log_blocked(name, step, "not_installed_or_no_launcher")
        return
    settle(2.5)
    cur = capture(adb, allowed)
    if cur.blocked:
        rec.log_blocked(name, step, cur.blocked)
        return
    rec.log_step(name, step, f"open {name}", home,
                 {"type": "open_app", "target_id": None, "target_label": None}, cur)

    # scroll down then up (swipes only; no taps)
    for goal, kind, y1, y2 in (("scroll down", "scroll_down", int(h * 0.7), int(h * 0.3)),
                               ("scroll up", "scroll_up", int(h * 0.3), int(h * 0.7))):
        if adb.input_blocked:
            break
        step += 1
        if not adb.swipe(w // 2, y1, w // 2, y2):
            break
        settle()
        nxt = capture(adb, allowed)
        rec.log_step(name, step, goal, cur,
                     {"type": kind, "target_id": None, "target_label": None}, nxt)
        if nxt.blocked:
            return
        cur = nxt

    seen: set[str] = set()
    taps = 0
    while taps < max_taps and not adb.input_blocked:
        cands = candidates(cur, nav, deny, seen)
        if not cands:
            break
        idx, node = cands[0]
        label = node.label or ""
        seen.add(label.casefold())
        # tap the centre of the control
        step += 1
        x = (node.bounds[0] + node.bounds[2]) // 2
        y = (node.bounds[1] + node.bounds[3]) // 2
        if not adb.tap(x, y):
            break
        taps += 1
        settle()
        nxt = capture(adb, allowed)
        rec.log_step(name, step, f"go to {label}", cur,
                     {"type": "tap", "target_id": idx, "target_label": label}, nxt)
        rec._stat(name)["navigated"].append(label)
        if nxt.blocked:
            rec.log_blocked(name, step, nxt.blocked)
        # return to where we were
        step += 1
        adb.back()
        settle()
        back_cap = capture(adb, allowed)
        rec.log_step(name, step, "go back", nxt,
                     {"type": "back", "target_id": None, "target_label": None}, back_cap)
        if back_cap.blocked or back_cap.text != cur.text:
            # Screens differ (feed refreshed, or we left the app): re-anchor from the app's
            # launcher entry rather than guessing.
            adb.launch(package)
            settle(2)
            back_cap = capture(adb, allowed)
            if back_cap.blocked:
                break
        cur = back_cap


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("apps", nargs="*", default=list(APPS))
    ap.add_argument("--serial")
    ap.add_argument("--max-taps", type=int, default=6)
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
        explore_app(adb, rec, name, APPS[name], args.max_taps, allow)
        rec.write_summary()
        if adb.input_blocked:
            print("input is blocked on this phone (enable USB debugging security settings); "
                  "only the launch screen was recorded")
    rec.write_summary()
    print("done; run scanner.py to audit the output")
    return 0


if __name__ == "__main__":
    sys.exit(main())
