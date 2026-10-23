"""Parse `uiautomator dump` XML into redacted nodes.

This is the only place raw screen text is ever read. Each node leaves this function with
structure (class, flags, bounds, package) and at most an allowlisted label; the raw
`text` / `content-desc` strings are not stored on the node and go out of scope here.
"""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from dataclasses import dataclass

from redact import ITEM, safe_id_leaf, safe_label

_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


@dataclass(frozen=True)
class Node:
    pkg: str
    cls: str                     # short class name, e.g. "Button"
    label: str | None            # allowlisted label, "<item>" for redacted interactive text, or None
    id_leaf: str | None
    clickable: bool
    long_clickable: bool
    editable: bool
    scrollable: bool
    enabled: bool
    password: bool
    bounds: tuple[int, int, int, int]


@dataclass(frozen=True)
class Dump:
    nodes: list[Node]
    packages: list[str]          # packages present, most nodes first
    has_password_field: bool


def _flag(v: str | None) -> bool:
    return v == "true"


def extract_xml(raw: str) -> str | None:
    """uiautomator prints the XML followed by a status line; cut to the document."""
    start = raw.find("<?xml")
    if start < 0:
        start = raw.find("<hierarchy")
    end = raw.rfind("</hierarchy>")
    if start < 0 or end < 0:
        return None
    return raw[start:end + len("</hierarchy>")]


def parse_dump(raw: str, allowed: set[str]) -> Dump | None:
    xml = extract_xml(raw)
    if xml is None:
        return None
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None

    nodes: list[Node] = []
    counts: dict[str, int] = {}
    has_pw = False
    for el in root.iter("node"):
        m = _BOUNDS.fullmatch(el.get("bounds", ""))
        if not m:
            continue
        l, t, r, b = (int(x) for x in m.groups())
        if r <= l or b <= t:
            continue
        cls = (el.get("class") or "").rsplit(".", 1)[-1]
        password = _flag(el.get("password"))
        editable = "EditText" in cls or password
        clickable = _flag(el.get("clickable"))
        long_c = _flag(el.get("long-clickable"))
        scrollable = _flag(el.get("scrollable"))
        interactive = clickable or long_c or editable or scrollable
        has_pw = has_pw or password

        label: str | None = None
        if not editable:  # typed text, hints and passwords are never kept
            raw_label = (el.get("text") or "").strip() or (el.get("content-desc") or "").strip()
            if raw_label:
                label = safe_label(raw_label, allowed)
                if label is None and interactive:
                    label = ITEM
        pkg = el.get("package") or ""
        counts[pkg] = counts.get(pkg, 0) + 1
        nodes.append(Node(
            pkg=pkg, cls=cls, label=label, id_leaf=safe_id_leaf(el.get("resource-id")),
            clickable=clickable, long_clickable=long_c, editable=editable,
            scrollable=scrollable, enabled=_flag(el.get("enabled")), password=password,
            bounds=(l, t, r, b),
        ))
    # The system UI (status bar, nav bar) is always present; the app is the next most common.
    pkgs = sorted((p for p in counts if p), key=lambda p: (p == "com.android.systemui", -counts[p]))
    return Dump(nodes, pkgs, has_pw)
