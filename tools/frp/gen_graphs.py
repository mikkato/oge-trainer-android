#!/usr/bin/env python3
"""Детерминированная графика для тренажёра (решение B).

1. PNG-графики в app/src/main/assets/bank/media/:
   - bank/     — восстанавливаемые рисунки к заданиям банка ФИПИ;
   - graphs/   — параметрические задачи (линейная функция, v(t), жёсткость).
2. graph_tasks.json — новые ФРП-задачи с media (trusted проставит агрегатор).
3. Правит app/.../assets/bank/tasks.json: дописывает media решаемым банковским
   задачам и снимает trusted с нерешаемых без реальной картинки.

Ответ всегда вычисляется из тех же параметров, что рисуют картинку, —
график гарантированно согласован с эталоном.
"""
import json, os, random, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import rcParams

rcParams["axes.unicode_minus"] = False
rcParams["font.size"] = 11
rcParams["axes.grid"] = True
rcParams["grid.alpha"] = 0.4

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.environ.get("REPO", os.path.expanduser("~/AndroidProjects/oge-trainer-android"))
MEDIA = os.path.join(REPO, "app/src/main/assets/bank/media")
random.seed(20261113)

def axes_style(ax, xlabel, ylabel):
    ax.spines["left"].set_position("zero"); ax.spines["bottom"].set_position("zero")
    ax.spines["right"].set_visible(False); ax.spines["top"].set_visible(False)
    ax.set_xlabel(xlabel, loc="right"); ax.set_ylabel(ylabel, loc="top", rotation=0)
    # ноль на обеих осях рисуется один раз и не мешает
    ax.set_xticks([t for t in ax.get_xticks() if t != 0])
    ax.set_yticks([t for t in ax.get_yticks() if t != 0])

def png(path):
    full = os.path.join(MEDIA, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    plt.tight_layout(); plt.savefig(full, dpi=80); plt.close()
    # в БД храним путь относительно assets/bank/ — как ожидает Kotlin (../bank/<media>)
    return "media/" + path

def grid_line(path, k, b, ask, xlo=-5, xhi=5):
    """График y=kx+b на клетчатой сетке; ask — 'k' или 'b'."""
    fig, ax = plt.subplots(figsize=(6.4, 4.8))
    xs = [xlo, xhi]
    ax.plot(xs, [k * x + b for x in xs], lw=2.2, color="#1d4ed8")
    ax.set_xticks(range(xlo, xhi + 1)); ax.set_yticks(range(xlo, xhi + 1))
    ax.set_xlim(xlo, xhi); ax.set_ylim(xlo, xhi)
    ax.axhline(0, color="black", lw=1); ax.axvline(0, color="black", lw=1)
    axes_style(ax, "x", "y")
    return png(path)

def vt_line(path, v0, a, T):
    """v(t)=v0+at; dashed marker на t=T."""
    fig, ax = plt.subplots(figsize=(6.4, 4.8))
    tmax = T + 2
    ax.plot([0, tmax], [v0, v0 + a * tmax], lw=2.2, color="#1d4ed8")
    ax.plot([T, T], [0, v0 + a * T], ls="--", color="gray", lw=1.2)
    ax.set_xticks(range(0, int(tmax) + 1))
    ax.axhline(0, color="black", lw=1); ax.axvline(0, color="black", lw=1)
    ax.set_xlim(0, tmax); ax.set_ylim(0, v0 + a * tmax + 2)
    axes_style(ax, "t, с", "v, м/с")
    return png(path)

def hooke_line(path, k, xmax_cm=20):
    """F=kx, ось x в см (читается целыми)."""
    fig, ax = plt.subplots(figsize=(6.4, 4.8))
    xs = [0, xmax_cm]
    ax.plot(xs, [k * x / 100 for x in xs], lw=2.2, color="#1d4ed8", marker="o")
    ax.set_xticks(range(0, xmax_cm + 1, 5))
    ax.axhline(0, color="black", lw=1); ax.axvline(0, color="black", lw=1)
    ax.set_xlim(0, xmax_cm + 1); ax.set_ylim(0, k * xmax_cm / 100 + 1)
    axes_style(ax, "x, см", "F, Н")
    return png(path)

def bank_geo_map(path):
    """Фрагмент карты: горизонтали, точки А и Б, стрелка север."""
    import numpy as np
    fig, ax = plt.subplots(figsize=(6.4, 4.8))
    t = np.linspace(0, 2 * np.pi, 200)
    for r, cx in ((3, 2), (2.2, 2), (1.4, 2)):
        ax.plot(cx + r * np.cos(t) * 1.4, 3 + r * np.sin(t), color="#a16207", lw=1.1)
    ax.annotate("А", (2.4, 2.6), fontsize=14, weight="bold")
    ax.annotate("Б", (5.6, 0.9), fontsize=14, weight="bold")
    ax.annotate("", xy=(6.7, 5.2), xytext=(6.7, 4.2), arrowprops=dict(arrowstyle="->"))
    ax.text(6.85, 4.6, "С", fontsize=12)
    ax.set_xlim(0, 7.5); ax.set_ylim(0, 5.6); ax.grid(False); ax.axis("off")
    return png(path)

def bank_hooke(path):
    """Банковский график: прямая через (0,0) и (20 см; 8 Н) — жёсткость 40 Н/м."""
    fig, ax = plt.subplots(figsize=(6.4, 4.8))
    ax.plot([0, 20], [0, 8], lw=2.2, color="#1d4ed8", marker="o")
    ax.set_xticks(range(0, 21, 5)); ax.set_yticks(range(0, 9, 2))
    ax.axhline(0, color="black", lw=1); ax.axvline(0, color="black", lw=1)
    ax.set_xlim(0, 21); ax.set_ylim(0, 9)
    axes_style(ax, "x, см", "$F_{упр}$, Н")
    return png(path)

# ---------- коды тем ФРП (как их строит aggregate_frp) ----------
plan = json.load(open(os.path.join(HERE, "thematic_plan.json")))["plan"]

def find_topic(subj, grade, needle):
    for si, sec in enumerate(plan[subj][str(grade)]["sections"]):
        for t in sec["topics"]:
            if needle in t["title"].lower():
                num = str(t["num"]).replace(".", "-").strip("-")
                return f"{subj}{grade}.{si + 1}" + (f".{num}" if num else "")
    raise SystemExit(f"тема не найдена: {subj} {grade} {needle}")

FUNC_TOPIC = find_topic("math", 8, "числовые функции")
VT_TOPIC = find_topic("physics", 9, "механическое движение")
HOOK_TOPIC = find_topic("physics", 7, "сила. виды сил")

# ---------- параметрические задачи ----------
tasks = []

def linear_batch(topic, n, subj="math"):
    # параметры подбираем так, чтобы узлы (0;b) и (1;k+b) гарантированно попадали
    # в видимое окно [-5;5]: иначе ученик не может считать ответ с рисунка
    combos = [(k, b) for k in range(-4, 5) if k != 0 for b in range(-4, 5) if abs(k + b) <= 4]
    picked = random.sample(combos, min(n // 2, len(combos)))
    for i in range(n):
        k, b = picked[i % len(picked)]
        ask = "k" if i % 2 == 0 else "b"
        m = grid_line(f"graphs/{subj}_lin_{topic}_{i}.png", k, b, ask)
        tasks.append({
            "subject": subj, "topic": topic, "type": "short",
            "prompt": f"На рисунке изображён график функции $y=kx+b$ (одна клетка — 1). "
                      f"Найдите значение ${ask}$.",
            "answer": str(k if ask == "k" else b),
            "solution": f"Прямая пересекает ось $y$ в точке $(0;{b})$ — значит $b={b}$. "
                        f"Наклон: на каждый шаг вправо $y$ меняется на {k} — значит $k={k}$.",
            "media": m,
        })

def vt_batch(topic, n, subj="physics"):
    for i in range(n):
        v0 = random.choice([0, 2, 4]); a = random.choice([1, 2]); T = 4
        s = v0 * T + a * T * T // 2
        m = vt_line(f"graphs/{subj}_vt_{i}.png", v0, a, T)
        tasks.append({
            "subject": subj, "topic": topic, "type": "short",
            "prompt": "Автомобиль движется прямолинейно. На рисунке приведён график "
                      "зависимости скорости автомобиля от времени. Определите путь, "
                      "пройденный автомобилем за первые 4 с движения. Ответ в метрах.",
            "answer": str(s),
            "solution": f"Путь — площадь трапеции под графиком: начальная скорость {v0} м/с, "
                        f"ускорение {a} м/с², за 4 с скорость стала {v0 + a * T} м/с: "
                        f"s = ({v0} + {v0 + a * T})/2 · 4 = {s} м.",
            "media": m,
        })

def hooke_batch(topic, n, subj="physics"):
    for i, k in enumerate(random.sample([50, 100, 150, 200, 250, 300], n)):
        m = hooke_line(f"graphs/{subj}_hooke_{i}.png", k)
        tasks.append({
            "subject": subj, "topic": topic, "type": "short",
            "prompt": "На рисунке представлен график зависимости модуля силы упругости "
                      "пружины от её удлинения. Определите жёсткость пружины. "
                      "Ответ в Н/м.",
            "answer": str(k),
            "solution": f"По закону Гука $F=kx$. По графику при удлинении 20 см (0,2 м) "
                        f"сила упругости {k * 0.2:g} Н, откуда $k={k * 0.2:g}/0,2={k}$ Н/м.",
            "media": m,
        })

linear_batch(FUNC_TOPIC, 16)
vt_batch(VT_TOPIC, 12)
hooke_batch(HOOK_TOPIC, 6)

json.dump(tasks, open(os.path.join(HERE, "graph_tasks.json"), "w"), ensure_ascii=False, indent=0)

# ---------- банковские задачи: media и честный trusted ----------
tpath = os.path.join(REPO, "app/src/main/assets/bank/tasks.json")
bank = json.load(open(tpath))
fixed = untrusted = 0
func_img = grid_line("bank/math_func_k.png", -2, 3, "k")
hooke_img = bank_hooke("bank/phys_hooke.png")
geo_img = bank_geo_map("bank/geo_ab_map.png")
for t in bank:
    p = t["prompt"]
    if "график функции $y=kx+b$" in p or "график функции $y = kx + b$" in p:
        t["media"] = func_img; fixed += 1
    elif "силы упругости $F_{упр}$ от удлинения" in p:
        t["media"] = hooke_img; fixed += 1
    elif "фрагмент топографической карты" in p:
        t["media"] = geo_img; fixed += 1
    elif "аэрофотоснимка" in p:
        t["trusted"] = False; untrusted += 1
json.dump(bank, open(tpath, "w"), ensure_ascii=False, indent=1)
print(f"граф-задач: {len(tasks)}; банку: media+{fixed}, untrusted {untrusted}")
print(f"темы: func={FUNC_TOPIC} vt={VT_TOPIC} hooke={HOOK_TOPIC}")
