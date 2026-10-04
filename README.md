# 医保数据公开定向发布平台（chs-dpub）· 第二批

面向统筹地区医保局「医保数据工作组」的 B 端平台，按月 / 季 / 年向辖区定点医疗机构定向发布医保数据。
本仓库实现 Claude Design 设计稿三批全部产品页面（`project/医保数据公开平台 第一~三批.dc.html`）：

- **医保局工作台**：A1 登录与身份、A2 全息图、A3 数据归集中心、A4 指标可视化配置、A5 图表与报告模板、A6 智能推荐中心、A7 病种专题工作台、A8 发布工作流、A9 流程设计器、A10 意见与申诉管理、A11 预警提醒、A12 用户权限管理、A13 展示策略配置、A14 审计日志
- **医疗机构门户**：B1 本院全息图、B2 病组下钻与专题、B3 对标与PK、B4 报告中心、B5 意见与机构核对、B6 政策指南与培训、B7 区域外数据、D1 移动端摘要（`/m`）
- **其他视图**：C1 县区医保视图、C2 省级与区域外汇总、C3 外部监督只读席位

原型里的「信息架构」说明页与「身份切换演示」不作为产品页面：身份切换由 A1 选择身份 + 后端按身份裁剪实现。

视觉沿用 **睿衡**（[haydncho/chs-reho](https://github.com/haydncho/chs-reho)）的设计风格：同一套设计令牌（`web/src/style.css`，含深色主题）、shadcn-vue 组件、液态玻璃顶栏 / 登录卡片、分组图标侧栏、3px 主色区块标题、圆角数据表格。设计稿的信息架构、交互与状态色语义（绿 = 结余 / 正常、橙 = 关注、红 = 逆差 / 预警、紫 = 核对期）保持不变。

| 目录 | 说明 | 技术栈 |
|---|---|---|
| [`web/`](web/) | 前端 | Vue 3 · TypeScript · Vite · Tailwind CSS v4 · shadcn-vue（reka-ui）· Pinia · Vue Router |
| [`server/`](server/) | 业务服务（唯一对外 API） | Java 21 · Spring Boot 3.5 · Spring Security + JWT · JDBC · Flyway · Maven |
| [`engine/`](engine/) | 分析引擎（内网，仅 server 调用） | Python 3.11 · FastAPI · Pydantic v2 |
| [`deploy/`](deploy/) | 容器编排 | Docker Compose · PostgreSQL 16 · Nginx |
| [`docs/`](docs/) | 接口契约 | |
| [`project/`](project/)、[`chats/`](chats/) | Claude Design 设计交付包（原型、交接说明、设计对话） | |

## 快速开始

### Docker Compose（演示模式）

```bash
cd deploy
docker compose --env-file demo.env -f docker-compose.yml -f docker-compose.demo.yml up -d --build
# 打开 http://localhost:8089
```

生产：`cp .env.example .env` 填写 `JWT_SECRET`（≥32 字符随机串）与 `DB_PASSWORD` 后 `docker compose up -d --build`；不叠加 demo 文件即关闭演示模式。

### 本地分别启动

前置：JDK 21、Maven 3.9+、Python 3.11+、Node 22+、PostgreSQL 16（库 `dpub`，用户 / 密码 `dpub`）。

```bash
# 1. 数据库
createuser -P dpub && createdb -O dpub dpub

# 2. 分析引擎 :8091
cd engine && python -m venv .venv && .venv/bin/pip install -e '.[dev]'
.venv/bin/uvicorn dpub_engine.main:app --port 8091

# 3. 业务服务 :8081（mvn spring-boot:run 默认 dev profile = 演示模式；Flyway 自动建表并灌入样例数据）
cd server && mvn spring-boot:run

# 4. 前端 :5173（/api 代理到 :8081）
cd web && npm install && npm run dev
```

### 演示账号（dev profile）

| 登录方式 | 账号 | 身份 | 可见页面 |
|---|---|---|---|
| 数字证书（UKey，PIN 任意） | 陈志远 `chenzhiyuan` | 召集人 · 分管领导 / 专家组成员（列席，二选一） | A2–A11、A13 / 仅 A7 只读 |
| 账号 + 短信验证码 `482916`（任意密码） | 李华 `lihua` | 行政管理组（可提交，不可审批） | A2–A11、A13 |
| 同上 | 周婷 `zhouting` | 意见承办人 | A10 |
| 同上 | 张悦 `zhangyue` | 委托分析团队（受控环境） | A7 |
| 同上 | 赵强 `zhaoqiang` | 安全管理员 | A12 |
| 同上 | 孙涛 `suntao` | 审计员 | A14 |
| 同上 | 李敏 `limin` | 医保办主任 · 示例市第一人民医院 | B1–B7、D1（`/m`） |
| 同上 | 钱丽 `qianli` | 县区医保部门 · 甲县医保局 | C1 |
| 同上 | 王磊 `wanglei` | 基金监管处 · 省医保局 | C2 |
| 同上 | 刘代表 `liudaibiao` | 外部监督 · 市人大代表（限时） | C3 |

## 实现要点

- **身份与菜单裁剪**：登录后选择本次身份，会话令牌携带角色、机构与数据范围；侧栏只显示该身份可见的页面（`/auth/me → pages`），接口按同一张表（`server/.../common/Roles.java`）鉴权。三员分立：安全管理员看不到业务数据，审计员只读日志。
- **安全规则**：常驻安全提示条；实名动态水印（姓名 机构 日期 时分，每分钟刷新，深色 / 打印同样保留）；导出一律走导出审批（用途、有效期、下载次数），返回 `WM-YYYYMMDD-NNNN` 水印编号并写审计日志，A14 可按编号溯源到人；越权访问接口返回 403 并记录「越权尝试」；全站无分享、复制链接、二维码入口；登录连续 5 次失败锁定 15 分钟。
- **数字只来自引擎**：A3 及时性与机构关注项、A7 关键行为费用倍率、差异归因瀑布、文稿初稿由 Python 引擎计算 / 生成；引擎不可用时 A3 计算项显示「—」，A7 返回 503 提示稍后重试，服务端不自行计算。
- **流程闭环**：A4 提交指标上线审批 → A8 召集人批准（指标上线并登记 A13 对标档位）或驳回（A4 显示驳回意见，修改后重新提交）；A8 批准发布 → 定向发布为各机构生成报告 → 机构在 B4 / 移动端签收（签收数回写 A8）→ 意见申诉 → 答复整改（意见全部答复后）→ 归档；A6 生成提醒函 → A11 待发出 → 发出 → 机构在 B5 提交回执（或经办在 A11 登记线下回执），A6 同步显示进度；A8 撤回已发布版本后，机构 B4 里的原报告显示「已撤回」。
- **串联流程**：流程 1（A3 到数登记 → 依赖指标恢复 → 质量校验 → A5 生成报告草稿进入发布工作流第 3 步）、流程 2（A7 七段逐段审定 → 提交机构核对与专家组审核）、流程 5（A11 规则触发 → 发出提醒函 → 机构回执 → 整改跟踪）。
- **对标三档**（A13）：切换档位生成待审批的变更单（同一指标同时只能有一张），审批前保持原档位。

## 与设计稿的差异（按需求取舍）

- **视觉风格**改为睿衡设计风格（按用户要求）；版式、字段、交互、状态色语义沿用设计稿。
- 原型的「设计说明」条与「原型导航」不进入产品（交接说明要求）；侧栏改为按身份裁剪的真实菜单，只含本批页面。
- 原型各页的示例人物改为真实会话身份（见演示账号）。
- 「演示:异地就医数据到达」「演示:机构回执」改为真实的「登记到数」「登记机构回执」接口（生产中分别由省平台交换回调、机构门户提交）。
- A5 生成的报告草稿自动进入 A8 作为「月告知」第 3 步；A13 档位切换单由召集人在 A8 批准 / 驳回。
- A9 非必经节点可删除（保存为新版本后生效）；A10 答复意见为必填。
- 演示数据中的时限（C3 席位有效期、B5 核对截止）改为相对部署日期，避免演示环境过期。
- 各组按真实数据计算的数字与原型示意数字有个别出入（如 B3 分位、C1 排名依据），详见各组提交说明。

## 测试

```bash
cd engine && .venv/bin/pytest -q          # 引擎单测
cd web && npm run typecheck && npm run build
cd web && npm run e2e                     # 第二批端到端冒烟：需三端已启动且为种子数据；CHROME=浏览器路径（可选）
cd web && node e2e/holo.mjs               # 各组端到端：holo / indicator / publish / portal-analysis / portal-reports / regional / closure（流程闭环）（每个都需全新种子库）
```

`web/e2e/shot.mjs` 可按身份批量截图：`node e2e/shot.mjs ca A3 A7`、`THEME=dark node e2e/shot.mjs suntao A14`。

接口契约见 [docs/api-contract.md](docs/api-contract.md)。
