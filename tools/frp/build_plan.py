#!/usr/bin/env python3
"""fgos_thematic.json -> thematic_plan.json для ассетов oge-trainer-android.

Ключи предметов берутся из app/src/main/assets/school/curriculum.json;
при нескольких ФРП на класс берётся базовый уровень, затем обычный, затем углублённый.
"""
import json, sys, re

SRC = "fgos_thematic.json"
CURRICULUM = sys.argv[1] if len(sys.argv) > 1 else "curriculum.json"
OUT = sys.argv[2] if len(sys.argv) > 2 else "thematic_plan.json"

SUBJ_MAP = {
    "Русский язык": "russian",
    "Литературное чтение": "literature_reading",
    "Литература": "literature",
    "Математика": "math",
    "Информатика": "informatics",
    "Иностранный язык": "foreign",
    "Окружающий мир": "environment",
    "Музыка": "music",
    "Изобразительное искусство": "art",
    "ИЗО": "art",
    "Труд (технология)": "tech",
    "Физическая культура": "pe",
    "ОРКСЭ": "orkse",
    "Основы религиозных культур и светской этики": "orkse",
    "Духовно-нравственная культура России": "odnknr",
    "История": "history",
    "Обществознание": "social",
    "География": "geography",
    "Биология": "biology",
    "Физика": "physics",
    "Химия": "chemistry",
    "Второй иностранный язык": "foreign2",
    "Труд": "tech",
    "Основы безопасности и защиты Родины": "obzr",
}

cur = json.load(open(CURRICULUM))
known = set(cur["subjects"])

def prio(name):
    n = name.lower()
    if "углубл" in n:
        return 2
    if "базов" in n:
        return 0
    return 1

best = {}  # (key, grade) -> (prio, doc, sections)
for p in json.load(open(SRC)):
    key = SUBJ_MAP.get(p["subject"])
    if key is None or (key not in known and key != "foreign2"):
        print(f"ПРОПУСК: предмет не отображён: {p['subject']} (id {p['id']})")
        continue
    for g, v in p["grades"].items():
        k = (key, g)
        pr = prio(p["name"])
        if k not in best or pr < best[k][0]:
            best[k] = (pr, p, v)

plan = {}
for (key, g), (pr, p, v) in best.items():
    plan.setdefault(key, {})[g] = {
        "frp_id": p["id"],
        "frp": p["name"],
        "hours": v["hours_total"],
        "sections": [
            {"title": s["title"],
             "topics": [{"num": t["num"], "title": t["title"],
                         "hours": t["hours"], "kind": t["kind"]}
                        for t in s["topics"]]}
            for s in v["sections"]
        ],
    }
out = {"version": "frp-2026",
       "source": "ФРП, fgosreestr.edsoo.ru (вариант 1 тематического планирования)",
       "plan": {k: dict(sorted(v.items(), key=lambda x: int(x[0])))
                for k, v in sorted(plan.items())}}
json.dump(out, open(OUT, "w"), ensure_ascii=False, indent=1)
n_gr = sum(len(v) for v in plan.values())
n_t = sum(t["hours"] and 1 for k in plan.values() for v in k.values()
          for s in v["sections"] for t in s["topics"])
print(f"предметов: {len(plan)}, ячеек предмет×класс: {n_gr}, тем: {n_t} -> {OUT}")
