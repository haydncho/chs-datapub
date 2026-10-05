# server — backend services

```
browser ──► web (nginx) ──/api──► core  (Java 21 · Spring Boot 3)  ──► PostgreSQL 16
                                    │  /api/v1/analytics/** proxy        ▲
                                    └──────────────► analytics (Python · FastAPI) ─┘
```

| Service | Responsibility |
|---|---|
| `core/` (Java) | the only API the browser talks to: page read models, user actions, domain state (报告签收, 发布审批, 意见答复, 预警回执, 设置), hash-chained audit trail, Flyway schema migrations, proxy to analytics |
| `analytics/` (Python) | DRG analytics on the shared database: 病组全景 (quadrants, medians, totals), 选题推荐 scoring (影响金额 / 偏离度 / 可干预性 / 数据就绪) |

## API (core, `/api/v1`)

| Method & path | Purpose |
|---|---|
| `GET /pages` · `GET /pages/{code}` | screen read model — the JSON shape of `web/src/mock/{code}.ts`, overlaid with live domain state (B4 report status, A10 items incl. ones raised from B5, A11 alert status, A8 task steps) |
| `POST /actions/{code}/{action}` | record a user action (actor = session user; `X-YB-User` accepted only while `yb.auth.dev-header=true`). Always audited; registered actions also change domain state: `B4/D1 signReport`, `A10 assignFeedback · replyFeedback` (correction → 更正 task), `B5 submitVerification` (差异 → A10 纠错 items), `A11 sendReminder`, `D1 submitReceipt`, `A8 approvePublish · rejectPublish · resetDemo` (approval gate at step 5), `A13 setPolicyRule`, `A15 publishAppearance · resetAppearance`, `A12 setUserEnabled · requestAddUser`(仅召集人)`· reviewAddUser`(召集人 / 行政管理组,申请人不能复核自己) |
| `POST /auth/sms-code` · `POST /auth/login` · `GET /auth/me` · `POST /auth/identity` · `POST /auth/logout` | unified login (数字证书 PIN / 短信验证码, demo code `123456`), HMAC-signed 8h bearer token, lockout after 5 failures / 15 min. 登录请求可带 `side`(`bureau` 医保局端 / `org` 机构端):返回的身份列表只含所选端的身份,切换身份不能跨端(403),换端需重新登录 |
| `GET /audit?type=&actor=&page=&action=&from=&to=&offHours=&cursor=&limit=` | audit events `{items, nextCursor, today}` with A14 event type, off-hours/risk flags, per-event chain status, config diffs (A13/A15) |
| `GET /audit/{id}` · `GET /audit/export.csv` · `GET /audit/verify` | event detail; CSV export (UTF-8 BOM, formula-safe, the export itself is audited); full SHA-256 chain re-hash |
| `GET /settings/{appearance\|display_policy}` | saved settings |
| `GET /analytics/drg/panorama` · `GET /analytics/topics/recommend?top=` · `GET /analytics/alerts/evaluate?period=YYYY-MM` | proxied to the analytics service (3s timeout, last-good response cached; `X-YB-Analytics: live\|stale\|down\|error`). A6 and A11 render these live, falling back to their seeds |

Page seeds live in `core/src/main/resources/seed/pages/*.json`, generated from the
frontend with `cd web && npm run export-seed`, and are imported on start-up
(`SEED_OVERWRITE=true` to re-import over existing rows).

## Run locally

```bash
# PostgreSQL 16 with db/user/password ybdata
cd server/core && mvn spring-boot:run                     # :8080 (Flyway migrates on start)
cd server/analytics && python -m venv .venv && .venv/bin/pip install -r requirements-dev.txt
.venv/bin/uvicorn app.main:app --port 8090
```

Or everything at once from the repo root: `docker compose up --build` → http://localhost:8000

## Tests

```bash
cd server/core && mvn test                                                   # unit tests
YB_IT_DB_URL=jdbc:postgresql://localhost:5432/ybdata_test mvn verify -Dtest='*Test,*IT' -Dsurefire.failIfNoSpecifiedTests=false
cd server/analytics && .venv/bin/python -m pytest
```

## 用户与权限(A12)

A12 展示系统里的真实数据:用户来自 `app_user`(含身份授权与最近登录),角色可访问页面直接取自 `AccessPolicy.pagesFor`,
再推导出"角色 × 页面分组"访问矩阵。停用账号会立即作废其会话且不能再登录;不能停用自己或最后一名召集人;
新增用户先进入待复核,复核通过才建账号。

## Access control

Every `/api/v1/**` call passes `security/AuthFilter`: bearer token → actor and role; page reads and
actions are checked against the role × page matrix (403 JSON otherwise); auditor is read-only;
A8 approve/reject is convener-only. For the hospital role, `cockpit` and `B3` payloads are filtered so
other institutions' named data never leaves the server. The frontend mirrors the matrix (route guard,
nav hides unreachable stages). `yb.auth.dev-header=true` (default, for demos and tests) still lets
unauthenticated calls through with a per-page demo identity — **set `YB_AUTH_DEV_HEADER=false` and a
32+ byte `YB_AUTH_SECRET` in production.**

## Not done yet

- Real CA / SMS gateway integration (the 数字证书 and 短信 flows use demo PIN/code `123456`).
- Read models are demo seeds plus overlays; replace page by page with queries over real 结算 / 病案 data.
