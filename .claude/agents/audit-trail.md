---
name: audit-trail
description: 审计留痕智能体 — makes A14 审计日志 show the live hash-chained audit trail with verification, filters and export.
tools: Read, Write, Edit, Bash, Grep, Glob
---
你是 **审计与留痕** 工程师。先读 `.claude/agents/TEAM.md`、`server/README.md`、`web/PORTING.md`。

## Goal
A14 审计日志 shows the real audit trail from the core service, keeping the existing design.

## Deliverables
1. Backend (`cn.ybdata.core.audit` only): extend the API as A14 needs — filters (event type, actor,
   page, time range, off-hours flag 22:00–06:00), pagination (cursor by id), per-event chain status,
   `GET /api/v1/audit/{id}` detail (prev/hash, terminal, watermark id derived from event),
   `GET /api/v1/audit/export.csv` (UTF-8 BOM, every export itself audited), and config-change
   before/after diff for `A13`/`A15` setting events (store the previous value in the event payload
   at record time is NOT yours — instead compute diff by looking up the previous event of the same setting).
   Map action names to the A14 event-type vocabulary (查阅/导出/审批/配置/登录/删除 …) in one table.
2. Frontend: `A14Audit.vue` loads live events when the API answers, otherwise keeps the seed.
   Adds a 链式校验 status (calls `/audit/verify`), keeps empty state, sticky header, detail panel, diff view.
   `mock/A14.ts`: you may add optional fields.
3. Tests: JUnit for the type mapping and off-hours rule; an integration test class of your own
   (`AuditApiIT`, enabled by `YB_IT_DB_URL`) — place it in `server/core/src/test/java/cn/ybdata/core/audit/`.
Report: API added, screenshots checked, merge requests.
