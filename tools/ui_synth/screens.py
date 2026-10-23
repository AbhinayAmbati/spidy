"""Synthetic screens in the exact text format of ScreenSerializer.kt.

A Screen is a list of Elem in visual order. Elems carry a semantic `tag` (never serialised) that
recipes use to pick the correct action by construction.
"""
from __future__ import annotations

import math
import random
from dataclasses import dataclass, field

from vocab import APPS, CAPTIONS, FIRST, LAST, LAUNCHER_FILLER, MESSAGES

TOKEN_BUDGET = 350
MORE_RESERVE = 5
MAX_LABEL = 24
CHARS_PER_TOKEN = 3.5


def est_tokens(s: str) -> int:
    return math.ceil(len(s) / CHARS_PER_TOKEN)


@dataclass(eq=False)
class Elem:
    role: str
    label: str = ""
    flags: str = ""
    tag: str | None = None
    keep: bool = False  # never trimmed (structure / targets)

    def line(self, i: int) -> str:
        fl = "".join(c for c in "clesx" if c in self.flags)
        s = f"{i} {self.role}"
        lab = self.label[:MAX_LABEL].replace('"', "'")
        if lab:
            s += f' "{lab}"'
        if fl:
            s += f" {fl}"
        return s


@dataclass
class Screen:
    app: str          # package leaf shown in header
    template: str
    elems: list[Elem]
    more: int = 0     # elements known to be off screen (rendered as "+N more", scroll enabled)
    scroll: bool = False
    state: dict = field(default_factory=dict)

    def find(self, tag: str) -> Elem | None:
        return next((e for e in self.elems if e.tag == tag), None)

    def find_all(self, prefix: str) -> list[Elem]:
        return [e for e in self.elems if e.tag and e.tag.startswith(prefix)]


def _prio(e: Elem) -> int:
    if "e" in e.flags:
        return 0
    if "c" in e.flags and e.label:
        return 1
    if "s" in e.flags:
        return 2
    if e.flags:
        return 3
    return 4


def render(scr: Screen, targets: tuple[Elem, ...] = ()) -> tuple[str, dict[int, Elem]]:
    """Return (text, id->Elem). Trims lowest-priority non-target elems to fit the token budget."""
    elems = list(scr.elems)
    dropped = 0

    def build() -> str:
        scroll = scr.scroll or scr.more > 0 or any("s" in e.flags for e in elems)
        lines = [f"app:{scr.app} scroll:{'yes' if scroll else 'no'}"]
        lines += [e.line(i + 1) for i, e in enumerate(elems)]
        extra = scr.more + dropped
        if extra:
            lines.append(f"+{extra} more")
        return "\n".join(lines)

    text = build()
    while est_tokens(text) > TOKEN_BUDGET - MORE_RESERVE:
        cands = [e for e in elems if e not in targets and not e.keep]
        if not cands:
            raise ValueError("screen cannot fit the token budget")
        victim = max(reversed(cands), key=_prio)  # lowest priority, latest in order
        elems.remove(victim)
        dropped += 1
        text = build()
    return text, {i + 1: e for i, e in enumerate(elems)}


# ---------------------------------------------------------------- helpers

def person(rng: random.Random) -> str:
    return f"{rng.choice(FIRST)} {rng.choice(LAST)}"


def people(rng: random.Random, n: int) -> list[str]:
    out: list[str] = []
    while len(out) < n:
        p = person(rng)
        if p not in out:
            out.append(p)
    return out


def tab_bar(app: str) -> list[Elem]:
    tabs = APPS[app]["tabs"]
    if not tabs:
        return []
    return [Elem("tab", lab, "c", tag=f"tab:{lab.lower()}", keep=True) for lab in tabs[1]]


def _bottom(app: str) -> list[Elem]:
    t = APPS[app]["tabs"]
    return tab_bar(app) if t and t[0] == "bottom" else []


def _topbar(app: str) -> list[Elem]:
    t = APPS[app]["tabs"]
    return tab_bar(app) if t and t[0] == "top" else []


def _up() -> list[Elem]:
    return [Elem("button", "Navigate up", "cl", tag="back_btn")]


def noise(rng: random.Random, elems: list[Elem], level: int = 2) -> None:
    """Insert harmless clutter: extra containers, disabled items, decorative images and text."""
    pool = [
        lambda: Elem("list", "", "s"),
        lambda: Elem("image", "", "c"),
        lambda: Elem("text", rng.choice(["Suggested for you", "Today", "Earlier", "Recent", "Featured"])),
        lambda: Elem("button", rng.choice(["Edit", "Archive", "Pin"]), "cx"),   # disabled
        lambda: Elem("other", rng.choice(["Banner", "Header", "Section"])),
        lambda: Elem("button", rng.choice(["Dismiss tip", "Learn more", "Got it"]), "cl"),
    ]
    if rng.random() < 0.35:   # dense screens: real apps are far busier than the templates
        level += rng.randint(5, 16)
        pool += [lambda: Elem("image", "", "c")] * 3 + [
            lambda: Elem("button", rng.choice(["Story", "Photo", "Item", "Card", "Tile"]), "cl"),
            lambda: Elem("text", rng.choice(["New", "Sponsored", "Trending", "Tip", "Shared"]))]
    for _ in range(rng.randint(0, level)):
        pos = rng.randint(1, max(1, len(elems) - 1))
        elems.insert(pos, pool[rng.randrange(len(pool))]())


# ---------------------------------------------------------------- templates

def launcher(rng: random.Random, apps: list[str]) -> Screen:
    names = [APPS[a]["name"] for a in apps] + rng.sample(LAUNCHER_FILLER, rng.randint(3, 6))
    rng.shuffle(names)
    elems = [Elem("list", "", "s")]
    for n in names:
        key = next((a for a in apps if APPS[a]["name"] == n), None)
        elems.append(Elem("button", n, "cl", tag=f"app:{key}" if key else None))
    if rng.random() < 0.5:
        elems.insert(1, Elem("button", "Search", "cl", tag="launcher_search"))
    return Screen("launcher", "launcher", elems, scroll=True)


def feed(rng: random.Random, app: str, n_posts: int | None = None, liked: frozenset[int] = frozenset(),
         ads: bool = False, authors: list[str] | None = None, more: int = 0, level: int = 2) -> Screen:
    n = n_posts or rng.randint(2, 3)
    authors = authors or people(rng, n)
    elems: list[Elem] = [Elem("list", "", "s"), Elem("button", "Notifications", "cl", tag="bell")]
    if rng.random() < 0.6 or not any(x.lower() == "search" for x in (APPS[app]["tabs"] or (0, []))[1]):
        elems.append(Elem("button", "Search", "cl", tag="search_icon"))
    caps = rng.sample(CAPTIONS, n)
    for i in range(n):
        elems.append(Elem("button", authors[i], "cl", tag=f"author:{i}"))
        elems.append(Elem("image", "", "c"))
        elems.append(Elem("button", "Unlike" if i in liked else "Like", "cl", tag=f"like:{i}"))
        elems.append(Elem("button", "Comment", "cl", tag=f"comment:{i}"))
        elems.append(Elem("button", "Share", "cl", tag=f"share:{i}"))
        if rng.random() < 0.5:
            elems.append(Elem("text", caps[i]))
    if ads:
        elems += [Elem("text", "Sponsored"), Elem("button", "Install now", "cl", tag="ad_cta"),
                  Elem("button", "Learn more", "cl")]
    noise(rng, elems, level)
    elems += _bottom(app)
    return Screen(app, "feed", elems, more=more, scroll=True, state=dict(authors=authors, n=n, liked=set(liked)))


def search(rng: random.Random, app: str, query: str | None = None, results: list[str] | None = None,
           level: int = 2) -> Screen:
    elems = _up()
    elems.append(Elem("edit", query if query else "Search", "cle", tag="input", keep=True))
    if query:
        elems.append(Elem("button", "Clear", "cl", tag="clear"))
    if results is None:
        elems.append(Elem("text", "Recent"))
        for q in rng.sample(["tips", "ideas", "nearby", "popular"], 2):
            elems.append(Elem("button", q, "cl", tag=f"suggest:{q}"))
    else:
        elems.append(Elem("list", "", "s"))
        for i, r in enumerate(results):
            elems.append(Elem("button", r, "cl", tag=f"result:{i}"))
    noise(rng, elems, level)
    return Screen(app, "search", elems, scroll=results is not None, state=dict(results=results))


def chat_list(rng: random.Random, app: str, contacts: list[str], more: int = 0, level: int = 2) -> Screen:
    elems: list[Elem] = [Elem("text", APPS[app]["name"]), Elem("button", "Search", "cl", tag="search_icon"),
                         Elem("button", "More options", "cl", tag="menu")]
    elems += _topbar(app)
    elems.append(Elem("list", "", "s"))
    for c in contacts:
        elems.append(Elem("button", c, "cl", tag=f"contact:{c}"))
    elems.append(Elem("button", "New chat", "cl", tag="fab"))
    noise(rng, elems, level)
    elems += _bottom(app)
    return Screen(app, "chat_list", elems, more=more, scroll=True)


def chat_view(rng: random.Random, app: str, contact: str, typed: str | None = None,
              sent: list[str] | None = None, level: int = 1) -> Screen:
    elems = _up() + [Elem("button", contact, "cl", tag="header"), Elem("button", "Voice call", "cl"),
                     Elem("button", "More options", "cl", tag="menu"), Elem("list", "", "s")]
    for m in rng.sample(MESSAGES, rng.randint(2, 4)):
        elems.append(Elem("text", m))
    for m in (sent or []):
        elems.append(Elem("text", m))
    noise(rng, elems, level)
    elems.append(Elem("button", "Attach", "cl"))
    elems.append(Elem("edit", typed if typed else "Message", "cle", tag="input", keep=True))
    if typed:
        elems.append(Elem("button", "Send", "c", tag="send", keep=True))
    else:
        elems.append(Elem("button", "Voice message", "cl"))
    return Screen(app, "chat_view", elems, scroll=True, state=dict(contact=contact))


def settings_list(rng: random.Random, app: str, entries: list[str], more: int = 0, level: int = 1) -> Screen:
    elems: list[Elem] = [Elem("text", "Settings"), Elem("button", "Search settings", "c", tag="search_icon"),
                         Elem("list", "", "s")]
    for e in entries:
        elems.append(Elem("button", e, "c", tag=f"setting:{e}"))
    noise(rng, elems, level)
    return Screen(app, "settings_list", elems, more=more, scroll=True)


def settings_detail(rng: random.Random, app: str, name: str, on: bool) -> Screen:
    elems = _up() + [Elem("text", name), Elem("switch", name, "c", tag="switch"),
                     Elem("text", "On" if on else "Off", tag="state")]
    if rng.random() < 0.6:
        elems.append(Elem("button", "Advanced", "c"))
    return Screen(app, "settings_detail", elems, state=dict(on=on))


def call_list(rng: random.Random, app: str, contacts: list[str], more: int = 0) -> Screen:
    elems: list[Elem] = [Elem("text", "Calls"), Elem("button", "Search", "cl", tag="search_icon"),
                         Elem("button", "More options", "cl", tag="menu")]
    elems += _topbar(app)
    elems.append(Elem("list", "", "s"))
    for c in contacts:
        elems.append(Elem("button", c, "cl", tag=f"contact:{c}"))
    elems.append(Elem("button", "New call", "cl", tag="fab"))
    elems += _bottom(app)
    return Screen(app, "call_list", elems, more=more, scroll=True)


def in_call(rng: random.Random, app: str, contact: str) -> Screen:
    return Screen(app, "in_call", [Elem("text", contact), Elem("text", "Calling"),
                                   Elem("button", "Mute", "cl"), Elem("button", "Speaker", "cl"),
                                   Elem("button", "End call", "cl", tag="end")])


def profile(rng: random.Random, app: str, owner: str) -> Screen:
    elems: list[Elem] = [Elem("text", owner), Elem("button", "Settings", "cl", tag="settings_btn"),
                         Elem("button", "Edit profile", "cl", tag="edit_profile"),
                         Elem("button", "Share profile", "cl")]
    elems += [Elem("image", "", "c") for _ in range(rng.randint(3, 6))]
    noise(rng, elems, 1)
    elems += _bottom(app)
    return Screen(app, "profile", elems, scroll=True, state=dict(owner=owner))


def file_list(rng: random.Random, app: str, folders: list[str], more: int = 0) -> Screen:
    elems: list[Elem] = [Elem("text", "Browse"), Elem("button", "Search", "cl", tag="search_icon"),
                         Elem("button", "More options", "cl", tag="menu"), Elem("list", "", "s")]
    for f in folders:
        elems.append(Elem("button", f, "cl", tag=f"folder:{f}"))
    noise(rng, elems, 1)
    elems += _bottom(app)
    return Screen(app, "file_list", elems, more=more, scroll=True)


def menu(rng: random.Random, app: str, items: list[str]) -> Screen:
    return Screen(app, "menu", [Elem("button", it, "cl", tag=f"menu:{it}") for it in items])


def dialog(rng: random.Random, app: str, kind: str, subject: str = "", label: str = "OK") -> Screen:
    """kind: permission | rate | cookie | confirm."""
    if kind == "permission":
        el = [Elem("text", "Turn on notifications?"), Elem("button", "Allow", "cl", tag="accept"),
              Elem("button", "Not now", "cl", tag="dismiss")]
    elif kind == "rate":
        el = [Elem("text", "Enjoying the app?"), Elem("button", "Rate now", "cl", tag="accept"),
              Elem("button", "Later", "cl", tag="dismiss")]
    elif kind == "cookie":
        el = [Elem("text", "Cookies on this app"), Elem("button", "Accept all", "cl", tag="accept"),
              Elem("button", "Reject all", "cl", tag="dismiss"), Elem("button", "Manage", "cl")]
        tail = el[1:]
        rng.shuffle(tail)
        el = [el[0]] + tail
    elif kind == "confirm":
        el = [Elem("text", f"{subject}?"), Elem("button", "Cancel", "cl", tag="cancel"),
              Elem("button", label, "cl", tag="confirm_btn")]
    else:
        raise ValueError(kind)
    return Screen(app, "dialog", el, state=dict(kind=kind))


def ad_popup(rng: random.Random, app: str) -> Screen:
    el = [Elem("button", "Close ad", "cl", tag="dismiss"), Elem("text", "Sponsored"), Elem("image", "", "c"),
          Elem("button", rng.choice(["Install", "Shop now", "Get offer"]), "cl", tag="ad_cta")]
    if rng.random() < 0.5:
        el.reverse()
    return Screen(app, "ad_popup", el)
