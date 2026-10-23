"""Last line of defence: reject any record that looks like it holds personal content.

Run on every row before it is written, and again over the whole output folder as an
audit (`python scanner.py <dir>` exits 1 on any finding).
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

from redact import ITEM, Allowlist

EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+\.[A-Za-z]{2,}")
URL = re.compile(r"(https?://|www\.)|\b[\w-]+\.(com|org|net|in|io|co|me|app|dev|ly|gl|tv)\b", re.I)
HANDLE = re.compile(r"(?<![\w])@\w{2,}")
LONG_DIGITS = re.compile(r"\d{5,}")
PHONE = re.compile(r"(?<!\w)\+?\d[\d\s().-]{6,}\d")
IPV4 = re.compile(r"\b\d{1,3}(\.\d{1,3}){3}\b")
QUOTED = re.compile(r'"([^"]*)"')
ID_LIKE = re.compile(r"^[a-z][a-z0-9_]{1,29}$")
MAX_STRING = 30
MAX_WORDS = 4
_SPACE = re.compile(r"\s+")


def _strings(value, out: list[str]) -> None:
    if isinstance(value, str):
        out.append(value)
    elif isinstance(value, dict):
        for k, v in value.items():
            out.append(str(k))
            _strings(v, out)
    elif isinstance(value, (list, tuple)):
        for v in value:
            _strings(v, out)


def _generic(s: str, where: str) -> list[str]:
    bad = []
    if EMAIL.search(s):
        bad.append(f"{where}: email")
    if URL.search(s):
        bad.append(f"{where}: url")
    if HANDLE.search(s):
        bad.append(f"{where}: handle")
    if LONG_DIGITS.search(s) or PHONE.search(s):
        bad.append(f"{where}: phone-or-long-number")
    if IPV4.search(s):
        bad.append(f"{where}: ip-address")
    return bad


def check_row(row: dict, allowed_labels: set[str] | None = None,
              known_ids: set[str] | None = None) -> list[str]:
    """Return a list of problems; empty means the row may be written.

    allowed_labels: every allowlisted label (default: the whole allowlist file).
    known_ids: resource-id leaves seen in the dump this row came from.
    """
    allowed = allowed_labels if allowed_labels is not None else Allowlist().all_labels()
    ids = known_ids or set()
    strings: list[str] = []
    _strings(row, strings)
    bad: list[str] = []
    for s in strings:
        bad += _generic(s, "value")
        # Serialized lines are short by construction; a long line is prose.
        if any(len(line) > 70 for line in s.split("\n")):
            bad.append("value: long free text")
    # Quoted labels in serialized screens must be vocabulary, "<item>" or a known id.
    for key in ("screen", "next_screen"):
        text = row.get(key)
        if not isinstance(text, str):
            continue
        for label in QUOTED.findall(text):
            n = _SPACE.sub(" ", label.strip()).casefold()
            # ids may be cut to the label length limit, so a prefix of a seen id is fine
            if label == ITEM or n in allowed or any(i.startswith(label) for i in ids):
                continue
            if len(label) > MAX_STRING:
                bad.append(f"{key}: long free text")
            elif len(label.split()) > MAX_WORDS:
                bad.append(f"{key}: free text")
            else:
                bad.append(f"{key}: label not on allowlist")
    # Free text outside quotes is not part of the format.
    for key in ("goal",):
        v = row.get(key)
        if isinstance(v, str) and (len(v) > 60 or any(ch.isdigit() for ch in v)):
            bad.append("goal: not a short template")
    return sorted(set(bad))


def audit_dir(path: Path) -> list[str]:
    findings: list[str] = []
    allowed = Allowlist().all_labels()
    for f in sorted(path.rglob("*")):
        if not f.is_file():
            continue
        if f.suffix.lower() in (".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4"):
            findings.append(f"{f}: image/video file")
            continue
        if f.suffix.lower() != ".jsonl":
            continue
        with f.open(encoding="utf-8") as fh:
            for n, line in enumerate(fh, 1):
                if not line.strip():
                    continue
                try:
                    row = json.loads(line)
                except ValueError:
                    findings.append(f"{f}:{n}: not json")
                    continue
                ids = set(row.get("ids", []))
                for p in check_row(row, allowed, ids):
                    findings.append(f"{f}:{n}: {p}")
    return findings


def main(argv: list[str]) -> int:
    target = Path(argv[1]) if len(argv) > 1 else Path(__file__).with_name("out")
    if not target.exists():
        print(f"nothing to scan at {target}")
        return 0
    findings = audit_dir(target)
    for f in findings:
        print(f)
    print(f"{len(findings)} finding(s)")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
