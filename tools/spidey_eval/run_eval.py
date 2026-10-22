#!/usr/bin/env python3
"""Measure Spidey's on-device intent model: accuracy per intent, latency and peak RAM.

The model (spidey.cact) maps one raw sentence to zero or more of five tool calls. It was
trained with NO system prompt, so the input is exactly the sentence and the five tools in
app/src/main/assets/spidey_tools.json. This script reproduces that contract.

Two modes:

  adb mode (a phone is needed)
      python tools/spidey_eval/run_eval.py --data F:/needle-finetune/data/dev_natural.jsonl
    Downloads spidey.cact from the URL pinned in ModelSpec.kt and the vendor runner from the
    Cactus-Compute/needle3 repo (android-arm64/needle), checks both against pinned SHA-256
    hashes, pushes them to /data/local/tmp/spidey_eval and runs every query in a fresh
    process on an arm64 phone. Nothing is installed on the phone. Writes results.json.

  replay mode (no phone)
      python tools/spidey_eval/run_eval.py --replay logs/eval_v4_dev_all.jsonl
    Scores answers that were recorded earlier with the same scoring code, so the reported
    figures can be recomputed on any machine. See docs/EVALUATION.md.

A match is EXACT: the predicted calls must equal the gold calls in order, with the same tool
names and the same argument values, character for character. The lenient score (case, edge
whitespace and trailing . ! ? ignored) is reported next to it and is what the training-side
scripts called "lenient_ok".

Latency is wall time of one process per query, so it includes loading the model; the app
loads once, so read it as an upper bound. Peak RAM is the process's VmHWM sampled from
/proc while it runs, so it is a lower bound on the true peak.
"""

import argparse
import collections
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
CACHE = os.path.join(HERE, "cache")
REMOTE = "/data/local/tmp/spidey_eval"
MODEL_SPEC = os.path.join(REPO, "app", "src", "main", "java", "com", "example", "agent",
                          "core", "ai", "local", "ModelSpec.kt")
TOOLS_JSON = os.path.join(REPO, "app", "src", "main", "assets", "spidey_tools.json")
DEFAULT_DATA = "F:/needle-finetune/data/dev_natural.jsonl"

RUNNER_URL = "https://huggingface.co/Cactus-Compute/needle3/resolve/main/android-arm64/needle"
RUNNER_SIZE = 1586416
RUNNER_SHA256 = "02a044e15489e19fa9f4f1cd1d5943d39dc0eefc08e0ba99d36279daf2351d46"

INTENT_TOOLS = ("open_app", "send_message", "call_contact", "set_alarm", "set_timer")


# --------------------------------------------------------------------------- scoring (pure)

def _norm(value):
    return value.strip().strip(".!?").casefold() if isinstance(value, str) else value


def canon(calls, fold=False):
    """Calls as comparable tuples: ordered, names and argument values."""
    keep = _norm if fold else (lambda value: value)
    return [(c["name"], tuple(sorted((k, keep(v)) for k, v in c["arguments"].items())))
            for c in calls]


def is_exact(pred, gold):
    return canon(pred) == canon(gold)


def is_lenient(pred, gold):
    return canon(pred, fold=True) == canon(gold, fold=True)


def intent_of(gold):
    """none for a request Spidey must refuse, chain for several calls, else the tool name."""
    if not gold:
        return "none"
    return "chain" if len(gold) > 1 else gold[0]["name"]


def failure_category(pred, gold, query):
    """correct, or why not. Mirrors the categories in docs/EVALUATION.md."""
    if is_exact(pred, gold):
        return "correct"
    if not gold:
        return "missed_unsupported"      # it acted on something it should have declined
    if not pred:
        return "refused"                 # it declined something it could do (a safe miss)
    if [c["name"] for c in pred] != [c["name"] for c in gold]:
        return "wrong_tool"
    lowered = query.casefold()
    for call in pred:
        for key in ("contact", "app"):
            value = call["arguments"].get(key)
            if isinstance(value, str) and value.strip().casefold() not in lowered:
                return "hallucinated_entity"  # a contact or app that is not in the sentence
    return "wrong_argument"


def grounded(calls, query):
    """List-free guard: drop calls whose contact or app is not literally in the sentence.

    The app's real guard (IntentGuard) resolves against the phone's contacts and installed
    apps; this stand-in needs no device and no vocabulary list, so it cannot be fitted to a
    test set.
    """
    lowered = query.casefold()
    kept = []
    for call in calls:
        values = [call["arguments"].get(k) for k in ("contact", "app")]
        if any(isinstance(v, str) and v.strip().casefold() not in lowered for v in values):
            continue
        kept.append(call)
    return kept


def extract_calls(output):
    """The function_calls list from the runner's stdout, or None if unreadable."""
    for obj in reversed(_json_objects(output)):
        if isinstance(obj, dict) and "function_calls" in obj:
            return obj.get("function_calls") or [], obj
    return None, None


def _json_objects(text):
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


def summarize(rows):
    """rows: dicts with query, gold, pred. Returns the accuracy report as plain data."""
    def pct(num, den):
        return round(100.0 * num / den, 1) if den else None

    by_intent = collections.defaultdict(lambda: collections.Counter())
    failures = collections.Counter()
    exact = lenient = guarded = 0
    for row in rows:
        gold, pred, query = row["gold"], row["pred"] or [], row["query"]
        ok = is_exact(pred, gold)
        bucket = by_intent[intent_of(gold)]
        bucket["n"] += 1
        bucket["exact"] += ok
        bucket["lenient"] += is_lenient(pred, gold)
        exact += ok
        lenient += is_lenient(pred, gold)
        guarded += is_lenient(grounded(pred, query), gold)
        failures[failure_category(pred, gold, query)] += 1
    n = len(rows)
    return {
        "n": n,
        "exact": {"correct": exact, "percent": pct(exact, n)},
        "lenient": {"correct": lenient, "percent": pct(lenient, n)},
        "grounded_guard_lenient": {"correct": guarded, "percent": pct(guarded, n)},
        "per_intent": {k: {"n": v["n"], "exact": v["exact"], "lenient": v["lenient"],
                           "exact_percent": pct(v["exact"], v["n"])}
                       for k, v in sorted(by_intent.items())},
        "failures": dict(failures),
    }


def print_report(summary, extra_lines=()):
    print(f"\nn={summary['n']}  exact {summary['exact']['correct']} "
          f"({summary['exact']['percent']}%)  lenient {summary['lenient']['correct']} "
          f"({summary['lenient']['percent']}%)  with list-free grounding guard "
          f"{summary['grounded_guard_lenient']['correct']} "
          f"({summary['grounded_guard_lenient']['percent']}%)")
    print(f"{'intent':<14}{'n':>5}{'exact':>7}{'%':>7}")
    for name, s in summary["per_intent"].items():
        print(f"{name:<14}{s['n']:>5}{s['exact']:>7}{s['exact_percent']:>7}")
    print("failures:", summary["failures"])
    for line in extra_lines:
        print(line)


# --------------------------------------------------------------------------- data

def load_rows(path):
    with open(path, encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def replay(path):
    """Score a recorded log (rows with query, gold, pred) or a dataset row set."""
    rows = []
    for row in load_rows(path):
        gold = row.get("gold", row.get("answers"))
        pred = row.get("pred", row.get("recorded"))
        if gold is None or pred is None:
            sys.exit(f"{path}: rows need gold/pred (or answers/recorded)")
        rows.append({"query": row["query"], "gold": gold, "pred": pred})
    print_report(summarize(rows))


# --------------------------------------------------------------------------- pinned downloads

def model_spec():
    """url, size and sha256 of OnDeviceModels.SPIDEY, read from ModelSpec.kt."""
    text = open(MODEL_SPEC, encoding="utf-8").read()
    block = text[text.index("val SPIDEY"):]
    block = block[:block.index("\n    )")]
    url = re.search(r'url = "([^"]+)"', block).group(1)
    size = int(re.search(r"sizeBytes = ([\d_]+)L", block).group(1).replace("_", ""))
    sha = re.search(r'sha256 = "([0-9a-f]{64})"', block).group(1)
    return url, size, sha


def sha256_of(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(name, url, size, expected):
    os.makedirs(CACHE, exist_ok=True)
    target = os.path.join(CACHE, name)
    if os.path.exists(target) and sha256_of(target) == expected:
        return target
    print(f"downloading {name} ...")
    partial = target + ".part"
    urllib.request.urlretrieve(url, partial)
    if os.path.getsize(partial) != size or sha256_of(partial) != expected:
        os.remove(partial)
        sys.exit(f"{name}: size or SHA-256 differs from the pin, refusing to use it")
    os.replace(partial, target)
    return target


# --------------------------------------------------------------------------- phone

def adb(*args, check=True, timeout=300):
    result = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout,
                            encoding="utf-8", errors="replace")
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {result.stderr.strip() or result.stdout.strip()}")
    return result


def check_phone():
    devices = [l for l in adb("devices").stdout.splitlines()[1:] if l.strip().endswith("device")]
    if not devices:
        sys.exit("No phone found. Connect it, enable USB debugging and accept the prompt.")
    abi = adb("shell", "getprop", "ro.product.cpu.abi").stdout.strip()
    if abi != "arm64-v8a":
        sys.exit("This evaluation needs an arm64-v8a phone.")
    return {
        "model": adb("shell", "getprop", "ro.product.model").stdout.strip(),
        "abi": abi,
        "ram": adb("shell", "grep", "MemTotal", "/proc/meminfo").stdout.strip(),
    }


# On the phone: run the model on prompt N in the background and sample VmHWM until it exits.
DEVICE_SCRIPT = """cd {remote}
./needle --model spidey.cact --tools tools.json --prompt "$(cat prompts/$1.txt)" > out.txt 2> err.txt &
pid=$!
peak=0
while kill -0 $pid 2>/dev/null; do
  v=$(grep VmHWM /proc/$pid/status 2>/dev/null | tr -dc 0-9)
  [ -n "$v" ] && peak=$v
  sleep 0.02
done
echo "PEAK_KB=$peak"
cat out.txt
"""


def push_everything(model, runner, rows):
    adb("shell", "mkdir", "-p", f"{REMOTE}/prompts")
    adb("push", model, f"{REMOTE}/spidey.cact", timeout=900)
    adb("push", runner, f"{REMOTE}/needle", timeout=300)
    adb("push", TOOLS_JSON, f"{REMOTE}/tools.json")
    adb("shell", "chmod", "755", f"{REMOTE}/needle")
    local_dir = os.path.join(CACHE, "prompts")
    os.makedirs(local_dir, exist_ok=True)
    for index, row in enumerate(rows):
        with open(os.path.join(local_dir, f"{index}.txt"), "w", encoding="utf-8", newline="\n") as h:
            h.write(row["query"])
    adb("push", local_dir + "/.", f"{REMOTE}/prompts/", timeout=600)
    script = os.path.join(CACHE, "case.sh")
    with open(script, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(DEVICE_SCRIPT.format(remote=REMOTE))
    adb("push", script, f"{REMOTE}/case.sh")


def run_case(index):
    started = time.time()
    result = adb("shell", "sh", f"{REMOTE}/case.sh", str(index), check=False, timeout=600)
    seconds = time.time() - started
    peak = re.search(r"PEAK_KB=(\d+)", result.stdout)
    return result.stdout, int(peak.group(1)) if peak else None, seconds


def percentile(sorted_values, fraction):
    return sorted_values[min(len(sorted_values) - 1, int(len(sorted_values) * fraction))]


def run_on_phone(args):
    rows = load_rows(args.data)
    if args.limit:
        rows = rows[:args.limit]
    url, size, sha = model_spec()
    model = fetch("spidey.cact", url, size, sha)
    runner = fetch("needle", RUNNER_URL, RUNNER_SIZE, RUNNER_SHA256)
    adb("version")
    phone = check_phone()
    print(f"phone: {phone['model']}, {phone['ram']}")
    push_everything(model, runner, rows)

    results, scored, seconds_all, peaks = [], [], [], []
    for index, row in enumerate(rows):
        out, peak, seconds = run_case(index)
        calls, raw = extract_calls(out)
        scored.append({"query": row["query"], "gold": row["answers"], "pred": calls or []})
        seconds_all.append(seconds)
        if peak:
            peaks.append(peak)
        ok = calls is not None and is_exact(calls, row["answers"])
        results.append({
            "query": row["query"], "gold": row["answers"], "pred": calls,
            "unreadable": calls is None, "exact": ok, "seconds": round(seconds, 3),
            "peak_ram_kb": peak, "raw": raw, "stdout": out if calls is None else None,
        })
        print(f"[{index + 1}/{len(rows)}] {'ok ' if ok else 'BAD'} {seconds:5.2f}s  {row['query'][:60]}")

    summary = summarize(scored)
    seconds_all.sort()
    summary["phone"] = phone
    summary["unreadable_outputs"] = sum(r["unreadable"] for r in results)
    summary["latency_seconds"] = {
        "mean": round(sum(seconds_all) / len(seconds_all), 3),
        "p50": round(percentile(seconds_all, 0.5), 3),
        "p95": round(percentile(seconds_all, 0.95), 3),
        "includes_model_load": True,
    }
    summary["peak_ram_mb"] = {
        "max": round(max(peaks) / 1024, 1) if peaks else None,
        "median": round(sorted(peaks)[len(peaks) // 2] / 1024, 1) if peaks else None,
        "note": "VmHWM sampled every 20 ms; a lower bound",
    }
    summary["model"] = {"url": url, "sha256": sha}
    summary["data"] = os.path.basename(args.data)
    print_report(summary, [f"latency {summary['latency_seconds']}", f"peak RAM {summary['peak_ram_mb']}"])

    out_path = os.path.join(HERE, "results.json")
    with open(out_path, "w", encoding="utf-8") as handle:
        json.dump({"summary": summary, "results": results}, handle, indent=1, ensure_ascii=False)
    print("full results:", out_path)
    if args.cleanup:
        adb("shell", "rm", "-rf", REMOTE, check=False)
        print("removed", REMOTE)


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--data", default=os.environ.get("SPIDEY_EVAL_DATA", DEFAULT_DATA),
                        help="dataset JSONL: query + answers per row (default: dev_natural)")
    parser.add_argument("--limit", type=int, default=0, help="only the first N queries")
    parser.add_argument("--replay", metavar="LOG", help="score a recorded log; no phone needed")
    parser.add_argument("--cleanup", action="store_true", help=f"remove {REMOTE} when done")
    args = parser.parse_args()
    if args.replay:
        replay(args.replay)
    else:
        run_on_phone(args)


if __name__ == "__main__":
    main()
