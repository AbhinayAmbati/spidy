#!/usr/bin/env python3
"""Verifies the model pins in ModelSpec.kt against what Hugging Face serves today.

For every ModelSpec the script sends a HEAD request to the pinned URL and compares:

  * the size   with X-Linked-Size (the LFS object size; Content-Length on the redirect is the
                 size of the redirect body, not the file), and
  * the SHA-256 with X-Linked-ETag (for an LFS file Hugging Face sets this to the sha256 of
                 the content).

A model that was replaced, re-uploaded or deleted upstream therefore fails CI here, before the
app ships a pin that every first-launch download would reject. Run it locally with:

    python3 tools/verify_model_pins.py

Exit status: 0 all pins match, 1 a pin does not match, 2 the check could not be completed
(network error, unparseable file). Both 1 and 2 fail CI: an unverifiable pin is not a verified one.

Set HF_TOKEN in the environment to send an Authorization header (only needed for gated or
private repositories; the current models are public).
"""

import os
import re
import sys
import time
import urllib.error
import urllib.request

MODEL_SPEC = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..",
    "app", "src", "main", "java", "com", "example", "agent", "core", "ai", "local", "ModelSpec.kt",
)

# One `ModelSpec(...)` call: capture its fields by name so field order does not matter.
SPEC_BLOCK = re.compile(r"ModelSpec\(\s*(.*?)\n\s*\)", re.DOTALL)
FIELD = {
    "id": re.compile(r'\bid\s*=\s*"([^"]+)"'),
    "url": re.compile(r'\burl\s*=\s*"([^"]+)"'),
    "size": re.compile(r"\bsizeBytes\s*=\s*([0-9_]+)L"),
    "sha256": re.compile(r'\bsha256\s*=\s*"([0-9a-fA-F]{64})"'),
}


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    """Stops at the first response: the LFS headers are on the 302, not on the CDN response."""

    def redirect_request(self, *args, **kwargs):
        return None


def parse_specs(path):
    with open(path, encoding="utf-8") as handle:
        source = handle.read()
    specs = []
    for block in SPEC_BLOCK.findall(source):
        # Skip the data class declaration itself (it has `val`, no string literals).
        if "val " in block.split("\n", 1)[0]:
            continue
        found = {name: pattern.search(block) for name, pattern in FIELD.items()}
        if not all(found.values()):
            continue
        specs.append({
            "id": found["id"].group(1),
            "url": found["url"].group(1),
            "size": int(found["size"].group(1).replace("_", "")),
            "sha256": found["sha256"].group(1).lower(),
        })
    return specs


def head(url, attempts=3):
    """Returns (status, headers) of the first response, retrying transient failures."""
    opener = urllib.request.build_opener(_NoRedirect)
    headers = {"User-Agent": "spidey-verify-model-pins"}
    token = os.environ.get("HF_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    last_error = None
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(url, method="HEAD", headers=headers)
        try:
            with opener.open(request, timeout=30) as response:
                return response.status, response.headers
        except urllib.error.HTTPError as error:
            if error.code in (301, 302, 303, 307, 308):
                return error.code, error.headers
            if error.code < 500 and error.code != 429:
                return error.code, error.headers
            last_error = error
        except (urllib.error.URLError, TimeoutError, ConnectionError) as error:
            last_error = error
        time.sleep(2 * attempt)
    raise RuntimeError(f"could not reach {url}: {last_error}")


def check(spec):
    """Returns a list of problems for one model; empty means the pin is verified."""
    status, headers = head(spec["url"])
    if status not in (200, 301, 302, 303, 307, 308):
        return [f"HTTP {status} for {spec['url']} (model removed, renamed or private?)"]

    linked_size = headers.get("X-Linked-Size")
    linked_etag = headers.get("X-Linked-ETag")
    if linked_size is None or linked_etag is None:
        return [
            "the response has no X-Linked-Size / X-Linked-ETag, so this is not an LFS file "
            "and its content hash cannot be verified from headers"
        ]

    problems = []
    if int(linked_size) != spec["size"]:
        problems.append(f"size: pinned {spec['size']}, Hugging Face has {int(linked_size)}")
    remote_sha = linked_etag.strip().removeprefix("W/").strip('"').lower()
    if remote_sha != spec["sha256"]:
        problems.append(f"sha256: pinned {spec['sha256']}, Hugging Face has {remote_sha}")
    return problems


def main():
    try:
        specs = parse_specs(MODEL_SPEC)
    except OSError as error:
        print(f"cannot read {MODEL_SPEC}: {error}", file=sys.stderr)
        return 2
    if not specs:
        print("no ModelSpec(...) found in ModelSpec.kt; the parser needs updating", file=sys.stderr)
        return 2

    failed = False
    for spec in specs:
        try:
            problems = check(spec)
        except RuntimeError as error:
            print(f"{spec['id']}: UNVERIFIED - {error}", file=sys.stderr)
            return 2
        if problems:
            failed = True
            print(f"{spec['id']}: MISMATCH", file=sys.stderr)
            for problem in problems:
                print(f"  - {problem}", file=sys.stderr)
        else:
            print(f"{spec['id']}: ok ({spec['size']} bytes, sha256 {spec['sha256'][:12]}...)")

    if failed:
        print(
            "\nA pinned model changed upstream. Do not just edit the pin: confirm the new file is "
            "the intended one, then update size and sha256 together (docs/RELEASING.md).",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
