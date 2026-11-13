#!/usr/bin/env python3
"""gen_tasks/*.json + thematic_plan.json -> ассеты приложения.

Выход:
  frp-topics.json  {subjects:[{code,title}], topics:[{code,title,subject,parent,order,grade}]}
  frp-tasks.json   [{subject,topic,type,prompt,answer,solution,trusted}]
Форматы — те же, что уже умеет BankRepository (grades.json / tasks.json), без смены схемы.
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.join(HERE, "gen_tasks")
plan = json.load(open(os.path.join(HERE, "thematic_plan.json")))

SUBJ_RENAME = {"russian": "rus", "foreign2": "foreign"}  # существующие коды банка
SUBJ_TITLE = {
    "rus": "Русский язык", "literature_reading": "Литературное чтение",
    "literature": "Литература", "foreign": "Иностранный язык", "math": "Математика",
    "informatics": "Информатика", "environment": "Окружающий мир",
    "history": "История", "social": "Обществознание", "geography": "География",
    "biology": "Биология", "physics": "Физика", "chemistry": "Химия",
    "music": "Музыка", "art": "ИЗО", "tech": "Труд (технология)",
    "pe": "Физическая культура", "orkse": "ОРКСЭ",
    "odnknr": "ДНКР", "obzr": "ОБЗР",
}
# темы этих kind не получают задач (генерация их пропускает), но остаются в дереве тем
SKIP_TASKS = {"control", "project"}
# Задания, требующие графику, без неё нерешаемы — в тренажёр не попадают (кроме имеющих media)
VISUAL_RE = re.compile(r"рисунк|на рис\.|чертёж|чертеж|схем|график|диаграмм|фотограф|аэрофот|снимк|иллюстрац", re.I)
# Решение пользователя (вопрос 2): второй эшелон — вне тренажёра (в thematic_plan.json сохраняется)
NO_TRAINER = {"music", "pe", "tech", "odnknr", "orkse", "art"}

def topic_code(subj, grade, sec_idx, num):
    n = str(num).replace(".", "-").strip("-")
    base = f"{subj}{grade}" if subj != "rus" else f"rus{grade}"
    return f"{base}.{sec_idx + 1}" + (f".{n}" if n else "")

subjects, topics, tasks = {}, [], []
stats = {"gen": 0, "empty": 0}
order = 0
for key, by_grade in plan["plan"].items():
    subj = SUBJ_RENAME.get(key, key)
    if key in NO_TRAINER:
        continue
    if subj not in SUBJ_TITLE:
        print("нет названия для", subj); continue
    subjects[subj] = {"code": subj, "title": SUBJ_TITLE[subj]}
    for g, cell in sorted(by_grade.items(), key=lambda x: int(x[0])):
        gfile = os.path.join(GEN, f"{key}_{g}.json")
        gen = {}
        if os.path.exists(gfile):
            gen = {t["topic"]: t for t in json.load(open(gfile)).get("topics", [])}
        else:
            stats["empty"] += len([t for s in cell["sections"] for t in s["topics"]])
        for si, sec in enumerate(cell["sections"]):
            sec_code = topic_code(subj, g, si, "")
            order += 1
            topics.append({"code": sec_code, "title": sec["title"], "subject": subj,
                           "parent": None, "order": order, "grade": int(g)})
            for t in sec["topics"]:
                order += 1
                code = topic_code(subj, g, si, t["num"])
                topics.append({"code": code, "title": t["title"], "subject": subj,
                               "parent": sec_code, "order": order, "grade": int(g)})
                got = gen.get(t["title"])
                if not got or t["kind"] in SKIP_TASKS:
                    continue
                for task in got["tasks"]:
                    if not task.get("media") and VISUAL_RE.search(task["prompt"]):
                        stats["visual"] = stats.get("visual", 0) + 1
                        continue
                    stats["gen"] += 1
                    entry = {
                        "subject": subj, "topic": code,
                        "type": "short" if task["kind"] == "short" else "extended",
                        "prompt": task["prompt"], "answer": task["answer"],
                        "solution": task["solution"], "trusted": False,
                    }
                    if task.get("media"):
                        entry["media"] = task["media"]
                        entry["trusted"] = True  # детерминированная графика: ответ вычислен кодом
                    tasks.append(entry)

# детерминированная параметрическая графика (генерируется gen_graphs.py)
graphs_file = os.path.join(HERE, "graph_tasks.json")
if os.path.exists(graphs_file):
    for g in json.load(open(graphs_file)):
        g.setdefault("trusted", True)
        tasks.append(g)

# вердикты независимой проверки второй моделью (verify_pass.py):
# доверяем задачу только при verdict=ok; wrong_answer/ambiguous/unsolvable —
# остаются черновиками (trusted=0) для разбора админом.
def norm(s):
    s = s.lower().replace("ё", "е")
    return " ".join(re.sub(r"[^a-zа-я0-9]+", " ", s).split())

POS = {"да", "верно", "истина", "правда", "yes", "true", "1"}
NEG = {"нет", "неверно", "ложь", "no", "false", "0"}

def yn(w):
    w = norm(w)
    return "да" if w in POS else "нет" if w in NEG else None

vr_file = os.path.join(HERE, "verify_report.json")
if os.path.exists(vr_file):
    vmap = {}
    for r in json.load(open(vr_file))["tasks"]:
        vmap.setdefault(norm(r["prompt_prefix"])[:50], r)
    cnt = {"ok": 0, "flag": 0, "miss": 0}
    for e in tasks:
        if e.get("media"):
            continue  # детерминированная графика не проверялась — и не нужна
        r = vmap.get(norm(e["prompt"])[:50])
        v = r["verdict"] if r else None
        if v == "wrong_answer" and r:  # ложные срабатывания «да/нет» vs «верно/неверно»
            a, m = yn(e["answer"]), yn(r.get("my_answer", ""))
            if a is not None and a == m:
                v = "ok"
        if v == "ok":
            e["trusted"] = True
            cnt["ok"] += 1
        elif v:
            cnt["flag"] += 1
        else:
            cnt["miss"] += 1
    print("вердикты:", cnt)

json.dump({"version": plan["version"], "subjects": sorted(subjects.values(), key=lambda x: x["code"]),
           "topics": topics},
          open("frp-topics.json", "w"), ensure_ascii=False, indent=0)
json.dump(tasks, open("frp-tasks.json", "w"), ensure_ascii=False)
print(f"предметов {len(subjects)}, тем(с разделами) {len(topics)}, задач {len(tasks)}, "
      f"пустых тем {stats['empty']}")
sizes = tuple(os.path.getsize(p) for p in ("frp-topics.json", "frp-tasks.json"))
print(f"размеры: topics {sizes[0]//1024}КБ, tasks {sizes[1]//1024}КБ")
