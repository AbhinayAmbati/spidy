"""Redaction: decide, in memory, which strings may survive.

The rule is default-deny. A label is kept only when it exactly matches a word on the
allowlist (labels that belong to the app UI itself, such as Like, Send or a tab name).
Everything else - message text, names, numbers, captions, URLs, search queries, emails -
is dropped here, before any caller can write it anywhere.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ITEM = "<item>"
_ALLOWLIST_PATH = Path(__file__).with_name("allowlist.json")
_ID_LEAF = re.compile(r"^[a-z][a-z0-9_]{1,29}$")
_SPACE = re.compile(r"\s+")


def _norm(s: str) -> str:
    return _SPACE.sub(" ", s.strip()).casefold()


class Allowlist:
    def __init__(self, data: dict | None = None):
        if data is None:
            data = json.loads(_ALLOWLIST_PATH.read_text(encoding="utf-8"))
        self._data = data
        self._global = {_norm(x) for x in data.get("global", [])}

    def for_app(self, app: str) -> set[str]:
        return self._global | {_norm(x) for x in self._data.get(app, [])}

    def all_labels(self) -> set[str]:
        out = set(self._global)
        for k, v in self._data.items():
            if k in ("_comment", "deny_tap", "nav_tap"):
                continue
            out |= {_norm(x) for x in v}
        return out

    def deny_tap(self) -> set[str]:
        return {_norm(x) for x in self._data.get("deny_tap", [])}

    def nav_tap(self) -> set[str]:
        return {_norm(x) for x in self._data.get("nav_tap", [])}


def safe_label(raw: str | None, allowed: set[str]) -> str | None:
    """Return the label if it is app UI vocabulary, else None."""
    if not raw:
        return None
    n = _norm(raw)
    if n and n in allowed:
        return _SPACE.sub(" ", raw.strip())
    return None


def safe_id_leaf(resource_id: str | None) -> str | None:
    """Resource-id names are fixed by the app's code, not by the user's content."""
    if not resource_id:
        return None
    leaf = resource_id.rsplit("/", 1)[-1]
    return leaf if _ID_LEAF.match(leaf) else None
