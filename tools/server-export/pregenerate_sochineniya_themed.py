"""Добор сочинений с тематическими формулировками, чтобы получить разные исходные тексты."""
from app.db import SessionLocal
from app.models import Subject, Task, Topic
from app.services import llm

THEMES = [
    "Сочинение 13.3 о дружбе",
    "Сочинение 13.3 о доброте",
    "Сочинение 13.3 о смелости и трусости",
    "Сочинение 13.3 о честности",
    "Сочинение 13.3 о сострадании",
]

db = SessionLocal()
db.expire_on_commit = False
subjects = {s.id: (s.code, s.title) for s in db.query(Subject).all()}
existing_prompts = {p.strip()[:120] for (p,) in db.query(Task.prompt).all()}
topic = db.query(Topic).filter(Topic.code == "rus.speech.3").first()
subject_code, subject_title = subjects[topic.subject_id]

ok = dup = err = 0
for theme in THEMES:
    try:
        data = llm.generate_task(subject_code, subject_title, theme, "sochinenie")
    except Exception as e:  # noqa: BLE001
        err += 1
        print("ERR", theme, str(e)[:140], flush=True)
        continue
    key = (data.get("prompt") or "").strip()[:120]
    if not key or key in existing_prompts:
        dup += 1
        print("dup", theme, flush=True)
        continue
    existing_prompts.add(key)
    db.add(Task(subject_id=topic.subject_id, topic_id=topic.id, type="sochinenie",
                prompt=data["prompt"], answer=data.get("answer", "") or "",
                solution=data.get("solution", "") or "", max_points=7))
    db.commit()
    ok += 1
    print("ok", theme, len(data["prompt"]), flush=True)
print(f"DONE ok={ok} dup={dup} err={err}", flush=True)
