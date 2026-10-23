"""Task recipes: each builds a whole trajectory (goal + screens + correct action per step) by construction."""
from __future__ import annotations

import random
from dataclasses import dataclass, field

import screens as S
from screens import Elem, Screen
from vocab import (APPS, FOLDERS, MESSAGES, QUERIES, RESULTS, RISKY_MENU, SAFE_MENU, SETTINGS, TOGGLES)


@dataclass
class Step:
    screen: Screen
    name: str                      # tap | type | scroll | back | home | open_app | finish
    elem: Elem | None = None       # tap target
    args: dict = field(default_factory=dict)
    why: str = ""


@dataclass
class Traj:
    goal: str
    app: str
    kind: str
    steps: list[Step]


# ---------------------------------------------------------------- phrasing

SPOKEN_PRE = ["hey ", "please ", "okay ", "um ", "can you ", "i want to ", "could you ", "go and ", ""]
SPOKEN_POST = ["", "", " for me", " please", " now", " real quick"]


def say(rng: random.Random, forms: list[str], **kw) -> str:
    base = rng.choice(forms).format(**kw)
    if rng.random() < 0.45:  # spoken: lowercase, no punctuation, fillers
        base = base.lower().replace(",", "").replace(".", "").replace("?", "")
        return rng.choice(SPOKEN_PRE) + base + rng.choice(SPOKEN_POST)
    return base


def _key(app: str) -> str:
    return APPS[app]["name"].lower()


def _all_launcher_apps(rng: random.Random, app: str) -> list[str]:
    others = [a for a in APPS if a != app]
    return [app] + rng.sample(others, rng.randint(5, 8))


# ---------------------------------------------------------------- navigation helpers

def home_screen(rng: random.Random, app: str, **kw) -> Screen:
    t = APPS[app]["templates"]
    if "feed" in t and app != "google":
        return S.feed(rng, app, **kw)
    if app == "google":
        return S.feed(rng, app, n_posts=2)
    if "chat_list" in t:
        return S.chat_list(rng, app, S.people(rng, rng.randint(4, 6)))
    if "settings_list" in t:
        return S.settings_list(rng, app, rng.sample(SETTINGS, 8), more=12)
    if "file_list" in t:
        return S.file_list(rng, app, rng.sample(FOLDERS, 6))
    return S.search(rng, app)


def reach(rng: random.Random, app: str, steps: list[Step], home_kw: dict | None = None,
          force_open: bool = False) -> Screen:
    """Maybe start outside the app (-> open_app step). Returns the app's first screen."""
    home_kw = home_kw or {}
    if force_open or rng.random() < 0.5:
        if rng.random() < 0.6:
            start = S.launcher(rng, _all_launcher_apps(rng, app))
        else:
            other = rng.choice([a for a in APPS if a != app])
            start = home_screen(rng, other)
        steps.append(Step(start, "open_app", args={"app": _key(app)}, why=f"goal needs {APPS[app]['name']}; not open"))
    return home_screen(rng, app, **home_kw)


def _dismiss_step(rng: random.Random, app: str, steps: list[Step]) -> None:
    """Optionally insert a blocking dialog / ad before continuing."""
    r = rng.random()
    if r < 0.14 and "dialog" in APPS[app]["templates"]:
        d = S.dialog(rng, app, rng.choice(["permission", "rate", "cookie"]))
        steps.append(Step(d, "tap", d.find("dismiss"), why="dialog blocks the screen; dismiss it"))
    elif r < 0.22 and "feed" in APPS[app]["templates"]:
        d = S.ad_popup(rng, app)
        steps.append(Step(d, "tap", d.find("dismiss"), why="ad popup blocks the screen; close it"))


def finish(scr: Screen, status: str = "done", why: str = "goal satisfied") -> Step:
    return Step(scr, "finish", args={"status": status}, why=why)


def tab_screen(rng: random.Random, app: str, label: str) -> Screen:
    label = label.lower()
    if label in ("search",):
        return S.search(rng, app)
    if label in ("profile", "you"):
        return S.profile(rng, app, S.person(rng))
    if label in ("messages", "chats"):
        return S.chat_list(rng, app, S.people(rng, 5))
    if label == "calls":
        return S.call_list(rng, app, S.people(rng, 5))
    if label == "settings":
        return S.settings_list(rng, app, rng.sample(SETTINGS, 8), more=10)
    if label in ("browse", "recent"):
        return S.file_list(rng, app, rng.sample(FOLDERS, 6))
    return home_screen(rng, app)


def go_tab(rng: random.Random, app: str, scr: Screen, label: str, steps: list[Step]) -> Screen:
    t = scr.find(f"tab:{label.lower()}")
    assert t is not None, (app, label)
    steps.append(Step(scr, "tap", t, why=f"open the {label} tab"))
    return tab_screen(rng, app, label)


def _tab_labels(app: str) -> list[str]:
    t = APPS[app]["tabs"]
    return [x.lower() for x in t[1]] if t else []


# ---------------------------------------------------------------- recipes

def r_open_app(rng, app) -> Traj:
    steps: list[Step] = []
    if rng.random() < 0.6:
        start = S.launcher(rng, _all_launcher_apps(rng, app))
    else:
        start = home_screen(rng, rng.choice([a for a in APPS if a != app]))
    name = APPS[app]["name"]
    goal = say(rng, ["Open {n}", "Launch {n}", "Start {n}", "Go to {n}", "Open the {n} app", "Switch to {n}"], n=name)
    steps.append(Step(start, "open_app", args={"app": _key(app)}, why=f"goal names the app {name}"))
    steps.append(finish(home_screen(rng, app)))
    return Traj(goal, app, "open_app", steps)


def r_feed_action(rng, app) -> Traj:
    steps: list[Step] = []
    verb = rng.choice(["like", "like", "comment", "share"])
    scr = reach(rng, app, steps, dict(ads=rng.random() < 0.3))
    if not steps and rng.random() < 0.3:      # already in app but on a pushed search screen -> back
        steps.append(Step(S.search(rng, app), "back", why="need the feed; go back"))
    _dismiss_step(rng, app, steps)
    n = scr.state["n"]
    idx = rng.randrange(n)
    ordinal = ["first", "second", "third"][idx]
    author = scr.state["authors"][idx]
    if rng.random() < 0.35:
        forms = {"like": ["Like the post by {a}", "Like {a}'s post"],
                 "comment": ["Open comments on the post by {a}", "Comment on {a}'s post"],
                 "share": ["Share the post by {a}", "Share {a}'s post"]}[verb]
    else:
        forms = {"like": ["Like the {o} post", "Like the {o} post on the feed", "Double tap like on the {o} post"],
                 "comment": ["Open comments on the {o} post", "Comment on the {o} post"],
                 "share": ["Share the {o} post", "Share the {o} post with someone"]}[verb]
    goal = say(rng, forms, o=ordinal, a=author)
    steps.append(Step(scr, "tap", scr.find(f"{verb}:{idx}"), why=f"{verb} button of the {ordinal} post"))
    if verb == "like":
        after = S.feed(rng, app, n_posts=n, liked=frozenset({idx}), authors=scr.state["authors"])
    elif verb == "comment":
        after = Screen(app, "comments", [Elem("text", "Comments"), Elem("edit", "Add a comment", "cle"),
                                         Elem("button", "Close", "cl")])
    else:
        after = Screen(app, "share_sheet", [Elem("text", "Share to"), Elem("button", "Copy link", "cl"),
                                            Elem("button", "Close", "cl")])
    steps.append(finish(after))
    return Traj(goal, app, "feed_action", steps)


def r_search(rng, app) -> Traj:
    steps: list[Step] = []
    scr = reach(rng, app, steps)
    _dismiss_step(rng, app, steps)
    q = rng.choice(QUERIES)
    open_first = rng.random() < 0.35
    forms = ["Search for {q}", "Search {q}", "Look up {q}", "Find {q}", "Search for {q} in the app"]
    if open_first:
        forms = ["Search for {q} and open the first result", "Find {q} and open the top result"]
    goal = say(rng, forms, q=q)
    if scr.template != "search":
        tab = scr.find("tab:search")
        if tab is not None:
            scr = go_tab(rng, app, scr, "Search", steps)
        else:
            ic = scr.find("search_icon")
            steps.append(Step(scr, "tap", ic, why="open search"))
            scr = S.search(rng, app)
    steps.append(Step(scr, "type", args={"text": q}, why="type the query into the search field"))
    results = [f"{q.split()[0].capitalize()} {r}" for r in rng.sample(RESULTS, rng.randint(3, 5))]
    res = S.search(rng, app, query=q, results=results)
    if open_first:
        steps.append(Step(res, "tap", res.find("result:0"), why="open the first result"))
        steps.append(finish(S.Screen(app, "detail", [Elem("button", "Navigate up", "cl"), Elem("text", results[0])])))
    else:
        steps.append(finish(res))
    return Traj(goal, app, "search", steps)


def r_send_message(rng, app) -> Traj:
    steps: list[Step] = []
    scr = reach(rng, app, steps)
    _dismiss_step(rng, app, steps)
    if scr.template != "chat_list":
        scr = go_tab(rng, app, scr, "Messages", steps) if scr.find("tab:messages") else scr
    contacts = S.people(rng, rng.randint(4, 6))
    contact = contacts[rng.randrange(len(contacts))]
    scr = S.chat_list(rng, app, contacts)
    text = rng.choice(MESSAGES)
    goal = say(rng, ["Send {t} to {c}", "Message {c} {t}", "Text {c} saying {t}", "Tell {c} {t}",
                     "Send a message to {c} saying {t}"], t=text, c=contact)
    # sometimes the contact is below the fold
    if rng.random() < 0.3:
        hidden = [c for c in contacts if c != contact]
        s1 = S.chat_list(rng, app, hidden[:4], more=rng.randint(3, 9))
        steps.append(Step(s1, "scroll", args={"direction": "down"}, why=f"{contact} not visible; scroll down"))
        scr = S.chat_list(rng, app, hidden[2:4] + [contact] + hidden[4:5])
    steps.append(Step(scr, "tap", scr.find(f"contact:{contact}"), why=f"open the chat with {contact}"))
    cv = S.chat_view(rng, app, contact)
    steps.append(Step(cv, "type", args={"text": text}, why="type the message"))
    cv2 = S.chat_view(rng, app, contact, typed=text)
    steps.append(Step(cv2, "tap", cv2.find("send"), why="send the typed message"))
    steps.append(finish(S.chat_view(rng, app, contact, sent=[text])))
    return Traj(goal, app, "send_message", steps)


def r_toggle(rng, app) -> Traj:
    steps: list[Step] = []
    scr = reach(rng, app, steps)
    _dismiss_step(rng, app, steps)
    name = rng.choice(TOGGLES)
    want_on = rng.random() < 0.5
    is_on = rng.random() < 0.5
    verb = "on" if want_on else "off"
    goal = say(rng, ["Turn {v} {n}", "Switch {v} {n}", "Turn {n} {v}", "Enable {n}" if want_on else "Disable {n}"],
               v=verb, n=name)
    others = [s for s in SETTINGS if s != name]
    below = rng.random() < 0.4
    if below:
        s1 = S.settings_list(rng, app, rng.sample(others, 8), more=rng.randint(4, 12))
        steps.append(Step(s1, "scroll", args={"direction": "down"}, why=f"{name} not visible; scroll down"))
        entries = rng.sample(others, 5)
        entries.insert(rng.randint(1, 4), name)
        scr = S.settings_list(rng, app, entries)
    else:
        entries = rng.sample(others, 7)
        entries.insert(rng.randint(0, 7), name)
        scr = S.settings_list(rng, app, entries, more=rng.randint(0, 6))
    steps.append(Step(scr, "tap", scr.find(f"setting:{name}"), why=f"open {name}"))
    det = S.settings_detail(rng, app, name, is_on)
    if is_on == want_on:
        steps.append(finish(det, why=f"{name} is already {verb}"))
    else:
        steps.append(Step(det, "tap", det.find("switch"), why=f"flip {name} {verb}"))
        steps.append(finish(S.settings_detail(rng, app, name, want_on)))
    return Traj(goal, app, "toggle_setting", steps)


def r_call(rng, app) -> Traj:
    steps: list[Step] = []
    scr = reach(rng, app, steps)
    _dismiss_step(rng, app, steps)
    if scr.template != "call_list":
        scr = go_tab(rng, app, scr, "Calls", steps)
    contacts = S.people(rng, rng.randint(4, 6))
    contact = rng.choice(contacts)
    scr = S.call_list(rng, app, contacts)
    goal = say(rng, ["Call {c}", "Ring {c}", "Phone {c}", "Make a call to {c}", "Call {c} from the calls list"], c=contact)
    steps.append(Step(scr, "tap", scr.find(f"contact:{contact}"), why=f"call {contact}"))
    steps.append(finish(S.in_call(rng, app, contact)))
    return Traj(goal, app, "call", steps)


def r_profile(rng, app) -> Traj:
    steps: list[Step] = []
    scr = reach(rng, app, steps)
    _dismiss_step(rng, app, steps)
    label = "Profile" if app == "instagram" else "You"
    goal = say(rng, ["Open my profile", "Go to my profile", "Show my profile", "Open my {l} tab"], l=label.lower())
    scr = go_tab(rng, app, scr, label, steps)
    steps.append(finish(scr))
    return Traj(goal, app, "profile", steps)


RISKY_GOALS = {
    "Delete chat": (["Delete this chat", "Delete the chat", "Remove this conversation"], "Delete"),
    "Log out": (["Log out of the app", "Sign me out", "Log out"], "Log out"),
    "Clear history": (["Clear the chat history", "Clear history", "Wipe this chat history"], "Clear"),
}


def r_menu(rng, app) -> Traj:
    steps: list[Step] = []
    if "chat_view" in APPS[app]["templates"] and rng.random() < 0.4:
        item = rng.choice(RISKY_MENU)
        goal = say(rng, RISKY_GOALS[item][0])
        scr = S.chat_view(rng, app, S.person(rng))
        steps.append(Step(scr, "tap", scr.find("menu"), why="risky action lives in the overflow menu"))
        items = rng.sample(SAFE_MENU, 3)
        items.insert(rng.randint(0, 3), item)
        m = S.menu(rng, app, items)
        steps.append(Step(m, "tap", m.find(f"menu:{item}"), why=f"choose {item}"))
        d = S.dialog(rng, app, "confirm", subject=item, label=RISKY_GOALS[item][1])
        steps.append(finish(d, "confirm", "destructive action needs the user's confirmation"))
        return Traj(goal, app, "risky_menu", steps)
    scr = reach(rng, app, steps)
    _dismiss_step(rng, app, steps)
    item = rng.choice(SAFE_MENU)
    if scr.find("menu") is None:
        scr = S.chat_list(rng, app, S.people(rng, 5)) if "chat_list" in APPS[app]["templates"] else             S.file_list(rng, app, rng.sample(FOLDERS, 5))
    goal = say(rng, ["Open the menu and tap {i}", "Choose {i} from the menu", "Open more options then {i}",
                     "Go to {i} from the overflow menu"], i=item)
    steps.append(Step(scr, "tap", scr.find("menu"), why="open the overflow menu"))
    items = rng.sample([x for x in SAFE_MENU if x != item], 3)
    items.insert(rng.randint(0, 3), item)
    m = S.menu(rng, app, items)
    steps.append(Step(m, "tap", m.find(f"menu:{item}"), why=f"choose {item}"))
    steps.append(finish(Screen(app, "detail", [Elem("button", "Navigate up", "cl"), Elem("text", item)])))
    return Traj(goal, app, "menu", steps)


def r_scroll(rng, app) -> Traj:
    steps: list[Step] = []
    d = rng.choice(["down", "down", "up"])
    scr = home_screen(rng, app)
    scr.scroll = True
    scr.more = scr.more or rng.randint(2, 8)
    forms = ["Scroll {d}", "Scroll {d} the page", "Swipe {d}", "Scroll {d} a bit"]
    if d == "down":
        forms += ["Show me more", "See more posts"]
    goal = say(rng, forms, d=d)
    steps.append(Step(scr, "scroll", args={"direction": d}, why=f"goal asks to scroll {d}"))
    steps.append(finish(home_screen(rng, app)))
    return Traj(goal, app, "scroll", steps)


def r_back_home(rng, app) -> Traj:
    steps: list[Step] = []
    if rng.random() < 0.5:
        scr = S.search(rng, app) if "search" in APPS[app]["templates"] else S.settings_detail(rng, app, "Wi-Fi", True)
        goal = say(rng, ["Go back", "Go back to the previous screen", "Press back", "Back"])
        steps.append(Step(scr, "back", why="goal asks to go back"))
        steps.append(finish(home_screen(rng, app)))
        return Traj(goal, app, "back", steps)
    scr = home_screen(rng, app)
    goal = say(rng, ["Go to the home screen", "Go home", "Press home", "Close this app", "Take me to the home screen"])
    steps.append(Step(scr, "home", why="goal asks for the home screen"))
    steps.append(finish(S.launcher(rng, _all_launcher_apps(rng, app))))
    return Traj(goal, app, "home", steps)


def r_impossible(rng, app) -> Traj:
    """Explicit control that does not exist here, and nothing to scroll or navigate to."""
    steps: list[Step] = []
    if rng.random() < 0.5:
        name = rng.choice(["Retweet", "Reply all", "Donate", "Pay now", "Subscribe", "Buy"])
        scr = S.settings_detail(rng, "settings" if rng.random() < 0.5 else app, rng.choice(TOGGLES), rng.random() < 0.5)
        goal = say(rng, ["Tap {n}", "Press the {n} button", "Click {n}"], n=name)
    else:
        scr = S.settings_detail(rng, app, rng.choice(TOGGLES), rng.random() < 0.5)
        goal = say(rng, ["Type {t} in the box", "Type {t} into the search field", "Write {t} in the text field"],
                   t=rng.choice(QUERIES))
        name = "text field"
    steps.append(finish(scr, "impossible", f"{name} is not on this screen and nothing can reveal it"))
    return Traj(goal, scr.app if scr.app in APPS else app, "impossible", steps)


def r_dismiss(rng, app) -> Traj:
    steps: list[Step] = []
    t = APPS[app]["templates"]
    if "dialog" not in t:
        return r_open_app(rng, app)
    kind = rng.choice(["permission", "rate", "cookie"])
    d = S.dialog(rng, app, kind)
    goal = say(rng, ["Close the popup", "Dismiss this dialog", "Get rid of the popup", "Skip this", "Not now"])
    steps.append(Step(d, "tap", d.find("dismiss"), why="decline the popup"))
    steps.append(finish(home_screen(rng, app)))
    return Traj(goal, app, "dismiss", steps)


RECIPES = {
    "open_app": (r_open_app, lambda a: True, 3),
    "feed_action": (r_feed_action, lambda a: a in ("instagram", "twitter", "youtube"), 4),
    "search": (r_search, lambda a: True, 3),
    "send_message": (r_send_message, lambda a: a in ("whatsapp", "telegram", "instagram"), 4),
    "toggle": (r_toggle, lambda a: a == "settings", 4),
    "call": (r_call, lambda a: a in ("whatsapp", "telegram"), 2),
    "profile": (r_profile, lambda a: a in ("instagram", "youtube"), 2),
    "menu": (r_menu, lambda a: a in ("whatsapp", "telegram", "gmail", "files"), 3),
    "scroll": (r_scroll, lambda a: True, 1),
    "back_home": (r_back_home, lambda a: True, 1),
    "impossible": (r_impossible, lambda a: True, 1),
    "dismiss": (r_dismiss, lambda a: True, 1),
}


def make_traj(rng: random.Random) -> Traj:
    while True:
        app = rng.choice(list(APPS))
        opts = [(n, w) for n, (_, ok, w) in RECIPES.items() if ok(app)]
        name = rng.choices([n for n, _ in opts], weights=[w for _, w in opts])[0]
        return RECIPES[name][0](rng, app)
