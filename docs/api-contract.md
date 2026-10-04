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

---

# 第一批 / 第三批接口

页面 → 角色见 `Roles.java`；机构门户 `/portal/**` 仅机构身份，机构一律取会话身份的 org，只返回本院数据；县区 `/county/**` 只给本县区机构明细；省级 `/province/**` 无机构级字段。

## A2 全息图 / B1 本院全息图（holo）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/holo/overview?period=月\|季\|年` | 气泡全景、公开状态矩阵与状态卡、快捷专区 |
| GET | `/holo/groups/{code}?period=` | 选中病组详情 |
| GET | `/holo/offsite` | 区域外流向与汇总；`ranking`（就医地机构排名）仅医保局身份下发 |
| GET | `/portal/holo` | 本院病组 + 同级同组灰色背景（仅编码、病例数、差额、半径）；病例 < 30 并入“其他” |

引擎 `/v1/holo/`：`panorama`、`group-detail`、`flows`、`publication`、`hospital`。

## A4 指标配置 / A6 智能推荐（indicator）

| 方法 | 路径 |
|---|---|
| GET | `/indicators?grp=&domain=&tag=&q=&sort=&dir=&page=&size=`、`/indicators/{id}`、`/indicators/meta` |
| POST / DELETE | `/indicators/{id}/package`（仅内部指标拒绝） |
| POST | `/indicators/formula/validate` |
| POST | `/indicators/drafts`；GET / PUT `/indicators/drafts/{id}`；POST `/indicators/drafts/{id}/reset`、`/preview`（`{org}`）、`/submit`（生成审批单号） |
| GET | `/recommend/topics`；POST `/recommend/topics/{code}/decision`（`{action: adopt\|reject}`）、`/undo`；PUT `/recommend/topics/{code}` |
| GET | `/recommend/attribution`、`/recommend/benchmark?threshold=60..90`、`/recommend/anomalies`、`/recommend/presentations`、`/recommend/methods` |
| POST | `/recommend/anomalies/{id}/letter` |

引擎 `/v1/indicator/`：`formula/validate`、`preview`、`topic-scores`、`attribution`、`benchmark`。

## A8 发布工作流（publish）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/publish/todos` | 六类待办 + 对标档位切换；A5 报告草稿自动登记为“月告知”第 3 步 |
| GET | `/publish/flows/{id}` | 流程详情（十步、发布包、定向范围与覆盖数） |
| PUT | `/publish/flows/{id}/scope` | 保存定向范围，返回覆盖机构数与名单（引擎计算） |
| POST | `/publish/flows/{id}/submit` | 提交至下一环节（第 5 步前） |
| POST | `/publish/flows/{id}/approve`、`/reject` | 仅召集人；驳回意见必填、退回第 3 步；审计「审批」 |
| POST | `/publish/flows/{id}/corrections` | 更正 / 撤回，说明必填 |
| GET | `/publish/audience-versions` | 分受众版本 |
| GET | `/publish/tier-requests/{id}`；POST `/approve`、`/reject` | A13 档位切换单：仅召集人；批准更新 `benchmark_tier` |

引擎 `/v1/publish/coverage`。

## B2 / B3 / B7（portal-analysis）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/portal/groups` | 重点病组、小样本病组（仅编码名称）、“其他”合计、相关专题 |
| GET | `/portal/groups/{code}` | 病组下钻；小样本病组 404 |
| GET | `/portal/benchmark`、`/portal/benchmark/{indicator}` | 档位读 `benchmark_tier`；按档位只返回 `percentile` / `anonymous` / `named` 之一，非具名档不含他院名称 |
| GET | `/portal/offsite` | 区域外汇总；无就医地机构明细 |

引擎 `/v1/portal-analysis/`：`benchmark`、`small-sample`、`group`。

## B4 / B5 / B6 / D1（portal-reports）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/portal/reports?kind=`、`/portal/reports/{id}` | 本院报告；预览含水印文字与编号 |
| POST | `/portal/reports/{id}/sign` | 签收；审计「查阅」 |
| GET | `/portal/check`；PUT `/portal/check/items/{idx}` | 核对期（截止后 409，未处理视为确认） |
| GET / POST | `/portal/opinions` | 提交意见：关联对象必选、说明必填；写入 `opinion_ticket`（A10 可见） |
| PUT | `/portal/opinions/{no}/rating` | 对已答复工单评价 |
| GET | `/portal/policy/docs?category=&q=`；POST `/portal/policy/docs/{id}/view` | |
| GET | `/portal/policy/courses`、`/portal/policy/quiz`；POST `/portal/policy/quiz/{id}/answer` | 答案只在服务端，引擎判分 |
| GET | `/portal/mobile/summary`、`/portal/mobile/alerts/{id}` | D1 移动端（`/m`） |

引擎 `/v1/portal-reports/quiz/grade`。

## C1 / C2 / C3（regional）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/county/overview` | 本县区机构具名 + 其他县区汇总与排名 + 医共体 14 项监测 |
| GET | `/province/summary?sort=&dir=` | 各统筹区汇总（排序列白名单）；无机构级字段 |
| GET | `/supervision/seat`、`/supervision/materials/{id}` | 只读席位；过期后 403 `SEAT_EXPIRED` 并审计 |
| POST | `/supervision/recording/progress` | `{watchedS}` |

引擎 `/v1/regional/`：`county`、`province`。
