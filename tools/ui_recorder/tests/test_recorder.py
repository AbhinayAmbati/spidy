import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
PKG = HERE.parent
sys.path.insert(0, str(PKG))

from explore import tap_allowed  # noqa: E402
from parse_dump import parse_dump  # noqa: E402
from recorder import Adb, AdbError, Capture, Recorder  # noqa: E402
from redact import Allowlist  # noqa: E402
from scanner import audit_dir, check_row  # noqa: E402
from serialize import serialize  # noqa: E402

CANARIES = [
    "+91 98765 43210", "9876543210", "jane.doe@example.com", "https://secret.example/x",
    "secret.example", "Jane Doe", "Rahul Verma", "See you at the secret meeting tomorrow at 7pm",
    "my bank pin is hidden", "@janedoe_99", "order 4482910", "best pizza near me",
    "Beautiful sunset at the beach caption", "hunter2",
]


def node(cls, text="", desc="", rid="", clickable=False, editable=False, scrollable=False,
         bounds="[0,0][400,100]", password=False, pkg="com.example.app"):
    def esc(s):
        return s.replace("&", "&amp;").replace('"', "&quot;").replace("<", "&lt;")
    return (f'<node class="android.widget.{cls}" package="{pkg}" text="{esc(text)}" '
            f'content-desc="{esc(desc)}" resource-id="{rid}" clickable="{str(clickable).lower()}" '
            f'long-clickable="false" scrollable="{str(scrollable).lower()}" enabled="true" '
            f'password="{str(password).lower()}" bounds="{bounds}"/>')


def dump(*nodes):
    return ('<?xml version="1.0"?><hierarchy rotation="0">' + "".join(nodes) +
            "</hierarchy>\nUI hierchary dumped to: /dev/tty")


def canary_dump():
    return dump(
        node("Button", "Search", clickable=True, bounds="[0,0][200,100]"),
        node("TextView", CANARIES[7], bounds="[0,110][900,160]"),
        node("TextView", "Jane Doe", bounds="[0,170][300,210]"),
        node("TextView", CANARIES[0], bounds="[0,220][300,260]"),
        node("TextView", CANARIES[2], desc=CANARIES[3], bounds="[0,270][300,300]"),
        node("Button", CANARIES[5], clickable=True, bounds="[0,310][300,360]"),
        node("Button", "", desc=CANARIES[8], clickable=True, bounds="[0,370][300,420]"),
        node("EditText", CANARIES[11], rid="com.x:id/search_box", clickable=True,
             bounds="[0,430][300,480]"),
        node("TextView", CANARIES[9] + " " + CANARIES[10], bounds="[0,490][300,540]"),
        node("TextView", CANARIES[12], clickable=True, bounds="[0,550][300,600]"),
        node("Button", "Like", clickable=True, bounds="[0,610][100,660]"),
        node("ListView", scrollable=True, bounds="[0,700][1000,2000]"),
    )


class Redaction(unittest.TestCase):
    def setUp(self):
        self.allow = Allowlist()
        self.allowed = self.allow.for_app("instagram")

    def test_canary_never_in_serialized_output(self):
        d = parse_dump(canary_dump(), self.allowed)
        ser = serialize(d)
        blob = ser.text + json.dumps([n.__dict__ for n in d.nodes], default=str)
        for c in CANARIES:
            self.assertNotIn(c.lower(), blob.lower(), c)
        for digits in re.findall(r"\d{4,}", "".join(CANARIES)):
            self.assertNotIn(digits, blob)
        self.assertIn('"Search"', ser.text)
        self.assertIn('"Like"', ser.text)
        self.assertIn("<item>", ser.text)

    def test_editable_text_and_hint_never_kept(self):
        d = parse_dump(dump(node("EditText", "Search", rid="a:id/q", clickable=True),
                            node("Button", "Back", clickable=True, bounds="[0,200][50,250]"),
                            node("Button", "Back", clickable=True, bounds="[0,300][50,350]")),
                       self.allowed)
        edit = [n for n in d.nodes if n.editable][0]
        self.assertIsNone(edit.label)

    def test_noninteractive_free_text_is_dropped(self):
        d = parse_dump(dump(node("TextView", "hello there general"),
                            node("Button", "Send", clickable=True, bounds="[0,200][50,250]"),
                            node("Button", "Send", clickable=True, bounds="[0,300][50,350]")),
                       self.allowed)
        self.assertNotIn("hello", serialize(d).text)

    def test_end_to_end_row_passes_scanner_and_file_is_clean(self):
        d = parse_dump(canary_dump(), self.allowed)
        cap = Capture(d, serialize(d), None)
        with tempfile.TemporaryDirectory() as tmp:
            rec = Recorder(Path(tmp))
            ok = rec.log_step("instagram", 1, "go to search", cap,
                              {"type": "tap", "target_id": 1, "target_label": "Search"}, cap)
            self.assertTrue(ok)
            text = (Path(tmp) / "instagram.jsonl").read_text(encoding="utf-8")
            for c in CANARIES:
                self.assertNotIn(c.lower(), text.lower(), c)
            self.assertEqual(audit_dir(Path(tmp)), [])

    def test_secure_screen_is_logged_blocked_without_content(self):
        with tempfile.TemporaryDirectory() as tmp:
            rec = Recorder(Path(tmp))
            blocked = Capture(None, None, "secure_or_empty")
            self.assertFalse(rec.log_step("telegram", 0, "open telegram", blocked,
                                          {"type": "open_app"}, blocked))
            row = json.loads((Path(tmp) / "telegram.jsonl").read_text())
            self.assertEqual(row["blocked"], True)
            self.assertNotIn("screen", row)


class Scanner(unittest.TestCase):
    def check(self, **fields):
        return check_row(fields, Allowlist().all_labels(), set())

    def test_rejects_each_canary_injected_raw(self):
        for c in CANARIES:
            screen = f'app:x scroll:no\n1 button "{c}" c'
            self.assertTrue(self.check(screen=screen), c)
        self.assertTrue(self.check(goal="call +91 98765 43210"))
        self.assertTrue(self.check(extra="mail jane.doe@example.com"))
        self.assertTrue(self.check(extra="a" * 120))

    def test_accepts_clean_row(self):
        screen = 'app:instagram scroll:yes\n1 button "Search" c\n2 button "<item>" c'
        self.assertEqual(self.check(screen=screen, next_screen=screen, goal="go to Search"), [])

    def test_known_id_labels_only_when_seen(self):
        screen = 'app:x scroll:no\n1 edit "search_box" ce'
        self.assertTrue(check_row({"screen": screen}, set(), set()))
        self.assertEqual(check_row({"screen": screen}, set(), {"search_box"}), [])

    def test_audit_flags_images(self):
        with tempfile.TemporaryDirectory() as tmp:
            (Path(tmp) / "x.png").write_bytes(b"x")
            self.assertTrue(audit_dir(Path(tmp)))


class Safety(unittest.TestCase):
    def test_tap_gate(self):
        allow = Allowlist()
        d = parse_dump(dump(
            node("Button", "Like", clickable=True), node("Button", "Send", clickable=True),
            node("Button", "Reels", clickable=True), node("Button", "Follow", clickable=True),
            node("Switch", "Wi-Fi", clickable=True), node("Button", "Stranger", clickable=True),
            node("Button", "Search", clickable=True)), allow.for_app("instagram"))
        got = {n.label for n in d.nodes if tap_allowed(n, allow.nav_tap(), allow.deny_tap())}
        self.assertEqual(got, {"Reels", "Search"})

    def test_deny_beats_nav(self):
        allow = Allowlist()
        self.assertEqual(allow.deny_tap() & allow.nav_tap(), set())

    def test_adb_rejects_unlisted_commands(self):
        adb = Adb(adb_path="adb")
        for bad in (("shell", "screencap", "-p"), ("shell", "input", "text", "hi"),
                    ("shell", "pm", "uninstall", "x"), ("pull", "/sdcard/x"),
                    ("shell", "settings", "put", "global", "a", "b")):
            with self.assertRaises(AdbError):
                adb.run(*bad)

    def test_source_has_no_network_or_image_capture(self):
        banned = re.compile(r"^\s*(import|from)\s+(socket|urllib|http|requests|ftplib|smtplib|ssl|"
                            r"webbrowser|xmlrpc|asyncio)\b", re.M)
        for f in PKG.glob("*.py"):
            src = f.read_text(encoding="utf-8")
            self.assertIsNone(banned.search(src), f.name)
            for word in ("screencap", "screenrecord", "PIL", "pytesseract"):
                self.assertNotIn(word, src, f"{f.name} mentions {word}")


if __name__ == "__main__":
    unittest.main()
