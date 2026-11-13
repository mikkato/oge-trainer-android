#!/usr/bin/env python3
"""Перегенерация ОТВЕРГНУТЫХ задач (verdict != ok) с более жёстким промптом.

Заменяет в gen_tasks только флакованные задачи (ok-задачи тем не трогает):
для каждой темы шлёт список отвергнутых и просит столько же новых задач тех же
kind. Возобновляемая по regen_state.json. Списки новых задач — regen_new.json
(для выборочной верификации verify_regen.py).
"""
import json, os, re, sys, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen

BASE = "https://cloud.agk-russia.ru:4443/v1/chat/completions"
KEY = os.environ["HERMES_CUSTOM_BIGMODEL_API_KEY"]
HERE = os.path.dirname(os.path.abspath(__file__))
lock = threading.Lock()

SYSTEM = json.load(open(os.path.join(HERE, "gen_system_strict.json")))["system"]

def norm(s):
    s = s.lower().replace("ё", "е")
    return " ".join(re.sub(r"[^a-zа-я0-9]+", " ", s).split())

POS = {"да", "верно", "истина", "правда", "yes", "true", "1"}
NEG = {"нет", "неверно", "ложь", "no", "false", "0"}
def yn(w):
    w = norm(w)
    return "да" if w in POS else "нет" if w in NEG else None

# ---------- кто под отсев ----------
rep = json.load(open(os.path.join(HERE, "verify_report.json")))["tasks"]
vmap = {}
for r in rep:
    vmap.setdefault(norm(r["prompt_prefix"])[:50], r)

jobs = []  # (fname, topic, [отвергнутые задачи])
state_path = os.path.join(HERE, "regen_state.json")
state = set(map(tuple, json.load(open(state_path)))) if os.path.exists(state_path) else set()
for fn in sorted(os.listdir(os.path.join(HERE, "gen_tasks"))):
    d = json.load(open(os.path.join(HERE, "gen_tasks", fn)))
    frp = d.get("frp", fn)
    for t in d.get("topics", []):
        flagged = []
        for ta in t["tasks"]:
            r = vmap.get(norm(ta["prompt"])[:50])
            if not r:
                continue
            v = r["verdict"]
            if v == "wrong_answer":
                a, m = yn(ta["answer"]), yn(r.get("my_answer", ""))
                if a is not None and a == m:
                    v = "ok"
            if v != "ok":
                flagged.append(ta)
        if flagged and (fn, t["topic"]) not in state:
            jobs.append((fn, frp, t, flagged))
print(f"тем к перегенерации: {len(jobs)}", flush=True)

def call(user, n):
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
            return json.loads(m.group(0))["tasks"] if m else []
        except Exception as e:
            sys.stderr.write(f"retry{attempt} {fn} {n}: {type(e).__name__} {e}\n")
            time.sleep(3 * attempt)
    return None

def work(job):
    fn, frp, t, flagged = job
    n = len(flagged)
    kinds = ", ".join(x.get("kind", "short") for x in flagged)
    old = "\n".join(f"- {x['prompt'][:150]}" for x in flagged)
    user = (f"Предмет: {frp}. Тема: {t['topic']}. Раздел: {t.get('section','')}.\n"
            f"Эти {n} заданий темы отклонены независимой проверкой (эталон неверен или "
            f"ответ неоднозначен):\n{old}\n"
            f"Сделай {n} НОВЫХ заданий по той же теме, тех же типов (kind): {kinds}. "
            f"Старые формулировки не повторяй. Соблюдай все правила жёсткого промпта.")
    raw = call(user, n)
    if raw is None:
        return job, None
    # чистка как в gen_all + отсев повторов внутри темы и старых промптов
    have_prompts = {norm(x["prompt"])[:50] for x in t["tasks"]}
    clean = []
    for task in raw:
        if not isinstance(task, dict):
            continue
        p, a = str(task.get("prompt", "")).strip(), str(task.get("answer", "")).strip()
        k = task.get("kind", "short")
        if not p or not a or norm(p)[:50] in have_prompts:
            continue
        if k == "short":
            a = a.replace("ё", "е")[:80]
        if len(a) > 400:
            k = "extended"
        have_prompts.add(norm(p)[:50])
        clean.append({"prompt": p, "answer": a, "kind": k,
                      "solution": str(task.get("solution", "")).replace("ё", "е")[:600]})
    return job, clean[:n] if clean else None

stats = {"ok": 0, "fail": 0, "new": []}
t0 = time.time()
with ThreadPoolExecutor(max_workers=6) as ex:
    futs = [ex.submit(work, j) for j in jobs]
    for i, f in enumerate(as_completed(futs), 1):
        (fn, frp, t, flagged), clean = f.result()
        with lock:
            if clean:
                stats["ok"] += 1
                flagged_set = {id(x) for x in flagged}
                # замена 1-в-1: новые задачи на места отвергнутых
                newtasks, it = [], iter(clean)
                for x in t["tasks"]:
                    newtasks.append(x if id(x) not in flagged_set else next(it, x))
                while True:
                    try:
                        newtasks.append(next(it))
                    except StopIteration:
                        break
                t["tasks"] = newtasks
                d = json.load(open(os.path.join(HERE, "gen_tasks", fn)))
                for tt in d["topics"]:
                    if tt["topic"] == t["topic"]:
                        tt["tasks"] = newtasks
                json.dump(d, open(os.path.join(HERE, "gen_tasks", fn), "w"),
                          ensure_ascii=False, indent=1)
                state.add((fn, t["topic"]))
                for x in clean:
                    stats["new"].append({"file": fn, "topic": t["topic"], "prompt": x["prompt"]})
            else:
                stats["fail"] += 1
            if i % 50 == 0 or i == len(jobs):
                json.dump(sorted(state), open(state_path, "w"), ensure_ascii=False)
                el = time.time() - t0
                print(f"переген {i}/{len(jobs)} ok={stats['ok']} fail={stats['fail']} "
                      f"темп={i/max(el,1):.2f} тем/с", flush=True)
json.dump(sorted(state), open(state_path, "w"), ensure_ascii=False)
json.dump(stats["new"], open(os.path.join(HERE, "regen_new.json"), "w"), ensure_ascii=False)
print(f"готово: заменено тем {stats['ok']}, не вышло {stats['fail']}, "
      f"новых задач {len(stats['new'])}", flush=True)
