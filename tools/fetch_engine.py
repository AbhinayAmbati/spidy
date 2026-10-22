#!/usr/bin/env python3
"""Fetches the vendor inference engine (libneedle.a + needle.h, Cactus Compute) that runs spidey.cact.

The engine is not committed. Run this once before building with on-device inference:

    python tools/fetch_engine.py

Each file is checked against a pinned SHA-256, so a changed upstream file fails the build
instead of silently entering the app. To update the engine, re-read the files on
https://huggingface.co/Cactus-Compute/needle3 and change the pins together with the change.

The model weights (spidey.cact, whistle.cact) are NOT fetched here: the app downloads them
on first launch.
"""

import hashlib
import os
import sys
import urllib.request

BASE = "https://huggingface.co/Cactus-Compute/needle3/resolve/main/android-arm64/"

# name -> (size in bytes, sha256)
FILES = {
    "libneedle.a": (2177676, "7aa6de6132a22bf45f5e903425feb66f159ad46f47fa558921bc4fd02c3a2fa0"),
    "needle.h": (4528, "a27dac4b3239eead91d124b7f7dbce5495c13019903fd817b68c16d39b055a99"),
}

DESTINATION = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "cpp", "engine")


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(name, size, expected):
    target = os.path.join(DESTINATION, name)
    if os.path.exists(target) and sha256_of(target) == expected:
        print(f"{name}: already present")
        return True

    partial = target + ".part"
    urllib.request.urlretrieve(BASE + name, partial)

    actual = sha256_of(partial)
    if os.path.getsize(partial) != size or actual != expected:
        os.remove(partial)
        print(f"{name}: checksum mismatch (got {actual}); refusing to use it", file=sys.stderr)
        return False

    os.replace(partial, target)
    print(f"{name}: ok")
    return True


def main():
    os.makedirs(DESTINATION, exist_ok=True)
    results = [fetch(name, size, digest) for name, (size, digest) in FILES.items()]
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
