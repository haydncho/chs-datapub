# 并行开发约定（第一批 / 第三批）

每个开发分组在独立 git worktree / 分支上工作，**只改自己名下的文件**；共享登记处（角色、菜单、路由、接口前缀、迁移号）已预先分配好，需要改共享文件时在交付说明里提出，由合并人统一处理。

## 分组与文件归属

| 分组 | 页面 | 后端包 `server/.../chsdpub/` | 接口前缀 `/api/v1` | 迁移 | 引擎路由 | 前端 |
|---|---|---|---|---|---|---|
| holo | A2 全息图、B1 本院全息图 | `holo/` | `/holo/**`（A2）、`/portal/holo/**`（B1） | `V10__holo.sql`（续用 `V10_1__…`） | `routers/holo.py` → `/v1/holo` | `views/holo/`、`components/holo/`、`api/holo.ts` |
| indicator | A4 指标配置、A6 智能推荐 | `indicator/`、`recommend/` | `/indicators/**`、`/recommend/**` | `V11__indicator.sql` | `routers/indicator.py` | `views/indicator/`、`components/indicator/`、`api/indicator.ts` |
| publish | A8 发布工作流（含 A13 档位切换单、A5 报告草稿的审批承接） | `publish/` | `/publish/**` | `V12__publish.sql` | `routers/publish.py` | `views/publish/`、`components/publish/`、`api/publish.ts` |
| portal-analysis | B2 病组下钻、B3 对标与PK、B7 区域外数据 | `portal/analysis/` | `/portal/groups/**`、`/portal/benchmark/**`、`/portal/offsite/**` | `V13__portal_analysis.sql` | `routers/portal_analysis.py` | `views/portal/B2* B3* B7*`、`components/portal-analysis/`、`api/portalAnalysis.ts` |
| portal-reports | B4 报告中心、B5 意见与核对、B6 政策培训、D1 移动端 | `portal/reports/` | `/portal/reports/**`、`/portal/opinions/**`、`/portal/check/**`、`/portal/policy/**`、`/portal/mobile/**` | `V14__portal_reports.sql` | `routers/portal_reports.py` | `views/portal/B4* B5* B6* D1*`、`components/portal-reports/`、`api/portalReports.ts` |
| regional | C1 县区、C2 省级、C3 外部监督 | `regional/` | `/county/**`、`/province/**`、`/supervision/**` | `V15__regional.sql` | `routers/regional.py` | `views/regional/`、`components/regional/`、`api/regional.ts` |

已预置（不要改）：`common/Roles.java`（页面 → 角色）、`config/SecurityConfig.java`（接口前缀鉴权）、`export/ExportController.java`（导出范围 A2/B1/B2/C1/C2）、`V3__identities.sql`（新身份）、`web/src/lib/nav.ts`、`web/src/router/index.ts`、`engine/dpub_engine/main.py`。

各组自己的类型定义写在自己的 `api/<组>.ts` 里，不要改 `api/types.ts`、`api/index.ts`。复用第二批的 `components/shared/*`、`components/ui/*`、`lib/*`，不修改它们。

迁移只建 **自己前缀** 的表（如 `holo_*`）；可以只读第二批的表；写入他组 / 第二批的表须在交付说明里写明（例如 B5 提交意见写入 `opinion_ticket`）。

## 演示身份

| 账号 | 身份 | 页面 |
|---|---|---|
| chenzhiyuan（UKey） | 召集人 | A2 A3 A4 A5 A6 A7 A8 A9 A10 A11 A13 |
| lihua | 行政管理组 | 同上 |
| limin | 医保办主任 · 示例市第一人民医院 | B1–B7、D1（`/m`） |
| qianli | 县区医保部门 · 甲县医保局 | C1 |
| wanglei | 省医保局 · 基金监管处 | C2 |
| liudaibiao | 外部监督 · 市人大代表 | C3 |

（dev profile：任意密码、短信验证码 482916、UKey PIN 任意）

## 本地端口（每组独立，避免互相干扰）

| 分组 | 数据库 | server | engine | vite |
|---|---|---|---|---|
| holo | dpub_holo | 8101 | 8201 | 5201 |
| indicator | dpub_indicator | 8102 | 8202 | 5202 |
| publish | dpub_publish | 8103 | 8203 | 5203 |
| portal-analysis | dpub_portal_analysis | 8104 | 8204 | 5204 |
| portal-reports | dpub_portal_reports | 8105 | 8205 | 5205 |
| regional | dpub_regional | 8106 | 8206 | 5206 |

```bash
# engine
cd engine && python3 -m venv .venv && .venv/bin/pip install -q -e '.[dev]' && .venv/bin/uvicorn dpub_engine.main:app --port <engine>
# server（数据库用户 / 密码 dpub）
cd server && mvn -q -DskipTests package && java -jar target/chs-dpub-server.jar --spring.profiles.active=dev \
  --server.port=<server> --spring.datasource.url=jdbc:postgresql://localhost:5432/<db> --app.engine.url=http://localhost:<engine>
# web（node_modules 可软链到主仓库：ln -s /home/claude/repo/web/node_modules web/node_modules）
cd web && API_TARGET=http://localhost:<server> npx vite --port <vite>
```

重置数据库：`PGPASSWORD=dpub psql -h localhost -U dpub -d <db> -c "drop schema public cascade; create schema public;"` 后重启 server。
停服务用 PID（`kill <pid>`），**不要用 `pkill -f`**——它会匹配到你自己的命令行。

## 质量要求

- 视觉沿用睿衡风格（见第二批页面写法：`PageHeader`、`Panel`、`.data-table`、`Tag`、`Chip`、`SegTabs`、`KpiCard`，语义令牌，不写十六进制色），中文文案与设计稿一致。
- 图表按交接说明的编码规则用 SVG / CSS 绘制，颜色取令牌（深色主题可用）。
- 数字由引擎计算的就放引擎；无权数据不下发（后端裁剪，不是前端打码）；机构端不出现他院名称。
- 交付前：`cd engine && .venv/bin/pytest -q`、`cd server && mvn -q -DskipTests compile`、`cd web && npx vue-tsc -b`；写一个 `web/e2e/<组>.mjs` 跑通本组主要交互（参照 `e2e/smoke.mjs`，`CHROME=/opt/pw-browsers/chromium`），并截图自查。
- 在自己的分支上提交（中文提交信息），不要推送。
