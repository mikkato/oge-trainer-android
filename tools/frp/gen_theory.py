#!/usr/bin/env python3
"""Генерация кратких теорий (конспектов) по темам ФРП, у которых есть задачи.

Выход: gen_theory.json {code: {"title","content"}} — возобновляемый.
Конспект 700-1100 символов, markdown + формулы $...$ (рендерит KaTeX-движок
приложения). Волна ~1400 тем, 6 потоков, ~25 мин.
"""
import json, os, re, sys, threading, time
from concurrent.futures import ThreadPoolExecutor, as_completed
from urllib.request import Request, urlopen

BASE = "https://cloud.agk-russia.ru:4443/v1/chat/completions"
KEY = os.environ["HERMES_CUSTOM_BIGMODEL_API_KEY"]
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "gen_theory.json")
lock = threading.Lock()

SYSTEM = ("Ты пишешь краткий теоретический конспект для школьного тренажёра "
          "(российская ФРП). Ответь СТРОГО JSON без пояснений и без markdown-ограждений: "
          '{"title":"<название темы>","content":"<конспект>"}\n'
          "Правила content:\n"
          "1. 700-1100 символов, markdown: ## подзаголовки, **термины**, списки -.\n"
          "2. Формулы в $...$ (LaTeX). Только то, что точно верно.\n"
          "3. Структура: ключевые определения -> главное правило/формула -> 1-2 примера.\n"
          "4. Язык — для школьника указанного класса, без воды и обращений.\n"
          "5. Никаких ссылок на рисунки, таблицы и параграфы учебника.")

topics = json.load(open(os.path.join(HERE, "frp-topics.json")))
subj_title = {s["code"]: s["title"] for s in topics["subjects"]}
by_code = {t["code"]: t for t in topics["topics"]}
need = sorted({t["topic"] for t in json.load(open(os.path.join(HERE, "frp-tasks.json")))})

have = json.load(open(OUT)) if os.path.exists(OUT) else {}
todo = [c for c in need if c not in have and c in by_code and not c.endswith((".0",))
        and by_code[c].get("parent")]  # только листы тем (не разделы-секции)
print(f"тем с задачами: {len(need)}, к генерации: {len(todo)}", flush=True)

def call(code):
    t = by_code[code]
    sec = by_code.get(t.get("parent") or "", {})
    user = (f"Предмет: {subj_title.get(t['subject'], t['subject'])}. Класс {t['grade']}. "
            f"Раздел: {sec.get('title','')}. Тема: {t['title']}. Сделай конспект.")
    body = json.dumps({
        "model": "qwen3.8-flash-next",
        "messages": [{"role": "system", "content": SYSTEM},
                     {"role": "user", "content": user}],
        "temperature": 0.4, "max_tokens": 1500,
        "chat_template_kwargs": {"enable_thinking": False},
    }).encode()
    for attempt in (1, 2):
        try:
            req = Request(BASE, data=body, headers={
                "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
            with urlopen(req, timeout=120) as r:
                d = json.loads(r.read())
            msg = d["choices"][0]["message"]
            c = re.sub(r"```(json)?|```", "", msg.get("content") or msg.get("reasoning_content") or "")
            m = re.search(r"\{.*\}", c, re.S)
            j = json.loads(m.group(0)) if m else None
            if j and len(str(j.get("content", ""))) >= 300:
                return str(j.get("title") or t["title"])[:120], str(j["content"])[:3000]
        except Exception as e:
            sys.stderr.write(f"retry{attempt} {code}: {type(e).__name__} {e}\n")
            time.sleep(2 * attempt)
    return None

t0 = time.time()
ok = fail = 0
with ThreadPoolExecutor(max_workers=6) as ex:
    futs = {ex.submit(call, c): c for c in todo}
    for i, f in enumerate(as_completed(futs), 1):
        c = futs[f]
        r = f.result()
        with lock:
            if r:
                have[c] = {"title": r[0], "content": r[1]}
                ok += 1
            else:
                fail += 1
            if i % 50 == 0 or i == len(todo):
                json.dump(have, open(OUT, "w"), ensure_ascii=False)
                el = time.time() - t0
                print(f"теория {i}/{len(todo)} ok={ok} fail={fail} "
                      f"темп={i/max(el,1):.2f} тем/с", flush=True)
json.dump(have, open(OUT, "w"), ensure_ascii=False)
print(f"готово: ok={ok} fail={fail} всего в файле {len(have)}", flush=True)
