#!/usr/bin/env bash
# 本地 / CI 通用的端到端回归:每个套件前重置数据库并重启业务服务,引擎与前端常驻。
# 用法:scripts/e2e.sh [套件名...]    默认全部套件
# 前置:PostgreSQL 已启动(库 dpub / 用户 dpub / 密码 dpub);server/target/chs-dpub-server.jar 已构建;engine 已 pip install -e .[dev];web 已 npm ci。
# 环境变量:CHROME(浏览器可执行文件,缺省用 Playwright 自带);LOG_DIR(日志目录,缺省 /tmp/dpub-e2e)
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG="${LOG_DIR:-/tmp/dpub-e2e}"; mkdir -p "$LOG"
export PGPASSWORD="${PGPASSWORD:-dpub}" PGDB=dpub BASE=http://localhost:5173
[ -n "${CHROME:-}" ] && export CHROME

stop_port() { for p in $(lsof -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null); do kill "$p"; done; for _ in $(seq 1 10); do lsof -t -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1 || return 0; sleep 1; done; }
ENGINE_BIN="${ENGINE_BIN:-uvicorn}"

stop_port 8091; (cd "$ROOT/engine" && nohup "$ENGINE_BIN" dpub_engine.main:app --port 8091 > "$LOG/engine.log" 2>&1 &)
stop_port 5173; (cd "$ROOT/web" && nohup npx vite --port 5173 --strictPort > "$LOG/vite.log" 2>&1 &)
sleep 4

restart_server() {
  stop_port 8081
  psql -h localhost -U dpub -d dpub -qc "drop schema public cascade; create schema public;" >/dev/null 2>&1
  (cd "$ROOT/server" && nohup java -jar target/chs-dpub-server.jar --spring.profiles.active=dev > "$LOG/server.log" 2>&1 &)
  for _ in $(seq 1 60); do curl -s localhost:8081/actuator/health | grep -q UP && return 0; sleep 1.5; done
  echo "server failed"; tail -30 "$LOG/server.log"; return 1
}

SUITES=("$@"); [ ${#SUITES[@]} -eq 0 ] && SUITES=(smoke holo indicator publish portal-analysis portal-reports regional closure exports gate)
FAIL=0
cd "$ROOT/web"
for t in "${SUITES[@]}"; do
  restart_server || exit 1
  echo "=============== $t"
  if [ "$t" = regional ]; then REGIONAL_DB=dpub timeout 400 node "e2e/$t.mjs" > "$LOG/$t.out" 2>&1; else timeout 400 node "e2e/$t.mjs" > "$LOG/$t.out" 2>&1; fi
  rc=$?; tail -4 "$LOG/$t.out"; echo "exit=$rc"
  [ $rc -ne 0 ] && FAIL=1
done
stop_port 5173; stop_port 8081; stop_port 8091
exit $FAIL
