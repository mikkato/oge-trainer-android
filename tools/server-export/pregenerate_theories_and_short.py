"""Предгенерация банка контента перед демонтажом сервера (v2, идемпотентно).
Добирает конспекты для тем без теории и задачи до планового объёма на тему/тип.
Сеть — в пуле потоков; в потоки передаются только простые типы, ORM в потоках не используется.
"""
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

from app.db import SessionLocal
from app.models import Subject, Task, Theory, Topic
from app.services import llm

SHORT_PER_TOPIC = 4
EXTENDED_PER_TOPIC = 2
WRITING_PER_TOPIC = 3
WORKERS = 3

db = SessionLocal()
db.expire_on_commit = False

subjects = {s.id: (s.code, s.title) for s in db.query(Subject).all()}
topics = [(t.id, t.subject_id, t.code, t.title)
          for t in db.query(Topic).order_by(Topic.subject_id, Topic.order).all()]
have_theory = {tid for (tid,) in db.query(Theory.topic_id).all()}
existing_prompts = {p.strip()[:120] for (p,) in db.query(Task.prompt).all()}
have = Counter((t.topic_id, t.type) for t in db.query(Task).all())

print(f"topics={len(topics)} theories={len(have_theory)} tasks={sum(have.values())}", flush=True)


def call_theory(args):
    tid, sid, code, title = args
    try:
        return tid, llm.generate_theory(subjects[sid][0], title)
    except Exception as e:  # noqa: BLE001
        return tid, {"__error__": str(e)[:200]}


def call_task(args):
    tid, sid, code, title, task_type = args
    try:
        return (tid, sid, task_type,
                llm.generate_task(subjects[sid][0], subjects[sid][1], title, task_type))
    except Exception as e:  # noqa: BLE001
        return tid, sid, task_type, {"__error__": str(e)[:200]}


# --- фаза 1: конспекты ---
missing = [t for t in topics if t[0] not in have_theory]
ok_t = err_t = 0
t0 = time.time()
if missing:
    with ThreadPoolExecutor(WORKERS) as ex:
        for tid, data in ex.map(call_theory, missing):
            if "__error__" in data:
                err_t += 1
                print("THEORY_ERR", tid, data["__error__"], flush=True)
                continue
            db.add(Theory(topic_id=tid, title=data.get("title") or "",
                          content=data.get("content") or "", illustrations="[]"))
            db.commit()
            ok_t += 1
            if ok_t % 10 == 0:
                print(f"theory {ok_t}/{len(missing)} {time.time() - t0:.0f}s", flush=True)
print(f"PHASE1 done ok={ok_t} err={err_t} {time.time() - t0:.0f}s", flush=True)

# --- фаза 2: банк задач ---
plan = []
for tid, sid, code, title in topics:
    want = {"short": SHORT_PER_TOPIC}
    if code in ("math", "physics"):
        want["extended"] = EXTENDED_PER_TOPIC
    if code == "rus":
        want["izlozhenie"] = WRITING_PER_TOPIC
        want["sochinenie"] = WRITING_PER_TOPIC
    for ttype, n in want.items():
        need = n - have.get((tid, ttype), 0)
        plan += [(tid, sid, code, title, ttype)] * max(0, need)

print(f"tasks planned={len(plan)}", flush=True)


def max_points_for(task_type, data):
    if task_type == "extended":
        return 2
    if task_type == "izlozhenie":
        return 6
    if task_type == "sochinenie":
        return 7
    return max(1, min(2, int(data.get("max_points") or 1)))


ok_q = dup_q = err_q = 0
t1 = time.time()
if plan:
    with ThreadPoolExecutor(WORKERS) as ex:
        for tid, sid, task_type, data in ex.map(call_task, plan):
            if "__error__" in data or not data.get("prompt"):
                err_q += 1
                print("TASK_ERR", task_type, str(data.get("__error__"))[:160], flush=True)
                continue
            key = data["prompt"].strip()[:120]
            if key in existing_prompts:
                dup_q += 1
                continue
            existing_prompts.add(key)
            db.add(Task(subject_id=sid, topic_id=tid, type=task_type,
                        prompt=data["prompt"], answer=data.get("answer", "") or "",
                        solution=data.get("solution", "") or "",
                        max_points=max_points_for(task_type, data)))
            db.commit()
            ok_q += 1
            if ok_q % 25 == 0:
                print(f"task {ok_q}/{len(plan)} {time.time() - t1:.0f}s", flush=True)

print(f"PHASE2 done ok={ok_q} dup={dup_q} err={err_q} {time.time() - t1:.0f}s", flush=True)
print(f"TOTALS theories_added={ok_t} tasks_added={ok_q}", flush=True)
