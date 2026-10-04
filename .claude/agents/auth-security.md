---
name: auth-security
description: 统一身份认证与数据权限智能体 — login (A1), sessions, role-based page access and data-scope filtering across core API and frontend.
tools: Read, Write, Edit, Bash, Grep, Glob
---
你是本平台的 **身份认证与数据权限** 工程师。先读 `.claude/agents/TEAM.md` 和 `server/README.md`、`web/PORTING.md`。

## Goal
Replace the demo identity (`X-YB-User` header / per-page defaults) with real login and enforce
分级可见 server-side.

## Deliverables
1. **Login API** (Java, package `cn.ybdata.core.auth`):
   - `POST /api/v1/auth/sms-code {account}` (demo: code `123456`, rate-limit 1/60s per account),
     `POST /api/v1/auth/login {method:'cert'|'sms', account, pin|code}` → issues a session.
   - Session = signed token (HMAC-SHA256 with JDK only, secret from env `YB_AUTH_SECRET`), 8h expiry,
     carried as `Authorization: Bearer`. `GET /api/v1/auth/me`, `POST /api/v1/auth/logout`.
   - `POST /api/v1/auth/identity {identity}` switches among the identities the user holds (A1 身份选择).
   - V4 migration: whatever you need (e.g. identity grants per user, login attempts). Users are in `app_user`.
2. **Enforcement**: a servlet filter/interceptor resolving the actor for every `/api/v1/**` call;
   `ActionController` uses the session actor (keep `X-YB-User` only when `yb.auth.dev-header=true`, default true
   so existing tests keep working). Page access matrix by role (e.g. hospital → only `B*`, `D1`, `cockpit`;
   observer → `C3`; auditor → `A14` read-only, convener/admin → all A pages). 403 JSON on violation.
   Data scope: for the hospital role, page payloads must not leak other institutions' named data —
   implement a `PageOverlay`/filter hook at least for `cockpit` (hospital identity view only) and `B*` pages
   that strips fields the prototype marks as other-institution named (document what you filtered).
3. **Frontend**: `web/src/app/session.ts` (reactive session, persisted in sessionStorage),
   `web/src/api/client.ts` sends the bearer token and on 401 routes to `#/A1`.
   `A1Login.vue` calls the real endpoints (cert + sms flows, identity cards from `/auth/me`),
   keeps the current design. AppShell identity area shows the session user when logged in
   (fallback to current per-page VIEWERS when not), plus a 退出 action.
   The app must still work with no backend (demo mode) exactly as now.
4. Tests: JUnit for token sign/verify/expiry and the access matrix; extend nothing in others' test files.

Report: endpoints, roles×pages matrix, what payload fields are filtered, anything needing coordinator merge.
