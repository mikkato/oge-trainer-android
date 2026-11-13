#!/usr/bin/env python3
"""Извлечение тематического планирования из ФРП (PDF) в структурированный JSON.

Разбираем только ПЕРВОЕ тематическое планирование: порядок тем в пределах класса
в ФРП varies намеренно, второй вариант даёт те же темы. Класс переключается
заголовком «N КЛАСС», колонки ищем по строке заголовков таблицы.
"""
import json, os, re
import pdfplumber

HERE = os.path.dirname(os.path.abspath(__file__))
rows = json.load(open(os.path.join(HERE, "manifest.json")))

RE_TP_HEAD = re.compile(r"ТЕМАТИЧЕСК[ИО][ЕЯ]\s+ПЛАНИРОВАНИЕ\s*(\d+)?", re.I)
# для старта и стоп-маркеров — только ЗАГЛАВНЫЕ заголовки разделов, не упоминания в прозе
RE_TP_CAPS = re.compile(r"ТЕМАТИЧЕСК[ИО][ЕЯ]\s+ПЛАНИРОВАНИЕ\s*(\d+)?")
RE_GRADE = re.compile(r"^\s*(\d{1,2})\s*КЛАСС\b", re.M | re.I)
RE_NUM = re.compile(r"^\d{1,2}(?:\.\d{1,2})*\.?$")
RE_SECTION = re.compile(r"^(Раздел|Глава|Блок|Часть|Модуль)\b", re.I)
RE_STOP = re.compile(
    r"ПРОВЕРЯЕМЫЕ\s+ПРЕДМЕТНЫЕ|ПРИМЕРНЫЙ\s+ПОУРОЧНЫЙ|ПРИМЕРНОЕ\s+КАЛЕНДАРНО"
    r"|Система\s+оценивания|УЧЕБНО-МЕТОДИЧЕСКОЕ|МАТЕРИАЛЬНО-ТЕХНИЧЕСКОЕ"
    r"|ПЛАНИРУЕМЫЕ\s+РЕЗУЛЬТАТЫ", re.I)
RE_TOTAL = re.compile(r"ОБЩЕЕ\s+КОЛИЧЕСТВО|ИТОГО|ВСЕГО", re.I)


def clean(s):
    return re.sub(r"\s+", " ", (s or "").replace("\n", " ")).strip()


def kind_of(title):
    t = title.lower()
    if "практическая работ" in t or "практикум" in t:
        return "practical"
    if "лабораторн" in t:
        return "lab"
    if "контрольн" in t or "диагностическ" in t or "итогов" in t or "зачёт" in t:
        return "control"
    if "проект" in t:
        return "project"
    if "входн" in t:
        return "input_check"
    return "topic"


def toc_line(text, end):
    """Строка оглавления: сразу за заголовком идут длинные точки."""
    return bool(re.match(r"^\s*[.…]{4,}", text[end:end + 60]))


def header_columns(cells):
    joined = " ".join(clean(c).lower() for c in cells)
    return (("наименован" in joined or "назван" in joined)
            and ("час" in joined or "п/п" in joined))


def col_map(cells):
    idx = {"num": None, "name": None, "hours": None, "content": None}
    for i, c in enumerate(cells):
        c = clean(c).lower()
        if idx["num"] is None and ("№" in c or "п/п" in c):
            idx["num"] = i
        elif idx["name"] is None and ("наименован" in c or "раздел" in c or "тем" in c):
            idx["name"] = i
        elif idx["hours"] is None and "час" in c:
            idx["hours"] = i
        elif idx["content"] is None and any(w in c for w in ("содержан", "характеристик", "образовательн")):
            idx["content"] = i
    if idx["name"] is None:
        idx["name"] = 1 if idx["num"] == 0 else 0
    if idx["hours"] is None:
        idx["hours"] = 2
    return idx


def parse_pdf(path, default_grade=5):
    out = {}
    cur_grade = cur_section = cur_topic = None
    started = False
    cm = None  # карта колонок живёт на весь документ: шапка таблицы не повторяется
    auto_num = 0
    tp_no = 0
    occ = {}
    frozen = set()  # классы, закрытые вариантом 2 планирования
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            text = page.extract_text() or ""
            if not started and any(
                    not toc_line(text, m.end()) for m in RE_TP_CAPS.finditer(text)):
                started = True
                cur_grade = None  # счётчики классов с этого места, оглавление не в счёт
                occ = {}
            if not started:
                continue
            if any(any(s["topics"] for s in secs) for secs in out.values()):
                if RE_STOP.search(text):
                    break  # дальше результаты/оценивание/обеспечение — не планирование
                for m in RE_TP_HEAD.finditer(text):
                    if (not toc_line(text, m.end()) and m.group(1)
                            and int(m.group(1)) >= 2):
                        # заголовок «ПЛАНИРОВАНИЕ 2» — заморозить уже собранные классы
                        frozen |= {g for g, secs in out.items()
                                   if any(x["topics"] for x in secs)}
            gm = None
            for mm in RE_GRADE.finditer(text):
                gm = mm
            if gm:
                g = int(gm.group(1))
                if 1 <= g <= 11 and g != cur_grade:
                    occ[g] = occ.get(g, 0) + 1
                    if occ[g] >= 2 and any(s["topics"] for s in out.get(g, [])):
                        frozen.add(g)  # второй проход по классу = компактный план/результаты
                    cur_grade, cur_section, cur_topic = g, None, None
                    out.setdefault(g, [])
            for table in page.extract_tables():
                for cells in table:
                    if not any(clean(c) for c in cells):
                        continue
                    if header_columns(cells) and sum(len(clean(c)) for c in cells) < 240:
                        cm = col_map(cells)
                        continue
                    if cm is None:
                        continue
                    if cur_grade is None:
                        cur_grade = default_grade
                        out.setdefault(cur_grade, [])
                    def cell(key):
                        i = cm.get(key)
                        return clean(cells[i]) if i is not None and i < len(cells) else ""

                    c_num, c_name = cell("num"), cell("name")
                    c_hours, c_content = cell("hours"), cell("content")
                    joined = " ".join(clean(c) for c in cells)
                    gm = RE_GRADE.match(c_name) or RE_GRADE.match(joined)
                    if gm and (cm["num"] is None or not c_num):
                        g = int(gm.group(1))
                        if 1 <= g <= 11:
                            occ[g] = occ.get(g, 0) + 1
                            if occ[g] >= 2 and any(s["topics"] for s in out.get(g, [])):
                                frozen.add(g)
                            cur_grade = g
                            cur_section = cur_topic = None
                            out.setdefault(cur_grade, [])
                        continue
                    if cur_grade in frozen:
                        continue
                    first_meaningful = c_num or c_name
                    if RE_SECTION.match(first_meaningful) or RE_SECTION.match(c_name):
                        title = first_meaningful if RE_SECTION.match(first_meaningful) else c_name
                        cur_section = {"title": title, "topics": []}
                        out[cur_grade].append(cur_section)
                        cur_topic = None
                        # строка раздела может одновременно быть темой (математика): если есть часы — тема
                        if not re.search(r"^\d{1,3}$", c_hours or ""):
                            continue
                    hours_m = re.match(r"^(\d{1,3})\b", c_hours or "")
                    is_num_row = cm["num"] is not None and RE_NUM.match(c_num)
                    is_plain_row = cm["num"] is None and c_name and hours_m
                    # Фолбэк: pdfplumber рыхлит склеенные ячейки (строка с большим числом
                    # колонок, чем в шапке) — данные уезжают с «картовых» индексов
                    # (num на месте, name пустой — тема молча терялась).
                    # Пересобираем строку структурно: num = первая короткая нумерация,
                    # hours = первая чисто-числовая ячейка после названия,
                    # name = текст между ними, content = самая длинная ячейка после часов.
                    if (not is_num_row and not is_plain_row) or (is_num_row and not c_name):
                        cells_c = [clean(c) for c in cells]
                        num_idx = next((i for i, c in enumerate(cells_c)
                                        if c and len(c) <= 6 and RE_NUM.match(c)), None)
                        if num_idx is not None:
                            tail = [i for i in range(num_idx + 1, len(cells_c))
                                    if re.fullmatch(r"\d{1,3}", cells_c[i] or "")]
                            hours_idx = tail[0] if tail else None
                            name_parts = [cells_c[i] for i in range(num_idx + 1,
                                          hours_idx if hours_idx is not None else len(cells_c))
                                          if cells_c[i] and not re.fullmatch(r"\d{1,3}", cells_c[i])]
                            joined_name = " ".join(name_parts).strip()
                            if joined_name:
                                c_num, c_name = cells_c[num_idx], joined_name
                                c_hours = cells_c[hours_idx] if hours_idx is not None else ""
                                rest = [cells_c[i] for i in range(num_idx + 1, len(cells_c))
                                        if i != hours_idx and len(cells_c[i]) > len(joined_name)]
                                c_content = max(rest, key=len) if rest else c_content
                                hours_m = re.match(r"^(\d{1,3})\b", c_hours or "")
                                is_num_row = cm["num"] is not None and RE_NUM.match(c_num)
                                is_plain_row = cm["num"] is None and c_name and hours_m
                    if is_num_row or is_plain_row:
                        if RE_TOTAL.search(c_name):
                            cur_topic = None
                            continue
                        auto_num = auto_num + 1
                        cur_topic = {
                            "num": c_num if is_num_row else str(auto_num),
                            "title": c_name if is_plain_row else (c_name or ""),
                            "hours": int(hours_m.group(1)) if hours_m else 1,
                            "kind": kind_of(c_name),
                            "content": c_content[:500],
                        }
                        if not cur_topic["title"]:
                            continue
                        if cur_section is None:
                            cur_section = {"title": "Без раздела", "topics": []}
                            out[cur_grade].append(cur_section)
                        cur_section["topics"].append(cur_topic)
                        continue
                    if cur_topic is not None:
                        if len(c_name) > 3:
                            cur_topic["title"] += " " + c_name
                            cur_topic["kind"] = kind_of(cur_topic["title"])
                        if c_content:
                            cur_topic["content"] = (cur_topic["content"] + " " + c_content)[:700]
                        elif c_name and len(c_name) > 3:
                            cur_topic["content"] = (cur_topic["content"] + " " + c_name)[:700]
    # дедуп по (класс, норм. заголовок): вариант 2 планирования повторяет те же темы
    def norm(t):
        t = re.sub(r"[^\wа-яё]+", " ", t.lower()).strip()
        return re.sub(r"\s+", " ", t)[:120]
    deduped = {}
    for g, secs in out.items():
        seen = set()
        new_secs = []
        for s in secs:
            keep = []
            for t in s["topics"]:
                key = norm(t["title"])
                if key and key not in seen:
                    seen.add(key)
                    keep.append(t)
            if keep:
                s["topics"] = keep
                new_secs.append(s)
        if new_secs:
            deduped[g] = new_secs
    return deduped


def _job(r):
    try:
        return r["id"], parse_pdf(os.path.join(HERE, "pdf", f"{r['id']}.pdf"), r["g1"]), None
    except Exception as e:
        return r["id"], None, f"EXC {type(e).__name__}: {e}"[:90]


def main():
    from concurrent.futures import ProcessPoolExecutor
    parsed = {}
    with ProcessPoolExecutor(max_workers=os.cpu_count() or 4) as ex:
        for rid, tp, err in ex.map(_job, rows):
            parsed[rid] = tp
            if err:
                parsed.setdefault("_err", {})[rid] = err
    errors = parsed.pop("_err", {})
    result, problems = [], []
    for r in rows:
        if r["id"] in errors:
            problems.append((r["id"], r["subject"], errors[r["id"]]))
            continue
        tp = parsed[r["id"]]
        per_grade = {}
        for g, secs in sorted(tp.items()):
            secs = [s for s in secs if s["topics"]]
            topics = [t for s in secs for t in s["topics"]]
            if not topics:
                continue
            per_grade[str(g)] = {
                "sections": secs,
                "topics_total": len(topics),
                "hours_total": sum(t["hours"] for t in topics),
            }
        if not per_grade:
            problems.append((r["id"], r["subject"], "пусто"))
            continue
        found = set(map(int, per_grade))
        if r["g1"] not in found:
            problems.append((r["id"], r["subject"],
                             f"классов {sorted(found)} вместо {r['g1']}-{r['g2']}"))
        result.append({**{k: r[k] for k in ("id", "subject", "level", "g1", "g2", "name")},
                       "grades": per_grade})
    out = os.path.join(HERE, "fgos_thematic.json")
    json.dump(result, open(out, "w"), ensure_ascii=False, indent=1)
    n_top = sum(p["grades"][g]["topics_total"] for p in result for g in p["grades"])
    n_sec = sum(len(p["grades"][g]["sections"]) for p in result for g in p["grades"])
    print(f"программ с планированием: {len(result)}/{len(rows)}")
    print(f"классов покрытия: {sum(len(p['grades']) for p in result)}, разделов: {n_sec}, тем: {n_top}")
    print(f"файл: {out}")
    if problems:
        print("проблемы:")
        for p in problems:
            print("  ", p)


if __name__ == "__main__":
    main()
