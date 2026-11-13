#!/usr/bin/env python3
"""После gen_all (файлы перезаписаны с сортировкой по topic) переносим старые
вердикты из verify_report.json в verify/<stem>.json по новым индексам задач.
Задачи, чей prompt не встречался в отчёте, остаются непроверенными —
verify_pass докачает их по resume."""
import json, os, glob

HERE = os.path.dirname(os.path.abspath(__file__))
GEN, VDIR = os.path.join(HERE, "gen_tasks"), os.path.join(HERE, "verify")
report = json.load(open(os.path.join(HERE, "verify_report.json")))["tasks"]

# (file, prompt_prefix) -> (verdict, my_answer)
by_prompt = {}
for r in report:
    by_prompt[(r["file"], r["prompt_prefix"])] = (r["verdict"], r.get("my_answer", ""))

reindexed = fresh = 0
for gf in sorted(glob.glob(os.path.join(GEN, "*.json"))):
    stem = os.path.splitext(os.path.basename(gf))[0]
    doc = json.load(open(gf))
    vpath = os.path.join(VDIR, stem + ".json")
    old = json.load(open(vpath)) if os.path.exists(vpath) else {"stageA": {}, "final": {}}
    # если файл генерации новее состояния — был перезаписан, нужен ремэп
    if os.path.exists(vpath) and os.path.getmtime(vpath) > os.path.getmtime(gf):
        continue
    new_final = {}
    k = 0
    for t in doc["topics"]:
        for tk in t["tasks"]:
            p = tk["prompt"]
            for plen in (80, 50):
                rec = by_prompt.get((stem + ".json", p[:plen]))
                if rec:
                    break
            if rec:
                new_final[str(k)] = {"verdict": rec[0], "my_answer": rec[1] or ""}
            k += 1
    tmp = vpath + ".tmp"
    json.dump({"stageA": old.get("stageA", {}), "final": new_final},
              open(tmp, "w"), ensure_ascii=False)
    os.replace(tmp, vpath)
    reindexed += 1
    fresh += k - len(new_final)
print(f"пересобрано состояний: {reindexed}, задач к свежей проверке: {fresh}")
