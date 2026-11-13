#!/bin/sh
# Цепочка приёмки: дождаться regen_flagged.py, верифицировать новое, смержить, переагрегировать.
cd "$(dirname "$0")"
while pgrep -f "regen_flagged\.py" >/dev/null; do sleep 20; done
echo "=== перегенерация завершена, верификация ==="
venv/bin/python verify_regen.py || { echo "VERIFY_FAIL"; exit 1; }
venv/bin/python verify_regen.py --merge || { echo "MERGE_FAIL"; exit 1; }
echo "=== переагрегация ==="
venv/bin/python aggregate_frp.py && echo "CHAIN_OK"
