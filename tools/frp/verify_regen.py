#!/usr/bin/env python3
"""Верификация ПЕРЕГЕНЕРИРОВАННЫХ задач (regen_new.json) второй моделью.

Stage A: модель вслепую решает (только prompt). Stage B (extended): сверка
моего ответа с эталоном отдельным запросом. Вердикты — regen_verify.json
(ключ norm(prompt)[:50]), возобновляемо. Финал: --merge дописывает вердикты
в verify_report.json (старые записи по тем же промптам заменяются).
"""
import json, os, re, sys, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen

BASE = "https://cloud.agk-russia.ru:4443/v1/chat/completions"
KEY = os.environ["HERMES_CUSTOM_BIGMODEL_API_KEY"]
HERE = os.path.dirname(os.path.abspath(__file__))
lock = threading.Lock()
VF = os.path.join(HERE, "regen_verify.json")

def norm(s):
    s = s.lower().replace("ё", "е")
    return " ".join(re.sub(r"[^a-zа-я0-9]+", " ", s).split())

def call(messages, max_tokens=4000):
    body = json.dumps({"model": "qwen3.8-flash-next", "messages": messages,
                       "temperature": 0.2, "max_tokens": max_tokens,
                       "chat_template_kwargs": {"enable_thinking": False}}).encode()
    for attempt in (1, 2, 3):
        try:
            req = Request(BASE, data=body, headers={
                "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
            with urlopen(req, timeout=300) as r:
                d = json.loads(r.read())
            msg = d["choices"][0]["message"]
            c = msg.get("content") or msg.get("reasoning_content") or ""
            return re.sub(r"```(json)?|```", "", c)
        except Exception:
            time.sleep(3 * attempt)
    return None

def jload(text):
    m = re.search(r"\{.*\}", text or "", re.S)
    return json.loads(m.group(0)) if m else None

POS = {"да", "верно", "истина", "правда", "yes", "true", "1"}
NEG = {"нет", "неверно", "ложь", "no", "false", "0"}
def yn(w):
    w = norm(w)
    return "да" if w in POS else "нет" if w in NEG else None

def eq_short(mine, ref):
    m, r = norm(mine), norm(ref)
    if not m:
        return False
    if m == r or r in m or m in r:
        return True
    a, b = yn(mine), yn(ref)
    return a is not None and a == b

def main():
    if "--merge" in sys.argv:
        return merge()
    res = json.load(open(VF)) if os.path.exists(VF) else {}
    # собрать новые задачи из gen_tasks
    todo = []
    for item in json.load(open(os.path.join(HERE, "regen_new.json"))):
        d = json.load(open(os.path.join(HERE, "gen_tasks", item["file"])))
        for t in d["topics"]:
            if t["topic"] == item["topic"]:
                for ta in t["tasks"]:
                    if norm(ta["prompt"]) == norm(item["prompt"]):
                        k = norm(ta["prompt"])[:50]
                        if k not in res:
                            todo.append({"file": item["file"], "topic": t["topic"], **ta})
    print(f"новых задач к проверке: {len(todo)}", flush=True)
    # stage A: батчи по 8 вслепую
    answers = {}
    batches = [todo[i:i + 8] for i in range(0, len(todo), 8)]
    def solve(b):
        nums = "\n\n".join(f"{j+1}. {t['prompt']}" for j, t in enumerate(b))
        out = call([{"role": "user", "content":
            "Реши каждое задание самостоятельно и кратко. Верни СТРОГО JSON без пояснений: "
            '{"results":[{"i":<номер>,"my_answer":"<ответ>"}]}\n\n' + nums}], 5000)
        d = jload(out)
        return b, (d or {}).get("results", []) if d else []
    t0 = time.time()
    with ThreadPoolExecutor(max_workers=6) as ex:
        futs = [ex.submit(solve, b) for b in batches]
        for i, f in enumerate(as_completed(futs), 1):
            b, results = f.result()
            with lock:
                for r in results:
                    try:
                        answers[norm(b[int(r["i"]) - 1]["prompt"])[:50]] = str(r.get("my_answer", ""))
                    except Exception:
                        pass
                if i % 50 == 0 or i == len(batches):
                    print(f"A {i}/{len(batches)} ответов={len(answers)} "
                          f"темп={i/max(time.time()-t0,1):.2f} батч/с", flush=True)
    # вердикты: short — кодом, extended — stage B
    ext = [t for t in todo if t.get("kind") == "extended"]
    def judge(t):
        k = norm(t["prompt"])[:50]
        mine = answers.get(k, "")
        if not mine:
            return k, "ambiguous", ""
        out = call([{"role": "user", "content":
            f"Задание: {t['prompt']}\nОтвет ученика: {mine}\nЭталон: {t['answer']}\n"
            'Оцени эквивалентны ли ответы. Верни СТРОГО JSON: {"verdict":"ok|wrong_answer|ambiguous"}'}], 300)
        d = jload(out)
        return k, (d or {}).get("verdict", "ambiguous"), mine
    with ThreadPoolExecutor(max_workers=6) as ex:
        futs = {ex.submit(judge, t): t for t in ext}
        for i, f in enumerate(as_completed(futs), 1):
            k, v, mine = f.result()
            with lock:
                res[k] = {"verdict": v, "my_answer": mine}
                if i % 50 == 0 or i == len(ext):
                    json.dump(res, open(VF, "w"), ensure_ascii=False)
                    print(f"B {i}/{len(ext)}", flush=True)
    for t in todo:
        if t.get("kind") == "extended":
            continue
        k = norm(t["prompt"])[:50]
        mine = answers.get(k, "")
        if not mine:
            res[k] = {"verdict": "ambiguous", "my_answer": ""}
        else:
            res[k] = {"verdict": "ok" if eq_short(mine, t["answer"]) else "wrong_answer",
                      "my_answer": mine}
    json.dump(res, open(VF, "w"), ensure_ascii=False)
    import collections
    print("итог:", dict(collections.Counter(v["verdict"] for v in res.values())), flush=True)

def merge():
    res = json.load(open(VF))
    report = json.load(open(os.path.join(HERE, "verify_report.json")))
    tasks = report["tasks"]
    # индекс старых записей по префиксу для замены
    by_pref = {}
    for i, r in enumerate(tasks):
        by_pref.setdefault(norm(r["prompt_prefix"])[:50], []).append(i)
    upd = new = 0
    for item in json.load(open(os.path.join(HERE, "regen_new.json"))):
        k = norm(item["prompt"])[:50]
        v = res.get(k)
        if not v:
            continue
        d = json.load(open(os.path.join(HERE, "gen_tasks", item["file"])))
        rec = None
        for t in d["topics"]:
            if t["topic"] == item["topic"]:
                for ta in t["tasks"]:
                    if norm(ta["prompt"])[:50] == k:
                        rec = {"file": item["file"], "topic": item["topic"],
                               "section": "", "kind": ta.get("kind", "short"),
                               "prompt_prefix": ta["prompt"][:70],
                               "reference_answer": ta["answer"],
                               "verdict": v["verdict"], "my_answer": v["my_answer"]}
        if not rec:
            continue
        idxs = by_pref.get(k)
        if idxs:
            tasks[idxs[0]] = rec
            upd += 1
        else:
            tasks.append(rec)
            new += 1
    json.dump({"tasks": tasks}, open(os.path.join(HERE, "verify_report.json"), "w"),
              ensure_ascii=False)
    print(f"merge: заменено {upd}, добавлено {new}, всего записей {len(tasks)}")

if __name__ == "__main__":
    main()
