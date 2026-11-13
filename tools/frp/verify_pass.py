#!/usr/bin/env python3
"""Blind verification pass of gen_tasks/*.json against qwen3.8-flash-next.

Stage A (blind): batches of 8 -> model solves from prompt only, returns my_answer
        + self-verdict ok|ambiguous|unsolvable.
Short tasks : final verdict computed in code by normalised comparison with `answer`.
Extended    : stage B (with reference answer) -> model judges ok|wrong_answer|ambiguous.
Resumable: per-file verdicts in verify/<stem>.json; restart skips finished work.
"""
import json, os, re, sys, time, glob, threading, urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT = os.path.expanduser("~/.hermes/cache/scratch/fgos")
GEN = os.path.join(ROOT, "gen_tasks")
VDIR = os.path.join(ROOT, "verify")
LOG = os.path.join(ROOT, "verify.log")
API = "https://cloud.agk-russia.ru:4443/v1/chat/completions"
MODEL = "qwen3.8-flash-next"
KEY = os.environ["HERMES_CUSTOM_BIGMODEL_API_KEY"]
WORKERS = 6
BATCH = 8
BATCH_CHARS = 7000
TIMEOUT = 300
MAX_TRIES = 4

os.makedirs(VDIR, exist_ok=True)
_lock = threading.Lock()
_stats = {"batches_done": 0, "ok": 0, "wrong_answer": 0, "ambiguous": 0,
          "unsolvable": 0, "error": 0, "total_batches": 0}

def log(msg):
    line = f"[{time.strftime('%F %T')}] {msg}"
    with open(LOG, "a") as f:
        f.write(line + "\n")

# ---------- API ----------
def call_api(messages, max_tokens):
    body = {"model": MODEL, "messages": messages, "max_tokens": max_tokens,
            "temperature": 0, "chat_template_kwargs": {"enable_thinking": False}}
    data = json.dumps(body, ensure_ascii=False).encode()
    last = None
    for attempt in range(MAX_TRIES):
        try:
            req = urllib.request.Request(API, data=data, headers={
                "Content-Type": "application/json", "Authorization": "Bearer " + KEY})
            r = json.load(urllib.request.urlopen(req, timeout=TIMEOUT))
            content = r["choices"][0]["message"].get("content")
            if content is None:
                raise ValueError("null content")
            return content
        except Exception as e:
            last = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"API failed after {MAX_TRIES} tries: {last!r}")

JSON_RE = re.compile(r"\{.*\}", re.S)
ITEM_RE = re.compile(
    r'"i"\s*:\s*(\d+).*?"verdict"\s*:\s*"(\w+)".*?"my_answer"\s*:\s*"(.*?)"\s*\}', re.S)
def parse_results(content):
    m = JSON_RE.search(content or "")
    if not m:
        raise ValueError("no JSON in reply")
    try:
        obj = json.loads(m.group(0))
    except json.JSONDecodeError:
        # fallback: model emitted unescaped quotes inside my_answer
        obj = {"results": [{"i": int(i), "verdict": v,
                            "my_answer": a.replace('\\"', '"')}
                           for i, v, a in ITEM_RE.findall(m.group(0))]}
    out = {}
    for r in obj.get("results", []):
        i = int(r.get("i"))
        out[i] = {"verdict": str(r.get("verdict", "")).strip().lower(),
                  "my_answer": str(r.get("my_answer", "")).strip()}
    if not out:
        raise ValueError("empty results")
    return out

# ---------- normalisation / comparison ----------
PUNCT = re.compile(r"[^\w\s]", re.UNICODE)
WS = re.compile(r"\s+")
def norm(s):
    s = (s or "").lower().replace("ё", "е")
    s = PUNCT.sub(" ", s)
    return WS.sub(" ", s).strip()

def short_matches(mine, ref):
    a, b = norm(mine), norm(ref)
    if not a or not b:
        return False
    if a == b:
        return True
    short, long_ = (a, b) if len(a) <= len(b) else (b, a)
    if len(short) < 2:
        return False
    # substring or word-boundary containment either direction
    if short in long_:
        return True
    toks = set(short.split())
    return any(t in long_.split() for t in toks if len(t) >= 3)

# ---------- prompts ----------
def stageA_prompt(items):  # items: [(i, prompt)]
    lines = "\n".join(f"{i}. {p}" for i, p in items)
    return (
        "Ты — независимый эксперт-проверяющий. Ниже учебные задания. Реши КАЖДОЕ "
        "задание самостоятельно, опираясь только на текст задания и свои знания.\n"
        "Верни СТРОГО один JSON без пояснений вне JSON:\n"
        '{"results":[{"i":<номер>,"verdict":"ok|ambiguous|unsolvable","my_answer":"<краткий ответ>"}]}\n'
        "verdict=ambiguous — задание двусмысленно/без корректной формулировки ответа; "
        "unsolvable — решить по тексту задания невозможно; иначе ok.\n\n"
        f"Задания:\n{lines}")

def stageB_prompt(items):  # items: [(i, prompt, ref, mine)]
    blocks = []
    for i, p, ref, mine in items:
        blocks.append(f"Задание {i}:\nВопрос: {p}\nЭталонный ответ: {ref}\nОтвет проверяющего: {mine}")
    return (
        "Ты — эксперт-проверяющий. Для каждого задания сравни ответ проверяющего с "
        "эталонным ответом по смыслу (формулировки могут различаться).верни СТРОГО один JSON:\n"
        '{"results":[{"i":<номер>,"verdict":"ok|wrong_answer|ambiguous","my_answer":"<ответ проверяющего как есть>"}]}\n'
        "ok — смысл совпадает с эталоном; wrong_answer — противоречит эталону или не по заданию; "
        "ambiguous — задание/эталон некорректны.\n\n" + "\n\n".join(blocks))

# ---------- state ----------
class FileState:
    def __init__(self, path, doc):
        self.path = path
        self.doc = doc
        self.stageA = {}   # key -> {my_answer, verdict}
        self.final = {}    # key -> {verdict, my_answer}
        self.tasks = {}    # key -> (topic_idx, task_idx, task)
        k = 0
        for ti, t in enumerate(doc["topics"]):
            for ki, tk in enumerate(t["tasks"]):
                self.tasks[k] = (ti, ki, tk)
                k += 1
        if os.path.exists(path):
            try:
                saved = json.load(open(path))
                self.stageA = saved.get("stageA", {})
                self.final = saved.get("final", {})
            except Exception:
                log(f"WARN corrupt {path}, starting fresh")
    def save(self):
        tmp = self.path + ".tmp"
        with open(tmp, "w") as f:
            json.dump({"stageA": self.stageA, "final": self.final}, f, ensure_ascii=False)
        os.replace(tmp, self.path)

def load_states():
    states = {}
    for f in sorted(glob.glob(os.path.join(GEN, "*.json"))):
        stem = os.path.splitext(os.path.basename(f))[0]
        states[stem] = FileState(os.path.join(VDIR, stem + ".json"), json.load(open(f)))
    return states

def record_final(stem, st, key, verdict, my_answer, tally=True):
    with _lock:
        st.final[str(key)] = {"verdict": verdict, "my_answer": my_answer}
        st.save()
        if tally and verdict in _stats:
            _stats[verdict] += 1

def batch_tick():
    with _lock:
        _stats["batches_done"] += 1
        n = _stats["batches_done"]
    if n % 50 == 0 or n == _stats["total_batches"]:
        log(f"batches {n}/{_stats['total_batches']} | ok={_stats['ok']} "
            f"wrong={_stats['wrong_answer']} amb={_stats['ambiguous']} "
            f"unsolv={_stats['unsolvable']} err={_stats['error']}")

# ---------- jobs ----------
def make_batches(pending):  # pending: [(key, prompt)] -> list of batches respecting char cap
    batches, cur, curlen = [], [], 0
    for key, prompt in pending:
        if cur and (len(cur) >= BATCH or curlen + len(prompt) > BATCH_CHARS):
            batches.append(cur); cur, curlen = [], 0
        cur.append((key, prompt)); curlen += len(prompt)
    if cur:
        batches.append(cur)
    return batches

def run_stageA(stem, st, batch):
    items = [(i + 1, st.tasks[k][2]["prompt"]) for i, (k, _) in enumerate(batch)]
    got = None
    try:
        got = parse_results(call_api(
            [{"role": "user", "content": stageA_prompt(items)}], 3200))
    except Exception as e:
        log(f"A ERR {stem}: {e}")
    with _lock:
        for i, (key, _) in enumerate(batch):
            r = (got or {}).get(i + 1)
            if r is None:
                st.stageA[str(key)] = {"my_answer": "", "verdict": "error"}
            else:
                v = r["verdict"] if r["verdict"] in ("ok", "ambiguous", "unsolvable") else "ok"
                st.stageA[str(key)] = {"my_answer": r["my_answer"], "verdict": v}
        st.save()
    batch_tick()

def finish_short(stem, st, key):
    task = st.tasks[key][2]
    a = st.stageA.get(str(key), {})
    if a.get("verdict") in ("ambiguous", "unsolvable"):
        record_final(stem, st, key, a["verdict"], a.get("my_answer", ""))
    elif a.get("verdict") == "error":
        record_final(stem, st, key, "error", a.get("my_answer", ""))
    else:
        ok = short_matches(a.get("my_answer", ""), task["answer"])
        record_final(stem, st, key, "ok" if ok else "wrong_answer", a.get("my_answer", ""))

def run_stageB(stem, st, batch):  # batch: [key,...]
    items = []
    for i, key in enumerate(batch):
        task = st.tasks[key][2]
        mine = st.stageA.get(str(key), {}).get("my_answer", "")
        items.append((i + 1, task["prompt"], task["answer"], mine))
    got = None
    try:
        got = parse_results(call_api(
            [{"role": "user", "content": stageB_prompt(items)}], 2000))
    except Exception as e:
        log(f"B ERR {stem}: {e}")
    for i, key in enumerate(batch):
        r = (got or {}).get(i + 1)
        if r is None:
            record_final(stem, st, key, "error", items[i][3])
        else:
            v = r["verdict"] if r["verdict"] in ("ok", "wrong_answer", "ambiguous", "unsolvable") else "error"
            record_final(stem, st, key, v, r.get("my_answer") or items[i][3])
    batch_tick()

def main():
    t0 = time.time()
    states = load_states()
    jobsA, jobsB = [], []
    ntasks = 0
    for stem, st in states.items():
        for key, (ti, ki, task) in st.tasks.items():
            ntasks += 1
            ks = str(key)
            if ks in st.final:
                continue
            if task.get("kind") not in ("short", "extended"):
                record_final(stem, st, key, "error", f"bad kind={task.get('kind')!r}", tally=True)
                continue
            if task["kind"] == "short" and ks in st.stageA:
                finish_short(stem, st, key)
                continue
            if task["kind"] == "extended" and ks in st.stageA and \
               st.stageA[ks]["verdict"] in ("ambiguous", "unsolvable", "error"):
                v = st.stageA[ks]["verdict"]
                record_final(stem, st, key, v if v != "error" else "error", st.stageA[ks]["my_answer"])
                continue
            if task["kind"] == "extended" and ks in st.stageA:
                jobsB.append((stem, key))
                continue
            jobsA.append((stem, key))
    log(f"start: {ntasks} tasks in {len(states)} files; "
        f"stageA pending {len(jobsA)}, stageB pending {len(jobsB)}")

    # group stage A jobs by file -> batches
    byFileA = {}
    for stem, key in jobsA:
        byFileA.setdefault(stem, []).append(key)
    batchA = [(stem, b) for stem, keys in byFileA.items()
              for b in make_batches([(k, states[stem].tasks[k][2]["prompt"]) for k in keys])]
    byFileB = {}
    for stem, key in jobsB:
        byFileB.setdefault(stem, []).append(key)
    batchB = [(stem, keys[i:i + BATCH]) for stem, keys in byFileB.items() for i in range(0, len(keys), BATCH)]

    _stats["total_batches"] = len(batchA) + len(batchB)
    log(f"batches: A={len(batchA)} B={len(batchB)}")

    with ThreadPoolExecutor(max_workers=WORKERS) as ex:
        futs = [ex.submit(run_stageA, stem, states[stem], b) for stem, b in batchA]
        futs += [ex.submit(run_stageB, stem, states[stem], b) for stem, b in batchB]
        for _ in as_completed(futs):
            pass

    # short tasks whose stageA arrived during this run
    for stem, st in states.items():
        for key, (ti, ki, task) in st.tasks.items():
            ks = str(key)
            if ks not in st.final and task.get("kind") == "short" and ks in st.stageA:
                finish_short(stem, st, key)

    # ---------- report ----------
    report = {"tasks": []}
    summary = {"total": 0, "ok": 0, "wrong_answer": 0, "ambiguous": 0,
               "unsolvable": 0, "errors": 0, "files": len(states)}
    for stem, st in states.items():
        for key, (ti, ki, task) in sorted(st.tasks.items()):
            f = st.final.get(str(key), {"verdict": "error", "my_answer": ""})
            topic = st.doc["topics"][ti]
            rec = {"file": f"{stem}.json", "topic": topic["topic"], "section": topic.get("section"),
                   "topic_idx": ti, "task_idx": ki, "kind": task.get("kind"),
                   "prompt_prefix": (task.get("prompt") or "")[:80],
                   "reference_answer": task.get("answer"),
                   "verdict": f["verdict"], "my_answer": f.get("my_answer", "")}
            report["tasks"].append(rec)
            summary["total"] += 1
            v = f["verdict"]
            if v == "error":
                summary["errors"] += 1
            elif v in summary:
                summary[v] += 1
            else:
                summary["errors"] += 1
    json.dump(report, open(os.path.join(ROOT, "verify_report.json"), "w"), ensure_ascii=False, indent=1)
    json.dump(summary, open(os.path.join(ROOT, "verify_summary.json"), "w"), ensure_ascii=False, indent=1)
    log(f"DONE in {round(time.time()-t0)}s {json.dumps(summary)}")
    print(json.dumps(summary, ensure_ascii=False))

if __name__ == "__main__":
    main()
