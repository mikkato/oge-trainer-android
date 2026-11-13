#!/usr/bin/env python3
"""Дерево тем «Обществознание» 5–9 класс: содержательные линии ФГОС ООО
(§45.6.2, ред. 18.06.2025) -> LLM-разметка по классам в формате ФРП.
ФРП по предмету в реестре fgosreestr нет только для 5–9 классов ООО,
сам стандарт — официальный документ. Выход: soc_tree.json
{grade: {"frp": str, "hours": int, "sections": [{title, topics:[{num,title,hours,kind}]}]}}
"""
import json, os, re, sys

BASE = "https://cloud.agk-russia.ru:4443/v1/chat/completions"
KEY = os.environ["HERMES_CUSTOM_BIGMODEL_API_KEY"]
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "soc_tree.json")

SEC = open(os.path.join(HERE, "soc_section.txt")).read()
GRADE_NOTES = {
    "5": "5 класс: человек и природа, человек и общество, базовые социальные роли, школа, семья, традиции; первые представления об экономике и праве в бытовом масштабе.",
    "6": "6 класс: человек как личность, деятельность, познание, эмоции и воля, общение, малая группа, правила и нормы, начальные понятия экономики (потребности, обмен, деньги).",
    "7": "7 класс: социальная структура, социальные нормы и ответственность, мораль, правонарушения и виды ответственности, семья как социальный институт, основы предпринимательской деятельности.",
    "8": "8 класс: экономика в масштабе общества (рынок, деньги, инфляция, налоги, бюджет), социальная сфера,духовная культура, наука и образование, религия.",
    "9": "9 класс: политика и власть, государство, формы правления, избирательное право, Конституция РФ, федеративное устройство, права и обязанности гражданина РФ, правоохранительные органы, глобальные проблемы.",
}

def call(model, messages, max_tokens=6000, temp=0.3):
    body = json.dumps({
        "model": model,
        "messages": messages,
        "temperature": temp,
        "max_tokens": max_tokens,
        "chat_template_kwargs": {"enable_thinking": False},
    }).encode()
    import urllib.request
    req = urllib.request.Request(BASE, data=body, headers={
        "Authorization": f"Bearer {KEY}", "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=300) as r:
        d = json.loads(r.read())
    c = d["choices"][0]["message"]["content"] or ""
    c = re.sub(r"^```(?:json)?|```$", "", c.strip(), flags=re.M).strip()
    return c

def one(grade):
    user = (
        "Составь тематическое планирование по обществознанию для " + grade + " класса "
        "(34 часа, 1 час в неделю) на основе приведённых предметных результатов ФГОС ООО.\n"
        "Специфика класса: " + GRADE_NOTES[grade] + "\n\n"
        "ТРЕБОВАНИЯ:\n"
        "- 4–6 разделов, всего 20–26 тем;\n"
        "- hours каждой темы — целое число, сумма всех hours = 34;\n"
        "- темы формулируй как в рабочих программах (именительный падеж, кратко);\n"
        "- не включай повторение/экскурсии/практикумы отдельными темами;\n"
        "- опирайся строго на содержание ФГОС-линий ниже, без выдуманных сюжетов.\n\n"
        "Ответь ТОЛЬКО JSON: {\"sections\":[{\"title\":\"…\",\"topics\":[{\"title\":\"…\",\"hours\":2}]}]}\n\n"
        "ТЕКСТ ФГОС ООО:\n" + SEC
    )
    raw = call("qwen3.8-flash-next", [
        {"role": "system", "content": "Ты методист-составитель рабочих программ по обществознанию. Отвечаешь только валидным JSON."},
        {"role": "user", "content": user}], max_tokens=4000)
    data = json.loads(raw)
    secs = []
    num = 1
    for s in data["sections"]:
        topics = []
        for t in s["topics"]:
            topics.append({"num": str(num), "title": t["title"].strip(),
                           "hours": int(t.get("hours", 1)), "kind": "lesson"})
            num += 1
        secs.append({"title": s["title"].strip(), "topics": topics})
    total = sum(t["hours"] for s in secs for t in s["topics"])
    return {"frp": "Синтез по ФГОС ООО §45.6.2 (Приказ №287, ред. 18.06.2025) — ФРП в реестре отсутствует",
            "hours": total, "sections": secs}

def main():
    out = json.load(open(OUT)) if os.path.exists(OUT) else {}
    for g in ("5", "6", "7", "8", "9"):
        if g in out:
            print(g, "уже есть, пропуск"); continue
        try:
            out[g] = one(g)
            json.dump(out, open(OUT, "w"), ensure_ascii=False, indent=1)
            n = sum(len(s["topics"]) for s in out[g]["sections"])
            print(g, "готово:", n, "тем,", out[g]["hours"], "ч.")
        except Exception as e:
            print(g, "ОШИБКА:", type(e).__name__, str(e)[:120])

if __name__ == "__main__":
    main()
