#!/usr/bin/env python3
"""Пилотная генерация заданий через cloud.agk-russia.ru:4443 (qwen3.8-flash-next).

Замеряет: completion-токены, секунды, байты на задание — для сметы на весь план.
"""
import json, os, re, sys, time, urllib.request

BASE = "https://cloud.agk-russia.ru:4443/v1/chat/completions"
KEY = os.environ["HERMES_CUSTOM_BIGMODEL_API_KEY"]

SYSTEM = """Ты генерируешь задания для школьного тренажёра (РФ, ФРП).
Ответь СТРОГО JSON без пояснений: {"tasks":[{"prompt","answer","solution","kind"}]}
kind: "short" — ответ одно-два слова/число, проверяется точным совпадением;
"extended" — развёрнутый ответ по эталону.
Правила:
- В поле answer для short: без буквы ё (используй е), без статей/пояснений, ровно эталон.
- prompt не содержит ответ и не намекает на него порядком слов.
- В solution: короткое обоснование (1-3 предложения).
- Задания разные по типу: термин, факт, причина-следствие, найди ошибку, верно/неверно, классификация.
- Никаких «выбери вариант A/B/C/D» — только открытый короткий или развёрнутый ответ."""

def gen(topic, section, frp, grade, n=5):
    user = (f"Предмет: {frp}. Класс {grade}. Раздел: {section}. Тема: {topic}.\n"
            f"Сделай {n} заданий по теме. Уровень — школьник {grade} класса.")
    body = json.dumps({
        "model": "qwen3.8-flash-next",
        "messages": [{"role": "system", "content": SYSTEM},
                     {"role": "user", "content": user}],
        "temperature": 0.9, "max_tokens": 6000,
        "chat_template_kwargs": {"enable_thinking": False},
    }).encode()
    req = urllib.request.Request(BASE, data=body, headers={
        "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=240) as r:
        d = json.loads(r.read())
    dt = time.time() - t0
    msg = d["choices"][0]["message"]
    content = msg.get("content") or msg.get("reasoning_content") or ""
    content = re.sub(r"```(json)?|```", "", content)
    m = re.search(r"\{.*\}", content, re.S)
    tasks = json.loads(m.group(0))["tasks"] if m else []
    u = d.get("usage", {})
    return tasks, dt, u.get("completion_tokens", 0), u.get("prompt_tokens", 0)

if __name__ == "__main__":
    plan = json.load(open("thematic_plan.json"))["plan"]
    cases = []
    for subj, grade, want in (("geography", "5", 0), ("russian", "7", 2)):
        secs = plan[subj][grade]["sections"]
        flat = [(s["title"], t) for s in secs for t in s["topics"]]
        sec, topic = flat[want][0], flat[want][1]["title"]
        cases.append((subj, grade, plan[subj][grade]["frp"], sec, topic))
    out = []
    for subj, grade, frp, sec, topic in cases:
        tasks, dt, ctok, ptok = gen(topic, sec, frp, grade)
        raw = json.dumps(tasks, ensure_ascii=False).encode()
        print(f"{subj}{grade} | {topic[:38]}: {len(tasks)} зад. | "
              f"{ctok} toк/{dt:.0f}с = {ctok/max(dt,1):.1f} t/с | {len(raw)} байт | in={ptok}")
        for t in tasks[:2]:
            print(f"    [{t['kind']}] {t['prompt'][:80]} => {t['answer'][:40]}")
        out.append({"subject": subj, "grade": grade, "topic": topic, "tasks": tasks})
    json.dump(out, open("pilot_tasks.json", "w"), ensure_ascii=False, indent=1)
