"""Добор банка: развёрнутые (math/physics по темам) и письменные (ru) задания.
Исправление предыдущего прогона: тип задания сопоставлялся с кодом темы вместо кода предмета.
"""
import time
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

from app.db import SessionLocal
from app.models import Subject, Task, Topic
from app.services import llm

EXTENDED_PER_TOPIC = 2
WRITING_EACH = 4          # изложений и сочинений на всю систему
WORKERS = 3

db = SessionLocal()
db.expire_on_commit = False
subjects = {s.id: (s.code, s.title) for s in db.query(Subject).all()}
topics = [(t.id, t.subject_id, t.code, t.title)
          for t in db.query(Topic).order_by(Topic.subject_id, Topic.order).all()]
have = Counter((t.topic_id, t.type) for t in db.query(Task).all())
existing_prompts = {p.strip()[:120] for (p,) in db.query(Task.prompt).all()}

plan = []
for tid, sid, code, title in topics:
    subject_code = subjects[sid][0]
    if subject_code in ("math", "physics"):
        need = EXTENDED_PER_TOPIC - have.get((tid, "extended"), 0)
        plan += [(tid, sid, subject_code, title, "extended")] * max(0, need)

rus = [(tid, sid, code, title) for tid, sid, code, title in topics if subjects[sid][0] == "rus"]
for ttype in ("izlozhenie", "sochinenie"):
    have_n = sum(1 for t in db.query(Task).all() if t.type == ttype)
    need = WRITING_EACH - have_n
    for i in range(max(0, need)):
        t = rus[i % len(rus)]
        plan.append((t[0], t[1], "rus", t[3], ttype))

print(f"planned={len(plan)} extended={sum(1 for p in plan if p[4]=='extended')} "
      f"writing={sum(1 for p in plan if p[4] in ('izlozhenie','sochinenie'))}", flush=True)


def call(args):
    tid, sid, subject_code, title, ttype = args
    try:
        return tid, sid, ttype, llm.generate_task(subject_code, subjects[sid][1], title, ttype)
    except Exception as e:  # noqa: BLE001
        return tid, sid, ttype, {"__error__": str(e)[:200]}


def max_points_for(ttype, data):
    if ttype == "extended":
        return 2
    if ttype == "izlozhenie":
        return 6
    if ttype == "sochinenie":
        return 7
    return max(1, min(2, int(data.get("max_points") or 1)))


ok = dup = err = 0
t0 = time.time()
with ThreadPoolExecutor(WORKERS) as ex:
    for tid, sid, ttype, data in ex.map(call, plan):
        if "__error__" in data or not data.get("prompt"):
            err += 1
            print("ERR", ttype, str(data.get("__error__"))[:140], flush=True)
            continue
        key = data["prompt"].strip()[:120]
        if key in existing_prompts:
            dup += 1
            continue
        existing_prompts.add(key)
        db.add(Task(subject_id=sid, topic_id=tid, type=ttype, prompt=data["prompt"],
                    answer=data.get("answer", "") or "", solution=data.get("solution", "") or "",
                    max_points=max_points_for(ttype, data)))
        db.commit()
        ok += 1
        if ok % 20 == 0:
            print(f"ok {ok}/{len(plan)} {time.time()-t0:.0f}s", flush=True)

print(f"DONE ok={ok} dup={dup} err={err} {time.time()-t0:.0f}s", flush=True)
