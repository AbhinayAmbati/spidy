#!/usr/bin/env python3
"""Feasibility spike: how well does Needle 3 drive Spidey's tools, on a real phone?

Runs Cactus's own `needle` command-line runner on an arm64 Android phone over adb, so it
needs none of the app's code. For each case in cases.json it sends the prompt with Spidey's
16 tools and checks the calls that come back.

Setup (once):
  1. Install Android platform-tools (adb) and enable USB debugging on the phone.
  2. Connect the phone and accept the debugging prompt; `adb devices` must list it.

Run:
    python tools/spidey_eval/run_eval.py

It downloads the runner and the model (about 37 MB) on the first run, verifies them against
pinned SHA-256 hashes, pushes them to /data/local/tmp/spidey_eval on the phone, and writes
results.json next to this file. Nothing is installed on the phone.

Every case is a fresh process, so the latency includes loading the model. That is slower than
the app will be (it loads once); read the per-case times as an upper bound.
"""

import hashlib
import json
import os
import re
import shlex
import subprocess
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "cache")
REMOTE = "/data/local/tmp/spidey_eval"
BASE = "https://huggingface.co/Cactus-Compute/needle3/resolve/main/"

# name -> (path in the repo, size, sha256)
ARTEFACTS = {
    "needle": ("android-arm64/needle", 1586416,
               "02a044e15489e19fa9f4f1cd1d5943d39dc0eefc08e0ba99d36279daf2351d46"),
    "needle3.cact": ("needle3.cact", 35335380,
                     "c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38"),
}


def adb(*args, check=True, timeout=300):
    result = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout,
                            encoding="utf-8", errors="replace")
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch_artefacts():
    os.makedirs(CACHE, exist_ok=True)
    for name, (remote_path, size, expected) in ARTEFACTS.items():
        target = os.path.join(CACHE, name)
        if os.path.exists(target) and sha256_of(target) == expected:
            continue
        print(f"downloading {name} ...")
        partial = target + ".part"
        urllib.request.urlretrieve(BASE + remote_path, partial)
        if os.path.getsize(partial) != size or sha256_of(partial) != expected:
            os.remove(partial)
            sys.exit(f"{name}: checksum mismatch, refusing to use it")
        os.replace(partial, target)


def check_phone():
    devices = [line for line in adb("devices").stdout.splitlines()[1:] if line.strip().endswith("device")]
    if not devices:
        sys.exit("No phone found. Connect it, enable USB debugging and accept the prompt.")
    abi = adb("shell", "getprop", "ro.product.cpu.abi").stdout.strip()
    model = adb("shell", "getprop", "ro.product.model").stdout.strip()
    ram = adb("shell", "grep", "MemTotal", "/proc/meminfo").stdout.strip()
    print(f"phone: {model}, abi {abi}, {ram}")
    if abi != "arm64-v8a":
        sys.exit("This spike needs an arm64-v8a phone.")
    return {"model": model, "abi": abi, "ram": ram}


def push_everything(tools_file):
    adb("shell", "mkdir", "-p", REMOTE)
    for name in ARTEFACTS:
        adb("push", os.path.join(CACHE, name), f"{REMOTE}/{name}", timeout=600)
    adb("push", tools_file, f"{REMOTE}/tools.json")
    adb("shell", "chmod", "755", f"{REMOTE}/needle")


def openai_wrapped(path):
    """The same tools in the other common shape: [{"type":"function","function":{...}}]."""
    with open(path, encoding="utf-8") as handle:
        flat = json.load(handle)
    wrapped = os.path.join(CACHE, "tools_openai.json")
    with open(wrapped, "w", encoding="utf-8") as handle:
        json.dump([{"type": "function", "function": tool} for tool in flat], handle)
    return wrapped


def run_prompt(prompt):
    """Runs one prompt on the phone. Returns (stdout, stderr, seconds)."""
    local_prompt = os.path.join(CACHE, "prompt.txt")
    local_script = os.path.join(CACHE, "case.sh")
    with open(local_prompt, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(prompt)
    with open(local_script, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(f'cd {REMOTE} && ./needle --model needle3.cact --tools tools.json '
                     f'--prompt "$(cat prompt.txt)"\n')
    adb("push", local_prompt, f"{REMOTE}/prompt.txt")
    adb("push", local_script, f"{REMOTE}/case.sh")

    started = time.time()
    result = adb("shell", "sh", f"{REMOTE}/case.sh", check=False, timeout=600)
    return result.stdout, result.stderr, time.time() - started


def json_objects(text):
    """Every top-level JSON object found in the text."""
    found, depth, start = [], 0, None
    in_string = escaped = False
    for index, char in enumerate(text):
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            if depth == 0:
                start = index
            depth += 1
        elif char == "}" and depth:
            depth -= 1
            if depth == 0:
                try:
                    found.append(json.loads(text[start:index + 1]))
                except ValueError:
                    pass
    return found


def calls_from(output):
    for obj in reversed(json_objects(output)):
        if isinstance(obj, dict) and "function_calls" in obj:
            return obj.get("function_calls") or [], obj
    return None, None


def matches(expected, call):
    if not isinstance(call, dict) or call.get("name") != expected["name"]:
        return False
    arguments = json.dumps(call.get("arguments", {}), ensure_ascii=False).lower()
    return all(fragment.lower() in arguments for fragment in expected["has"])


def score(expect, calls):
    if calls is None:
        return False, False
    first = bool(calls) and matches(expect[0], calls[0])
    sequence = len(calls) == len(expect) and all(matches(e, c) for e, c in zip(expect, calls))
    return first, sequence


def main():
    adb("version")
    fetch_artefacts()
    phone = check_phone()
    tools_file = os.path.join(HERE, "tools.json")
    push_everything(tools_file)

    with open(os.path.join(HERE, "cases.json"), encoding="utf-8") as handle:
        cases = json.load(handle)["cases"]

    # The tool list format is not documented, so the first case decides which shape works.
    out, err, _ = run_prompt(cases[0]["prompt"])
    if calls_from(out)[0] is None:
        print("flat tool list gave no readable answer; trying the OpenAI-style wrapper ...")
        print("  stdout:", out.strip()[:300])
        print("  stderr:", err.strip()[:300])
        adb("push", openai_wrapped(tools_file), f"{REMOTE}/tools.json")
        out, err, _ = run_prompt(cases[0]["prompt"])
        if calls_from(out)[0] is None:
            print("Neither tool format produced a readable answer. Raw output of the first case:")
            print(out)
            print(err)
            sys.exit(1)

    results = []
    for case in cases:
        out, err, seconds = run_prompt(case["prompt"])
        calls, raw = calls_from(out)
        first, sequence = score(case["expect"], calls)
        results.append({
            "id": case["id"], "group": case["group"], "prompt": case["prompt"],
            "expected": case["expect"], "calls": calls, "raw": raw,
            "first_ok": first, "sequence_ok": sequence, "seconds": round(seconds, 2),
            "stdout": out if calls is None else None, "stderr": err.strip()[:300],
        })
        flag = "ok " if sequence else ("1st" if first else "no ")
        got = ", ".join(c.get("name", "?") for c in calls) if calls else "(unreadable)"
        print(f"{case['id']} [{flag}] {seconds:5.1f}s  expected {case['expect'][0]['name']:<16} got {got}")

    summary = {"phone": phone, "groups": {}}
    for group in sorted({r["group"] for r in results}):
        rows = [r for r in results if r["group"] == group]
        times = sorted(r["seconds"] for r in rows)
        summary["groups"][group] = {
            "cases": len(rows),
            "first_call_correct": sum(r["first_ok"] for r in rows),
            "whole_sequence_correct": sum(r["sequence_ok"] for r in rows),
            "median_seconds": times[len(times) // 2],
        }
    print()
    for group, stats in summary["groups"].items():
        print(f"group {group}: first call {stats['first_call_correct']}/{stats['cases']}, "
              f"whole sequence {stats['whole_sequence_correct']}/{stats['cases']}, "
              f"median {stats['median_seconds']}s")

    with open(os.path.join(HERE, "results.json"), "w", encoding="utf-8") as handle:
        json.dump({"summary": summary, "results": results}, handle, indent=1, ensure_ascii=False)
    print("full results: tools/spidey_eval/results.json")


if __name__ == "__main__":
    main()
