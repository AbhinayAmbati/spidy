"""Python mirror of ScreenSerializer.kt, working on redacted nodes.

Same filtering, merging, ordering, line format and token budget as the app, so recorded
rows look like what the on-device model will see. Element ids are 1..N in visual order.
"""
from __future__ import annotations

import math
from dataclasses import dataclass, replace

from parse_dump import Dump, Node

DEFAULT_TOKEN_BUDGET = 350
MAX_LABEL_CHARS = 24
CHARS_PER_TOKEN = 3.5
DUPLICATE_PX = 16
MORE_RESERVE = 5
LAYOUT = {"View", "ViewGroup", "FrameLayout", "LinearLayout", "RelativeLayout",
          "ConstraintLayout", "CoordinatorLayout", "ComposeView"}


@dataclass(frozen=True)
class Serialized:
    text: str
    id_to_node: dict[int, Node]   # kept in memory only (used to tap); never written
    tokens: int
    dropped: int


def estimate_tokens(s: str) -> int:
    return math.ceil(len(s) / CHARS_PER_TOKEN)


def _interactive(n: Node) -> bool:
    return n.clickable or n.long_clickable or n.editable or n.scrollable


def _cx(n: Node) -> int:
    return (n.bounds[0] + n.bounds[2]) // 2


def _cy(n: Node) -> int:
    return (n.bounds[1] + n.bounds[3]) // 2


def role(n: Node) -> str:
    c = n.cls
    if n.editable or "EditText" in c:
        return "edit"
    if any(k in c for k in ("Switch", "CheckBox", "Toggle")):
        return "switch"
    if "Tab" in c:
        return "tab"
    if "Image" in c:
        return "image"
    if any(k in c for k in ("RecyclerView", "ListView", "ScrollView", "Pager")):
        return "list"
    if "Button" in c:
        return "button"
    if "TextView" in c and not _interactive(n):
        return "text"
    if n.clickable:
        return "button"
    if n.scrollable:
        return "list"
    return "other"


def _worth_keeping(n: Node) -> bool:
    if n.label:
        return True
    if not _interactive(n):
        return False
    bare = n.cls in LAYOUT and n.id_leaf is None and not n.editable and not n.scrollable
    return not bare


def _contains(o: Node, i: Node) -> bool:
    a, b = o.bounds, i.bounds
    return a[0] <= b[0] and a[1] <= b[1] and a[2] >= b[2] and a[3] >= b[3]


def _merge(nodes: list[Node]) -> list[Node]:
    parents = [n for n in nodes if n.clickable]
    relabel: dict[int, str] = {}
    kept: list[Node] = []
    for n in nodes:
        if not _interactive(n) and n.label:
            parent = next((p for p in parents if p is not n and _contains(p, n)), None)
            if parent is not None:
                if not parent.label and id(parent) not in relabel:
                    relabel[id(parent)] = n.label
                continue
        kept.append(n)
    out: list[Node] = []
    for n in kept:
        if id(n) in relabel:
            n = replace(n, label=relabel[id(n)])
        twin = n.label is not None and any(
            role(o) == role(n) and o.label == n.label
            and abs(_cx(o) - _cx(n)) <= DUPLICATE_PX and abs(_cy(o) - _cy(n)) <= DUPLICATE_PX
            for o in out)
        if not twin:
            out.append(n)
    return out


def _priority(n: Node) -> int:
    if n.editable:
        return 0
    if n.clickable and n.label:
        return 1
    if n.scrollable:
        return 2
    if _interactive(n):
        return 3
    return 4


def _line(i: int, n: Node) -> str:
    flags = "".join(c for c, on in (("c", n.clickable), ("l", n.long_clickable),
                                    ("e", n.editable), ("s", n.scrollable),
                                    ("x", not n.enabled)) if on)
    label = (n.label or n.id_leaf or "")[:MAX_LABEL_CHARS].replace('"', "'")
    s = f"{i} {role(n)}"
    if label:
        s += f' "{label}"'
    if flags:
        s += f" {flags}"
    return s


def serialize(dump: Dump, token_budget: int = DEFAULT_TOKEN_BUDGET) -> Serialized:
    kept = _merge([n for n in dump.nodes if _worth_keeping(n)])
    app = (dump.packages[0].rsplit(".", 1)[-1] if dump.packages else "unknown")
    header = f"app:{app} scroll:{'yes' if any(n.scrollable for n in kept) else 'no'}"
    used = estimate_tokens(header)
    chosen: list[tuple[int, Node]] = []
    for idx, n in sorted(enumerate(kept), key=lambda p: (_priority(p[1]), p[0])):
        cost = estimate_tokens(_line(0, n)) + 1
        if used + cost <= token_budget - MORE_RESERVE:
            used += cost
            chosen.append((idx, n))
    dropped = len(kept) - len(chosen)
    ordered = sorted((n for _, n in chosen), key=lambda n: (n.bounds[1], n.bounds[0]))
    lines = [header] + [_line(i + 1, n) for i, n in enumerate(ordered)]
    if dropped:
        lines.append(f"+{dropped} more")
    text = "\n".join(lines)
    return Serialized(text, {i + 1: n for i, n in enumerate(ordered)},
                      estimate_tokens(text), dropped)
