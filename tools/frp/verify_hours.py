#!/usr/bin/env python3
"""Сверка часов fgos_thematic.json с федеральным нормативом часов в год."""
import json
from collections import defaultdict

STD = {  # часов/год, федеральный компонент (базовый уровень)
    "Русский язык": {5: 170, 6: 170, 7: 170, 8: 170},
    "Математика": {5: 175, 6: 175, 7: 175, 8: 175, 9: 175},
    "История": {5: 70, 6: 68, 7: 68, 8: 68, 9: 68},
    "Обществознание": {5: 35, 6: 35, 7: 35, 8: 35, 9: 34},
    "География": {5: 35, 6: 35, 7: 35, 8: 35, 9: 35},
    "Биология": {5: 35, 6: 35, 7: 35, 8: 35, 9: 35},
    "Физика": {7: 70, 8: 70, 9: 70},
    "Химия": {8: 68, 9: 68},
    "Литература": {5: 70, 6: 70, 7: 70, 8: 70, 9: 68},
    "Иностранный язык": {2: 68, 3: 68, 4: 68, 5: 105, 6: 105, 7: 105, 8: 105, 9: 105},
    "Информатика": {7: 35, 8: 35, 9: 35},
}

d = json.load(open("fgos_thematic.json"))
per = defaultdict(list)  # (subject, grade) -> [(hours, doc)]
for p in d:
    for g, v in p["grades"].items():
        per[(p["subject"], int(g))].append((v["hours_total"], p["id"], v["topics_total"]))

flag = ok = 0
for (s, g), vals in sorted(per.items()):
    exp = STD.get(s, {}).get(g)
    if not exp:
        continue
    for h, pid, n in vals:
        r = h / exp
        if 0.7 <= r <= 1.35:
            ok += 1
        else:
            flag += 1
            print(f"FLAG {s} {g} (id {pid}): {h}ч vs {exp} (x{r:.2f}), тем {n}")
print(f"сверка: ok={ok}, flag={flag}")
