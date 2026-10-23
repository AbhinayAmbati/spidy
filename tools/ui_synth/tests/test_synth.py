import json
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
PKG = HERE.parent
sys.path.insert(0, str(PKG))

import generate  # noqa: E402
import mix  # noqa: E402
from screens import TOKEN_BUDGET, est_tokens  # noqa: E402
from vocab import APPS, FIRST, LAST, TEST_APPS, TEST_TEMPLATES  # noqa: E402

HEADER = re.compile(r"^app:[a-z]+ scroll:(yes|no)$")
LINE = re.compile(r'^(\d+) (button|image|text|edit|switch|tab|list|other)(?: "([^"]{1,24})")?(?: ([clesx]+))?$')
MORE = re.compile(r"^\+\d+ more$")
UI_NAMES = {"tap", "type", "scroll", "back", "home", "open_app", "finish"}

TRAIN, DEV, TEST = generate.generate(400, seed=7)
ALL = TRAIN + DEV + TEST


def parse(query):
    """-> (goal line, history line, screen lines)."""
    goal, hist, rest = query.split("\n", 2)
    return goal, hist, rest.split("screen:\n", 1)[1].split("\n")


class Labels(unittest.TestCase):
    def test_every_screen_line_is_valid(self):
        for row, _ in ALL:
            _, _, lines = parse(row["query"])
            self.assertRegex(lines[0], HEADER)
            body = lines[1:]
            if body and MORE.match(body[-1]):
                body = body[:-1]
            for i, ln in enumerate(body, 1):
                m = LINE.match(ln)
                self.assertIsNotNone(m, ln)
                self.assertEqual(int(m.group(1)), i)

    def test_action_names_and_args(self):
        for row, _ in ALL:
            self.assertEqual(len(row["answers"]), 1)
            a = row["answers"][0]
            self.assertIn(a["name"], UI_NAMES)
            args = a["arguments"]
            if a["name"] == "scroll":
                self.assertIn(args["direction"], ("up", "down"))
            if a["name"] == "finish":
                self.assertIn(args["status"], ("done", "impossible", "confirm"))
            if a["name"] == "open_app":
                self.assertIn(args["app"], {v["name"].lower() for v in APPS.values()})
            self.assertEqual({t["name"] for t in row["tools"]}, UI_NAMES)
            self.assertEqual(set(row), {"query", "tools", "answers", "reasoning"})


class CorrectActionExists(unittest.TestCase):
    def test_tap_target_is_on_screen_with_expected_label(self):
        taps = 0
        for row, meta in ALL:
            a = row["answers"][0]
            _, _, lines = parse(row["query"])
            body = [ln for ln in lines[1:] if not MORE.match(ln)]
            if a["name"] == "tap":
                taps += 1
                i = a["arguments"]["id"]
                self.assertTrue(1 <= i <= len(body))
                m = LINE.match(body[i - 1])
                self.assertIn("c", m.group(4) or "")                      # tappable
                self.assertNotIn("x", m.group(4) or "")                   # not disabled
                self.assertEqual(m.group(3), meta["target_label"][:24])
            elif a["name"] == "type":
                self.assertTrue(any(" edit" in ln for ln in body), "type needs a text field")
            elif a["name"] == "scroll":
                self.assertTrue(lines[0].endswith("scroll:yes"))
        self.assertGreater(taps, 100)

    def test_every_action_kind_and_negative_present(self):
        names = {r["answers"][0]["name"] for r, _ in ALL}
        self.assertEqual(names, UI_NAMES)
        statuses = {r["answers"][0]["arguments"]["status"] for r, _ in ALL if r["answers"][0]["name"] == "finish"}
        self.assertEqual(statuses, {"done", "impossible", "confirm"})
        kinds = {m["kind"] for _, m in ALL}
        for k in ("send_message", "search", "toggle_setting", "feed_action", "risky_menu", "impossible", "dismiss"):
            self.assertIn(k, kinds)

    def test_multistep_history(self):
        self.assertTrue(any("history: open_app" in r["query"] and "tap(" in r["query"] for r, _ in ALL))
        self.assertTrue(any("history: none" in r["query"] for r, _ in ALL))
        self.assertTrue(any("scroll(down)" in parse(r["query"])[1] for r, _ in ALL))


class Determinism(unittest.TestCase):
    def test_same_seed_same_rows(self):
        a = generate.generate(60, seed=3)
        b = generate.generate(60, seed=3)
        c = generate.generate(60, seed=4)

        def dump(x):
            return json.dumps([[r for r, _ in s] for s in x])
        self.assertEqual(dump(a), dump(b))
        self.assertNotEqual(dump(a), dump(c))


class TokenCap(unittest.TestCase):
    def test_screen_and_total_within_budget(self):
        for row, _ in ALL:
            _, _, lines = parse(row["query"])
            self.assertLessEqual(est_tokens("\n".join(lines)), TOKEN_BUDGET)
            total = est_tokens(row["query"]) + est_tokens(json.dumps(row["tools"]))
            self.assertLessEqual(total, 900, "must fit the 1,024-token window with the answer")


class Privacy(unittest.TestCase):
    PATTERNS = [re.compile(p) for p in (r"\d", r"@", r"https?:", r"www\.", r"\.com\b")]

    def test_no_digits_emails_urls_handles(self):
        for row, _ in ALL:
            goal, hist, lines = parse(row["query"])
            screen_text = re.sub(r"^\d+ ", "", "\n".join(lines[1:]), flags=re.M)
            screen_text = re.sub(r"^\+\d+ more$", "", screen_text, flags=re.M)
            hist = re.sub(r"\(\d+ ", "(", hist)
            ans = re.sub(r'"id": \d+', "", json.dumps(row["answers"]))
            for blob in (goal, hist, screen_text, ans, row["reasoning"]):
                for p in self.PATTERNS:
                    self.assertIsNone(p.search(blob), (p.pattern, blob[:80]))

    def test_person_names_come_only_from_fictional_pools(self):
        seen = 0
        for row, meta in ALL:
            if (meta["target_tag"] or "").startswith("contact:"):
                first, last = meta["target_label"].split()
                self.assertIn(first, FIRST)
                self.assertIn(last, LAST)
                seen += 1
        self.assertGreater(seen, 10)


class NoRecordedDataLeakage(unittest.TestCase):
    def test_sources_never_touch_recorder_output_or_network(self):
        for py in PKG.glob("*.py"):
            src = py.read_text(encoding="utf8")
            for bad in ("ui_recorder", "socket", "urllib", "requests", "http.client"):
                self.assertNotIn(bad, src, f"{py.name}: {bad}")

    def test_no_generated_screen_equals_a_recorded_screen(self):
        cands = [PKG.parent / "ui_recorder" / "out"]
        if os.environ.get("UI_RECORDER_OUT"):
            cands.append(Path(os.environ["UI_RECORDER_OUT"]))
        recorded = set()
        for d in cands:
            for f in (d.glob("*.jsonl") if d.is_dir() else []):
                for ln in f.read_text(encoding="utf8").splitlines():
                    try:
                        r = json.loads(ln)
                    except ValueError:
                        continue
                    for k in ("screen", "next_screen"):
                        if isinstance(r.get(k), str):
                            recorded.add(r[k].strip())
        for row, _ in ALL:
            self.assertNotIn("\n".join(parse(row["query"])[2]).strip(), recorded)

    def test_gitignore_covers_out(self):
        root = PKG.parent.parent
        self.assertIn("tools/ui_synth/out", (root / ".gitignore").read_text(encoding="utf8"))


class Splits(unittest.TestCase):
    def test_train_has_no_heldout_template_or_app(self):
        for _, m in TRAIN + DEV:
            self.assertNotIn(m["template"], TEST_TEMPLATES)
            self.assertNotIn(m["app"], TEST_APPS)

    def test_test_covers_both_holdouts_and_is_disjoint(self):
        self.assertEqual({m["heldout"] for _, m in TEST}, {"template", "app"})
        self.assertTrue(all(m["template"] in TEST_TEMPLATES or m["app"] in TEST_APPS for _, m in TEST))
        tr = {r["query"] for r, _ in TRAIN + DEV}
        self.assertFalse(tr & {r["query"] for r, _ in TEST})

    def test_dev_split_exists(self):
        self.assertGreater(len(DEV), 0)


class Mixing(unittest.TestCase):
    def test_share(self):
        ui = [json.dumps({"u": i}) for i in range(70)]
        base = [json.dumps({"b": i}) for i in range(500)]
        out = mix.mix(ui, base, 0.3, seed=1)
        self.assertEqual(len(out), 100)
        self.assertEqual(sum('"b"' in r for r in out), 30)
        self.assertEqual(out, mix.mix(ui, base, 0.3, seed=1))
        out2 = mix.mix(ui, base, 0.5, seed=1, total=60)
        self.assertEqual((len(out2), sum('"b"' in r for r in out2)), (60, 30))

    def test_cli(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            (d / "u.jsonl").write_text("\n".join(json.dumps({"u": i}) for i in range(10)) + "\n")
            (d / "b.jsonl").write_text("\n".join(json.dumps({"b": i}) for i in range(10)) + "\n")
            self.assertEqual(mix.main(["--ui", str(d / "u.jsonl"), "--base", str(d / "b.jsonl"),
                                       "--share", "0.5", "--out", str(d / "o.jsonl")]), 0)
            self.assertEqual(len((d / "o.jsonl").read_text().splitlines()), 20)


if __name__ == "__main__":
    unittest.main()
