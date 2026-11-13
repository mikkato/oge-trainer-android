"""Экспорт контента и истории перед демонтажом сервера (v2).
Выгружает кодификатор, конспекты, банк задач, иллюстрации, фоны, программы,
а также попытки и освоение (без хешей паролей).
"""
import hashlib
import json
import os
import shutil
import sqlite3

DB = "/home/mikkato/oge-trainer/data/app.db"
OUT = "/home/mikkato/content-export"
ILL = "/home/mikkato/oge-trainer/backend/app/illustrations"
BG = "/home/mikkato/oge-trainer/frontend/public/backgrounds"

for d in ("", "/illustrations", "/backgrounds"):
    os.makedirs(OUT + d, exist_ok=True)

db = sqlite3.connect(DB)
db.row_factory = sqlite3.Row


def q(sql, *args):
    return [dict(r) for r in db.execute(sql, args).fetchall()]


subjects = q("select id, code, title, description from subjects order by id")
subj_code = {s["id"]: s["code"] for s in subjects}

topics = q('select id, subject_id, parent_id, code, title, "order", source from topics order by subject_id, "order", id')
topic_code = {t["id"]: t["code"] for t in topics}
topics_out = [{
    "code": t["code"], "title": t["title"], "subject": subj_code[t["subject_id"]],
    "parent": topic_code.get(t["parent_id"]) if t["parent_id"] else None,
    "order": t["order"], "source": t["source"],
} for t in topics]
valid_topic_ids = set(topic_code)

theories = q("select topic_id, title, content from theories order by topic_id")
theories_out = [{"topic": topic_code[t["topic_id"]], "title": t["title"], "content": t["content"]}
                for t in theories if t["topic_id"] in valid_topic_ids]

tasks = q("select subject_id, topic_id, type, prompt, answer, solution, trusted, max_points from tasks order by id")
tasks_out = [{
    "subject": subj_code[t["subject_id"]], "topic": topic_code[t["topic_id"]],
    "type": t["type"], "prompt": t["prompt"], "answer": t["answer"], "solution": t["solution"],
    "trusted": bool(t["trusted"]), "max_points": t["max_points"],
} for t in tasks if t["topic_id"] in valid_topic_ids]

users = q("select id, username, full_name, role from users order by id")
uname = {u["id"]: u["username"] for u in users}

programs = q("""select p.id, p.user_id, p.type, p.grade, s.code from programs p
                left join program_subjects ps on ps.program_id = p.id
                left join subjects s on s.id = ps.subject_id order by p.id, s.code""")
progs = {}
for p in programs:
    key = p["id"]
    progs.setdefault(key, {"username": uname.get(p["user_id"], "?"), "type": p["type"],
                           "grade": p["grade"], "subjects": []})
    if p["code"]:
        progs[key]["subjects"].append(p["code"])

attempts = q("""select a.user_id, a.answer_text, a.correct, a.score, a.points, a.max_points,
                       a.feedback, a.created_at, t.prompt, t.type, t.topic_id
                from attempts a join tasks t on t.id = a.task_id order by a.id""")
attempts_out = [{
    "username": uname.get(a["user_id"], "?"), "topic": topic_code.get(a["topic_id"]),
    "type": a["type"], "prompt": a["prompt"], "answer_text": a["answer_text"],
    "correct": bool(a["correct"]), "score": a["score"], "points": a["points"],
    "max_points": a["max_points"], "feedback": a["feedback"], "created_at": a["created_at"],
} for a in attempts if a["topic_id"] in valid_topic_ids]

mastery = q("""select user_id, topic_id, mastery, attempts_count, last_attempt_at
               from topic_mastery order by user_id, topic_id""")
mastery_out = [{
    "username": uname.get(m["user_id"], "?"), "topic": topic_code[m["topic_id"]],
    "mastery": round(m["mastery"], 4), "attempts_count": m["attempts_count"],
    "last_attempt_at": m["last_attempt_at"],
} for m in mastery if m["topic_id"] in valid_topic_ids]

n_ill = n_bg = 0
for f in sorted(os.listdir(ILL)):
    if f.endswith(".svg"):
        shutil.copy2(os.path.join(ILL, f), os.path.join(OUT, "illustrations", f))
        n_ill += 1
for f in sorted(os.listdir(BG)):
    if f.endswith(".svg"):
        shutil.copy2(os.path.join(BG, f), os.path.join(OUT, "backgrounds", f))
        n_bg += 1

files = {
    "subjects.json": subjects,
    "topics.json": topics_out,
    "theories.json": theories_out,
    "tasks.json": tasks_out,
    "programs.json": list(progs.values()),
    "users.json": users,
    "attempts-archive.json": attempts_out,
    "mastery-archive.json": mastery_out,
}
checksums = {}
for name, data in files.items():
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
    checksums[name] = hashlib.sha256(open(path, "rb").read()).hexdigest()

meta = {
    "version": 2,
    "source": "oge-trainer 0.4.0, app.db",
    "exported_at": "2026-11-13",
    "counts": {
        "subjects": len(subjects), "topics": len(topics_out), "theories": len(theories_out),
        "tasks": len(tasks_out), "programs": len(progs), "users": len(users),
        "attempts": len(attempts_out), "mastery": len(mastery_out),
        "illustrations": n_ill, "backgrounds": n_bg,
    },
    "topics_without_theory": sorted({t["code"] for t in topics_out} - {x["topic"] for x in theories_out}),
    "tasks_per_type": {k: sum(1 for t in tasks_out if t["type"] == k)
                       for k in sorted({t["type"] for t in tasks_out})},
    "sha256": checksums,
}
with open(os.path.join(OUT, "meta.json"), "w", encoding="utf-8") as fh:
    json.dump(meta, fh, ensure_ascii=False, indent=1)

print(json.dumps(meta, ensure_ascii=False, indent=1))
