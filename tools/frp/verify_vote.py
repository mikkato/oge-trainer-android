#!/usr/bin/env python3
"""Автоприёмка черновиков мажоритарным голосованием вместо ручной приёмки.
Два новых независимых раунда слепой проверки (разный shuffle + температура).
Правило: trusted = ok в ОБОИХ раундах. Промежуточный прогресс — vote_state.json.
"""
import json, os, random, re, sys, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import verify_pass as VP

HERE = os.path.dirname(os.path.abspath(__file__))
TASKS = os.path.join(HERE, "frp-tasks.json")
STATE = os.path.join(HERE, "vote_state.json")
lock = threading.Lock()
ROUNDS = [("R2", 0.3, 11), ("R3", 0.6, 77)]

POS = {"да", "верно", "истина", "true", "1", "плюс"}
NEG = {"нет", "неверно", "ложь", "false", "0", "минус"}

def yn(w):
    w = VP.norm(w)
    return "да" if w in POS else "нет" if w in NEG else None

def call_t(messages, max_tokens, temp):
    body = {"model": VP.MODEL, "messages": messages, "max_tokens": max_tokens,
            "temperature": temp, "chat_template_kwargs": {"enable_thinking": False}}
    data = json.dumps(body, ensure_ascii=False).encode()
    import urllib.request
    last = None
    for attempt in range(VP.MAX_TRIES):
        try:
            req = urllib.request.Request(VP.API, data=data, headers={
                "Content-Type": "application/json", "Authorization": "Bearer " + VP.KEY})
            r = json.load(urllib.request.urlopen(req, timeout=VP.TIMEOUT))
            c = r["choices"][0]["message"].get("content")
            if c is None:
                raise ValueError("null content")
            return c
        except Exception as e:
            last = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"API failed: {last!r}")

def parse(content):
    m = VP.JSON_RE.search(content or "")
    if not m:
        raise ValueError("no JSON")
    try:
        obj = json.loads(m.group(0))
    except json.JSONDecodeError:
        obj = {"results": [{"i": int(i), "verdict": v, "my_answer": a}
                           for i, v, a in VP.ITEM_RE.findall(m.group(0))]}
    return {int(r["i"]): {"verdict": str(r.get("verdict", "")).lower().strip(),
                          "my_answer": str(r.get("my_answer", "")).strip()}
            for r in obj.get("results", [])}

def stageA_batch(items, temp):
    resp = parse(call_t([{"role": "user", "content": VP.stageA_prompt(items)}], 3000, temp))
    return resp

def stageB_batch(items, temp):
    resp = parse(call_t([{"role": "user", "content": VP.stageB_prompt(items)}], 2500, temp))
    return resp

def run_round(rnd, temp, seed, drafts, state):
    idx = list(range(len(drafts)))
    random.Random(seed).shuffle(idx)
    # батчи по 8 и ≤7000 симв, пропуск уже проверенных в этом раунде
    batches, cur, cur_len = [], [], 0
    for i in idx:
        key = f"{rnd}:{i}"
        if key in state:
            continue
        p = drafts[i]["prompt"]
        if len(cur) >= 8 or cur_len + len(p) > 7000:
            batches.append(cur); cur, cur_len = [], 0
        cur.append(i); cur_len += len(p)
    if cur:
        batches.append(cur)
    print(f"[{rnd}] батчей: {len(batches)}", flush=True)
    done = [0]

    def work(b):
        items = [(j + 1, drafts[i]["prompt"]) for j, i in enumerate(b)]
        try:
            a = stageA_batch(items, temp)
        except Exception as e:
            return f"err {type(e).__name__}"
        need_b = []
        finals = {}
        for j, i in enumerate(b):
            r = a.get(j + 1, {"verdict": "error", "my_answer": ""})
            t = drafts[i]
            if t["type"] == "short":
                if r["verdict"] != "ok":
                    finals[i] = r["verdict"] if r["verdict"] in ("ambiguous", "unsolvable") else "wrong_answer"
                else:
                    if VP.short_matches(r["my_answer"], t["answer"]):
                        finals[i] = "ok"
                    else:
                        y1, y2 = yn(r["my_answer"]), yn(t["answer"])
                        finals[i] = "ok" if (y1 and y1 == y2) else "wrong_answer"
            else:
                if r["verdict"] in ("ambiguous", "unsolvable"):
                    finals[i] = r["verdict"]
                else:
                    need_b.append((j, i, r))
        if need_b:
            bitems = [(j + 1, drafts[i]["prompt"], drafts[i]["answer"], r["my_answer"])
                      for j, i, r in need_b]
            try:
                bb = stageB_batch(bitems, temp)
                for j, i, r in need_b:
                    finals[i] = bb.get(j + 1, {}).get("verdict", "error") or "error"
            except Exception:
                for j, i, r in need_b:
                    finals[i] = "error"
        with lock:
            for i, v in finals.items():
                state[f"{rnd}:{i}"] = v
            done[0] += 1
            if done[0] % 20 == 0:
                json.dump(state, open(STATE, "w"), ensure_ascii=False)
                print(f"[{rnd}] {done[0]}/{len(batches)} батчей", flush=True)
        return "ok"

    with ThreadPoolExecutor(max_workers=6) as ex:
        list(as_completed([ex.submit(work, b) for b in batches]))
    json.dump(state, open(STATE, "w"), ensure_ascii=False)

def main():
    tasks = json.load(open(TASKS))
    drafts = [t for t in tasks if not t["trusted"] and not t.get("media")]
    print("черновиков к голосованию:", len(drafts))
    state = json.load(open(STATE)) if os.path.exists(STATE) else {}
    for rnd, temp, seed in ROUNDS:
        run_round(rnd, temp, seed, drafts, state)
    promoted = kept = err = 0
    for i, t in enumerate(drafts):
        vs = [state.get(f"{r}:{i}") for r, _, _ in ROUNDS]
        if "error" in vs or any(v is None for v in vs):
            err += 1
            continue
        if all(v == "ok" for v in vs):
            t["trusted"] = True
            promoted += 1
        else:
            kept += 1
    json.dump(tasks, open(TASKS, "w"), ensure_ascii=False, indent=1)
    print(f"промоушн: {promoted}, осталось черновиков: {kept}, ошибок раундов: {err}")
    print("trusted всего:", sum(1 for t in tasks if t["trusted"]))

if __name__ == "__main__":
    main()
