# 多智能体协作规则 (team rules)

Five agents work on this repo **at the same time**. Each owns a disjoint set of
files. Read this file and your own role file before starting.

| Agent | Role file | Owns (may create/modify) | Dev ports | Database |
|---|---|---|---|---|
| auth | `auth-security.md` | `server/core/src/main/java/cn/ybdata/core/{auth,security}/**`, `server/core/src/main/java/cn/ybdata/core/action/**`, `server/core/src/main/java/cn/ybdata/core/page/**`, `server/core/pom.xml`, `server/core/src/main/resources/application.yml`, migration **V4__*.sql**, `web/src/api/**`, `web/src/app/session.ts`, `web/src/pages/A1Login.vue`, `web/src/pages/A1/**`, `web/src/mock/A1.ts`, `web/src/components/yb/AppShell.vue` (identity area only) | core 8081, vite 5301 | `ybdata_auth` |
| audit | `audit-trail.md` | `server/core/src/main/java/cn/ybdata/core/audit/**`, `web/src/pages/A14Audit.vue`, `web/src/pages/A14/**`, `web/src/mock/A14.ts` | core 8082, vite 5302 | `ybdata_audit` |
| analytics | `analytics.md` | `server/analytics/**`, `server/core/src/main/java/cn/ybdata/core/analytics/**`, migration **V5__*.sql**, `web/src/pages/A6Recommend.vue`, `web/src/pages/A6/**`, `web/src/mock/A6.ts`, `web/src/pages/A11Alerts.vue`, `web/src/mock/A11.ts` | core 8083, analytics 8093, vite 5303 | `ybdata_analytics` |
| ui | `ui-polish.md` | `web/src/components/ui/**`, `web/src/components/yb/**` (except AppShell identity area), `web/src/style.css`, `web/src/lib/**`, `web/src/app/appearance.ts`, every `web/src/pages/**` file **not** owned above | vite 5304 | — |
| qa | `qa-ci.md` | `web/e2e/**`, `web/playwright.config.ts`, `web/package.json`, `web/package-lock.json`, `.github/**`, `server/core/src/test/**` (new test files only) | core 8085, vite 5305 | `ybdata_qa` |

## Hard rules

1. **Never edit a file you don't own.** If you need a change in someone else's
   file, write the request in your final report (the coordinator merges it).
2. **Dependencies:** only `qa` edits `web/package.json` / lockfile (no `npm install <pkg>` by others);
   only `auth` edits `server/core/pom.xml`; only `analytics` edits `server/analytics/requirements*.txt`.
3. **Flyway:** auth uses `V4__…`, analytics uses `V5__…`. Never edit V1–V3.
4. **Contracts are shared:** page payload shapes are `web/src/mock/*.ts`; you may *add* optional
   fields to mocks you own, never rename/remove fields others rely on.
5. **Local runtime:** PostgreSQL 16 on `localhost:5432`, superuser `postgres` (trust auth).
   Create your own database (`createdb -h localhost -U postgres -O ybdata <db>`) and build/run your own
   core instance in your own build dir (parallel builds must not share `target/`):
   `cd server/core && mvn -q -B package -DskipTests -Dyb.build.dir=$SCRATCH/<agent>/target && PORT=<port> DB_URL=jdbc:postgresql://localhost:5432/<db> java -jar $SCRATCH/<agent>/target/yb-core-0.1.0.jar`
   (same `-Dyb.build.dir=…` for `mvn test`).
   Vite: `VITE_CACHE_DIR=node_modules/.vite-<agent> API_TARGET=http://localhost:<core port> npx vite --port <port> --strictPort`.
6. **Never kill processes you didn't start.** Kill by PID, never `pkill -f` patterns.
7. **Scratch files** (screenshots, scripts, logs, build dirs) go in your own folder `$SCRATCH/<agent>/`, where
   `SCRATCH=/tmp/claude-0/-home-claude-repo/f1207aa9-bf02-5b99-a678-870904376d35/scratchpad`.
   Playwright: `NODE_PATH=$(npm root -g)`, `chromium.launch({ executablePath: '/opt/pw-browsers/chromium' })`.
8. **Done means verified:** `cd web && npx vue-tsc -b` clean for your files; Java `mvn -q -B test` passes;
   Python `pytest` passes; screenshots of changed screens checked. Do not commit or push — the coordinator does.
9. Frontend conventions: `web/PORTING.md`. UI copy is Chinese; keep the existing visual language.
