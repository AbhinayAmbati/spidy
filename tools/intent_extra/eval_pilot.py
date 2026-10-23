"""Score an adapter on JSONL test files whose rows carry their own tools (run on the GPU box).

    python eval_pilot.py --weights pilot1/adapter.safetensors --data pilot1/intents_old_test.jsonl --tag old
"""
import argparse, collections, json, time
import needle

ap = argparse.ArgumentParser()
ap.add_argument("--weights", default=None)
ap.add_argument("--data", required=True)
ap.add_argument("--tag", required=True)
args = ap.parse_args()

rows = [json.loads(l) for l in open(args.data, encoding="utf8") if l.strip()]
agents = {}

def agent_for(tools):
    key = json.dumps(tools, sort_keys=True)
    if key not in agents:
        agents[key] = needle.Needle(tools=tools, weights=args.weights, auto_date=False)
    return agents[key]

norm = lambda v: v.strip().strip(".!?").casefold() if isinstance(v, str) else v
canon = lambda calls, f: [(c["name"], tuple(sorted((k, f(v)) for k, v in c["arguments"].items()))) for c in calls]
stats = collections.defaultdict(collections.Counter); lat = []; out = []
for row in rows:
    gold = row["answers"]
    kind = "negative" if not gold else (gold[0]["name"] if len(gold) == 1 else "multi")
    ag = agent_for(row["tools"]); ag.reset(); t0 = time.time()
    try:
        r = ag.complete(row["query"])
    except Exception as e:
        r = {"function_calls": [], "error": repr(e)}
    lat.append(time.time() - t0)
    pred = r.get("function_calls") or []
    ok = canon(pred, norm) == canon(gold, norm)
    names = [c["name"] for c in pred] == [c["name"] for c in gold]
    for k in (kind, "ALL"):
        s = stats[k]; s["n"] += 1; s["ok"] += ok; s["names"] += names; s["err"] += bool(r.get("error"))
    out.append({"query": row["query"], "ok": ok, "gold": gold, "pred": pred})
print(f"\n== {args.tag} ({len(rows)} rows) ==")
print(f"{'group':<16}{'n':>5}{'exact':>8}{'names':>8}{'err':>5}")
for k in sorted(stats, key=lambda x: (x == "ALL", x)):
    s = stats[k]; print(f"{k:<16}{s['n']:>5}{s['ok']/s['n']:>8.1%}{s['names']/s['n']:>8.1%}{s['err']:>5}")
lat.sort(); print(f"latency s/query mean={sum(lat)/len(lat):.3f} p95={lat[int(len(lat)*.95)-1]:.3f}")
with open(f"pilot1/eval_{args.tag}.jsonl", "w", encoding="utf8") as f:
    for o in out: f.write(json.dumps(o, ensure_ascii=False) + "\n")
