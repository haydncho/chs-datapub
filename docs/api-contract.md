# 接口契约（第二批）

- 业务服务前缀 `/api/v1`，JSON 字段 camelCase；错误统一 `{code, message}`（`VALIDATION` 400 / `UNAUTHORIZED` 401 / `FORBIDDEN` 403 / `NOT_FOUND` 404 / `CONFLICT` 409 / `LOCKED` 423 / `ENGINE_UNAVAILABLE` 503）。
- 认证：`Authorization: Bearer <会话令牌>`；令牌含角色、机构、数据范围，退出登录后即时失效（`token_version`）。
- 鉴权：页面 → 角色见 `server/src/main/java/gov/ybj/chsdpub/common/Roles.java`；越权一律 403 并写审计日志「越权尝试」。

## 认证（A1，免登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/auth/config` | `{cert: {holder, org, issuer, expires} \| null, demo, demoSmsCode?, demoAccounts?}` |
| POST | `/auth/sms-code` | `{username}` → `{sentTo, resendSeconds}` |
| POST | `/auth/login` | `{method: CA\|PASSWORD, pin?, username?, password?, smsCode?}` → `{ticket, name, identities[]}`；连续 5 次失败锁定 15 分钟 |
| POST | `/auth/identity` | `{ticket, identityId}` → `{token, user}`；写审计「登录」 |
| GET | `/auth/me` | `{username, name, role, roleLabel, org, scope, pages[]}` |
| POST | `/auth/logout` | 204 |

## 业务接口

| 页面 | 方法 | 路径 | 说明 |
|---|---|---|---|
| A3 | GET | `/collection?period=2026-08` | 流水线、数据源、质量报告（及时性与关注项来自引擎）、血缘指标 |
| | GET | `/collection/sources/{id}` | 依赖指标（未到达时 `suspended=true`） |
| | GET | `/collection/lineage?indicator=` | 指标卡版本 → 取数批次 → 主题库表 → 源表 → 数据源 |
| | POST | `/collection/sources/{id}/arrival` | 到数登记；已到数 409 |
| | POST | `/collection/quality-check?period=` | 引擎校验；有未到达数据源 409 |
| A5 | GET | `/chart-templates`、`/report-blocks`、`/report-presets` | |
| | POST | `/report-drafts` | `{preset, blockIds[], period}` → `{id, flowStep: 3, message}` |
| A7 | GET | `/topics/{code}` | 含引擎计算的 `behaviors`、`waterfall` |
| | POST | `/topics/{code}/sections/{idx}/approve`、`/regenerate` | 专家组身份 403；已提交 409 |
| | POST | `/topics/{code}/submit` | 七段未全部审定 409 |
| A9 | GET | `/flow-templates`、`/flow-templates/{id}` | |
| | PUT | `/flow-templates/{id}/nodes/{idx}` | `{mode?, days?}`；召集人审批只能单人；机构节点不设处理方式 |
| | DELETE | `/flow-templates/{id}/nodes/{idx}` | 必经节点 409 |
| | POST | `/flow-templates/{id}/versions` | 生成待召集人确认的新版本号 |
| A10 | GET | `/opinions?tab=全部\|核对期异议\|待答复\|已答复` | `{counts, rows}`；剩余时限按当前日期计算 |
| | POST | `/opinions/{no}/reply`、`/transfer`；PUT `/opinions/{no}/typical` | 答复意见必填 |
| A11 | GET | `/alerts/rules`、`/alerts/triggers` | 触发记录含提醒函与五步跟踪 |
| | PUT | `/alerts/rules/{id}` | `{enabled}` |
| | POST | `/alerts/triggers/{id}/send`、`/receipt` | 状态机 GEN → SENT → RCPT |
| A12 | GET | `/admin/orgs`、`/admin/roles`、`/admin/accounts` | 仅安全管理员 |
| | POST | `/admin/operations` | `{target, action}` → 待第二名管理员复核；审计「授权」 |
| A13 | GET | `/display-policy/quadrants`、`/benchmark-tiers` | |
| | POST | `/benchmark-tiers/{indicator}/change-requests` | `{toTier, reason}`；同一指标已有待审批单 409 |
| A14 | GET | `/audit/logs?type=&page=&size=` | `{rows, total, todayCount, todayOverreach}`；仅审计员 |
| | GET | `/audit/trace?wm=` | 未找到 404 |
| 导出 | GET | `/exports/options?scope=A7\|A14` | 导出内容（只读）与可选项 |
| | POST | `/exports` | `{scope, purpose, validity, times}` → `{watermarkNo}`；审计「导出」 |

## 分析引擎（Python，内网 `/v1`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/v1/health` | |
| POST | `/v1/quality/check` | `{sources[{name,status,qualityScore}], orgs[{org,comorbidityPct,listQcPct}]}` → `{passed, timelinessPct, flags[], blocking[], message}` |
| POST | `/v1/topic/behaviors` | 费用倍率 = 有该行为次均 / 无该行为次均，≥1.5 为 `high` |
| POST | `/v1/topic/waterfall` | 全市均值 → 患者差异 → 行为差异 → 偏离组均值，含占比与坐标轴上限 |
| POST | `/v1/topic/draft` | `{section 1–7, facts}` → `{text, provider: "rule"}`（接入大模型时替换，输出仍须人工审定） |
