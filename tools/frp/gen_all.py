#!/usr/bin/env python3
"""Полная генерация заданий по тематическому плану ФРП через cloud.agk-russia.ru.

Волны: 1) классы 5–9, 2) 10–11, 3) 1–4. Возобновляемая: тема считается готовой,
если она есть в gen_tasks/<subj><grade>.json с >=3 задачами.
"""
import json, os, re, sys, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen

BASE = "https://cloud.agk-russia.ru:4443/v1/chat/completions"
KEY = os.environ["HERMES_CUSTOM_BIGMODEL_API_KEY"]
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "gen_tasks")
os.makedirs(OUT, exist_ok=True)
lock = threading.Lock()
plan = json.load(open(os.path.join(HERE, "thematic_plan.json")))["plan"]

SYSTEM = json.load(open(os.path.join(HERE, "gen_system.json")))["system"]

def one(topic, section, frp, grade, n=5):
    user = (f"Предмет: {frp}. Класс {grade}. Раздел: {section}. Тема: {topic}.\n"
            f"Сделай {n} заданий по теме. Уровень — школьник {grade} класса.")
    body = json.dumps({
        "model": "qwen3.8-flash-next",
        "messages": [{"role": "system", "content": SYSTEM},
                     {"role": "user", "content": user}],
        "temperature": 0.9, "max_tokens": 6000,
        "chat_template_kwargs": {"enable_thinking": False},
    }).encode()
    for attempt in (1, 2):
        try:
            req = Request(BASE, data=body, headers={
                "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
            with urlopen(req, timeout=300) as r:
                d = json.loads(r.read())
            msg = d["choices"][0]["message"]
            content = re.sub(r"```(json)?|```", "",
                             msg.get("content") or msg.get("reasoning_content") or "")
            m = re.search(r"\{.*\}", content, re.S)
            tasks = json.loads(m.group(0))["tasks"] if m else []
            clean = []
            seen = set()
            for t in tasks:
                if not isinstance(t, dict):
                    continue
                p, a = str(t.get("prompt", "")).strip(), str(t.get("answer", "")).strip()
                k = t.get("kind", "short")
                if not p or not a or p.lower() in seen:
                    continue
                if k == "short":
                    a = a.replace("ё", "е")[:80]
                if len(a) > 400:
                    k = "extended"
                seen.add(p.lower())
                clean.append({"prompt": p, "answer": a, "kind": k,
                              "solution": str(t.get("solution", "")).replace("ё", "е")[:600]})
            if len(clean) >= 3:
                return clean
        except Exception as e:
            sys.stderr.write(f"retry{attempt} {grade} {topic[:30]}: {type(e).__name__} {e}\n")
            time.sleep(3 * attempt)
    return None

def file_for(subj, grade):
    return os.path.join(OUT, f"{subj}_{grade}.json")

def done_map(path):
    if os.path.exists(path):
        try:
            d = json.load(open(path))
            return {t["topic"]: t for t in d.get("topics", [])}
        except Exception:
            return {}
    return {}

def run_wave(name, grades, per_topic=5):
    jobs = []
    frps = {}
    for subj, by_grade in plan.items():
        for g in sorted(by_grade, key=int):
            if int(g) not in grades:
                continue
            path = file_for(subj, g)
            frps[path] = by_grade[g]["frp"]
            have = done_map(path)
            for s in by_grade[g]["sections"]:
                for t in s["topics"]:
                    cur = have.get(t["title"])
                    if cur and len(cur["tasks"]) >= 3:
                        continue
                    jobs.append((subj, g, path, by_grade[g]["frp"], s["title"], t["title"]))
    print(f"[{name}] тем к генерации: {len(jobs)}", flush=True)
    stats = {"ok": 0, "fail": [], "tasks": 0, "t0": time.time()}
    results = {}

    def work(job):
        subj, g, path, frp, sec, topic = job
        tasks = one(topic, sec, frp, g, per_topic)
        return job, tasks

    with ThreadPoolExecutor(max_workers=6) as ex:
        futs = [ex.submit(work, j) for j in jobs]
        for i, f in enumerate(as_completed(futs), 1):
            (subj, g, path, frp, sec, topic), tasks = f.result()
            with lock:
                if tasks:
                    stats["ok"] += 1
                    stats["tasks"] += len(tasks)
                    results.setdefault(path, []).append(
                        {"topic": topic, "section": sec, "tasks": tasks})
                else:
                    stats["fail"].append(f"{subj}{g}::{topic[:40]}")
                if i % 25 == 0 or i == len(jobs):
                    el = time.time() - stats["t0"]
                    print(f"[{name}] {i}/{len(jobs)} ok={stats['ok']} "
                          f"задач={stats['tasks']} темп={i/max(el,1):.2f} тем/с", flush=True)
    for path, items in results.items():
        have = done_map(path)
        for it in items:
            have[it["topic"]] = it
        json.dump({"frp": frps.get(path, ""),
                   "topics": sorted(have.values(), key=lambda x: x["topic"])},
                  open(path, "w"), ensure_ascii=False, indent=1)
    print(f"[{name}] готово: {stats['ok']} тем, {stats['tasks']} задач, "
          f"пустых: {len(stats['fail'])}", flush=True)
    for f in stats["fail"]:
        print("  FAIL", f, flush=True)

if __name__ == "__main__":
    wave = sys.argv[1] if len(sys.argv) > 1 else "1"
    if wave == "1":
        run_wave("волна1 5-9", set(range(5, 10)))
    elif wave == "2":
        run_wave("волна2 10-11", {10, 11})
    elif wave == "3":
        run_wave("волна3 1-4", {1, 2, 3, 4})
