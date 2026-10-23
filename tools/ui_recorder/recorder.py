"""adb driver and row writer.

The adb wrapper only runs a fixed set of read-only commands (dump the UI tree as text, launch
an app, swipe, back/home, tap). Nothing here takes pictures or uses the network; the dump
travels over the USB cable into memory and is redacted before anything is written.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import time
from pathlib import Path

from parse_dump import Dump, parse_dump
from redact import Allowlist
from scanner import check_row
from serialize import Serialized, role, serialize

OUT_DIR = Path(__file__).with_name("out")
_SDK_ADB = Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"

_ALLOWED = [
    re.compile(r"^devices$"),
    re.compile(r"^exec-out uiautomator dump /dev/tty$"),
    re.compile(r"^shell wm size$"),
    re.compile(r"^shell cmd package resolve-activity --brief [\w.]+$"),
    re.compile(r"^shell am start -a android\.intent\.action\.MAIN -c android\.intent\.category\.LAUNCHER -n [\w./$]+$"),
    re.compile(r"^shell input swipe \d+ \d+ \d+ \d+ \d+$"),
    re.compile(r"^shell input tap \d+ \d+$"),
    re.compile(r"^shell input keyevent KEYCODE_(BACK|HOME)$"),
]


class AdbError(Exception):
    pass


class Adb:
    def __init__(self, serial: str | None = None, adb_path: str | None = None):
        self.serial = serial
        self.path = adb_path or os.environ.get("ADB") or shutil.which("adb") or str(_SDK_ADB)
        self.input_blocked = False

    def run(self, *args: str, timeout: int = 30) -> subprocess.CompletedProcess:
        joined = " ".join(args)
        if not any(p.match(joined) for p in _ALLOWED):
            raise AdbError(f"command not allowed: {joined}")
        cmd = [self.path] + (["-s", self.serial] if self.serial else []) + list(args)
        return subprocess.run(cmd, capture_output=True, timeout=timeout, check=False)

    # ---- read ----
    def devices(self) -> list[str]:
        out = self.run("devices").stdout.decode("utf-8", "replace").splitlines()[1:]
        return [ln.split()[0] for ln in out if ln.strip().endswith("device")]

    def screen_size(self) -> tuple[int, int]:
        out = self.run("shell", "wm", "size").stdout.decode("utf-8", "replace")
        m = re.search(r"(\d+)x(\d+)", out)
        return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)

    def launcher_component(self, package: str) -> str | None:
        out = self.run("shell", "cmd", "package", "resolve-activity", "--brief", package)
        lines = [ln.strip() for ln in out.stdout.decode("utf-8", "replace").splitlines() if "/" in ln]
        return lines[-1] if lines else None

    def dump_raw(self) -> str:
        r = self.run("exec-out", "uiautomator", "dump", "/dev/tty", timeout=40)
        return r.stdout.decode("utf-8", "replace")

    # ---- navigation (no content is created or changed by these) ----
    def _input(self, *args: str) -> bool:
        r = self.run("shell", "input", *args)
        err = (r.stderr + r.stdout).decode("utf-8", "replace")
        if r.returncode != 0 or "INJECT_EVENTS" in err or "SecurityException" in err:
            self.input_blocked = True
            return False
        return True

    def launch(self, package: str) -> bool:
        comp = self.launcher_component(package)
        if not comp or comp.startswith("No activity"):
            return False
        r = self.run("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c",
                     "android.intent.category.LAUNCHER", "-n", comp)
        return r.returncode == 0 and b"Error" not in r.stdout

    def back(self) -> bool:
        return self._input("keyevent", "KEYCODE_BACK")

    def home(self) -> bool:
        return self._input("keyevent", "KEYCODE_HOME")

    def tap(self, x: int, y: int) -> bool:
        return self._input("tap", str(x), str(y))

    def swipe(self, x1: int, y1: int, x2: int, y2: int, ms: int = 300) -> bool:
        return self._input("swipe", str(x1), str(y1), str(x2), str(y2), str(ms))


class Capture:
    """One redacted view of the screen. `blocked` is set instead of a screen when unreadable."""

    def __init__(self, dump: Dump | None, ser: Serialized | None, blocked: str | None):
        self.dump, self.ser, self.blocked = dump, ser, blocked

    @property
    def text(self) -> str:
        return self.ser.text if self.ser else ""

    def ids(self) -> set[str]:
        return {n.id_leaf for n in self.dump.nodes if n.id_leaf} if self.dump else set()


def capture(adb: Adb, allowed: set[str]) -> Capture:
    try:
        raw = adb.dump_raw()
    except subprocess.TimeoutExpired:
        return Capture(None, None, "dump_timeout")
    dump = parse_dump(raw, allowed)
    del raw  # the raw dump never leaves this function
    if dump is None:
        return Capture(None, None, "dump_failed_or_secure")
    interactive = sum(1 for n in dump.nodes if n.clickable or n.editable or n.scrollable)
    if dump.has_password_field:
        return Capture(None, None, "login_screen")
    if len(dump.nodes) < 3 or interactive == 0:
        return Capture(None, None, "secure_or_empty")
    return Capture(dump, serialize(dump), None)


class Recorder:
    def __init__(self, out_dir: Path = OUT_DIR):
        self.out = out_dir
        self.out.mkdir(parents=True, exist_ok=True)
        self.allow = Allowlist().all_labels()
        self.stats: dict[str, dict] = {}
        old = self.out / "summary.json"
        if old.exists():
            try:
                self.stats = json.loads(old.read_text(encoding="utf-8"))
            except ValueError:
                pass

    def _stat(self, app: str) -> dict:
        return self.stats.setdefault(app, {"rows": 0, "blocked": 0, "rejected": 0, "screens": 0,
                                           "max_tokens": 0, "roles": {}, "navigated": []})

    def _append(self, app: str, row: dict) -> None:
        with (self.out / f"{app}.jsonl").open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(row, ensure_ascii=True) + "\n")

    def log_blocked(self, app: str, step: int, reason: str) -> None:
        self._append(app, {"app": app, "step": step, "blocked": True, "reason": reason})
        self._stat(app)["blocked"] += 1

    def log_step(self, app: str, step: int, goal: str, before: Capture, action: dict,
                 after: Capture) -> bool:
        st = self._stat(app)
        if before.blocked or after.blocked:
            self.log_blocked(app, step, before.blocked or after.blocked or "blocked")
            return False
        row = {"app": app, "step": step, "goal": goal, "screen": before.text, "action": action,
               "next_screen": after.text, "ids": sorted(before.ids() | after.ids())}
        problems = check_row(row, self.allow, before.ids() | after.ids())
        if problems:
            st["rejected"] += 1  # counted, content discarded, never written
            for p in problems:  # categories only, e.g. "screen: label not on allowlist"
                st.setdefault("rejected_reasons", {})
                st["rejected_reasons"][p] = st["rejected_reasons"].get(p, 0) + 1
            return False
        self._append(app, row)
        st["rows"] += 1
        st["screens"] += 1
        st["max_tokens"] = max(st["max_tokens"], before.ser.tokens, after.ser.tokens)
        for n in before.ser.id_to_node.values():
            r = role(n)
            st["roles"][r] = st["roles"].get(r, 0) + 1
        return True

    def write_summary(self) -> None:
        (self.out / "summary.json").write_text(json.dumps(self.stats, indent=2), encoding="utf-8")


def settle(seconds: float = 1.3) -> None:
    time.sleep(seconds)
