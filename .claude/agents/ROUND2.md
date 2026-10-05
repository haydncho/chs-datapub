# 第二轮协作约定(6 项改动)

工作目录 `/home/claude/repo`(源码;完成后由协调者同步到 `chs-datapub` 并提交)。**所有回复、过程说明、代码注释里的界面文案一律用中文。**
先读 `.claude/agents/TEAM.md` 的第 3–9 条(数据库、构建目录、端口、清理、验证规则;其中的"归属表"以本文件为准)、`web/PORTING.md`、`server/README.md`。

## 目标(用户原话)
1. 首页改为登录页
2. 医保局端和机构端通过登录页选择进入不同的页面
3. 用户与权限按实际用户调整
4. 外观配置整体优化一下
5. 机构端的功能也按医保局端的分类整理一下
6. 不同层级的菜单要有清晰的层次关系,菜单可以增加小图标

## 共享约定(必须遵守)
- **端(side)**:`'bureau'`(医保局端)与 `'org'`(机构端)。会话里的每个身份都带 `side`。
  医保局端:召集人、行政管理组、委托分析团队、安全审计员;机构端:医保办主任(医院)、县区医保部门、外部监督(社会监督员)。
- **会话数据(`web/src/app/session.ts`)**:`session.current.identity.side` 是当前端;`session.current.pages` 是该身份可访问的页面编码。
  登录智能体负责把 `side` 加进会话和后端 `/auth/me`、`/auth/login`、`/auth/identity` 返回的每个身份对象。
- **导航数据结构(`web/src/app/nav.ts`,壳层智能体负责)**:
  ```ts
  export interface NavItem { code: PageCode; name: string; icon: string; query?: Record<string,string> }
  export interface NavGroup { id: string; n?: string; name: string; icon: string; items: NavItem[] }
  export function navFor(side: 'bureau'|'org', allowed: readonly string[] | null): NavGroup[]  // 只保留允许访问的项,空分组去掉
  export function breadcrumbOf(side, code, query?): { side: string; group: string; item: string }
  ```
  `icon` 是图标名(来自 `@lucide/vue`,在 `web/src/lib/icons.ts` 里集中映射),所有菜单层级都带小图标。
- **页面编码不变**(`cockpit`、`A1`、`A3`…`A15`、`B1`…`B7`、`C3`、`D1`),后端权限矩阵 `AccessPolicy` 的页面集合不变。
- 默认入口:未登录访问任何地址 → `#/A1`;登录后进入所选身份的落地页。

## 文件归属(只改自己的文件;需要别人文件里的改动 → 写进最终报告,由协调者合并)
| 智能体 | 可改文件 | 端口 / 库 |
|---|---|---|
| **shell**(壳层与菜单,需求 5、6) | `web/src/app/nav.ts`、`web/src/app/pages.ts`、`web/src/components/yb/AppShell.vue`、`web/src/components/yb/nav/**`(新建)、`web/src/lib/icons.ts`(新建)、`web/src/styles/nav.css`(新建)、`web/PORTING.md`(仅新增菜单一节) | vite 5311,无后端(种子模式) |
| **login**(登录与会话,需求 1、2) | `web/src/app/router.ts`、`web/src/app/session.ts`、`web/src/api/auth.ts`、`web/src/api/client.ts`、`web/src/pages/A1Login.vue`、`web/src/pages/A1/**`、`web/src/mock/A1.ts`、`server/core/src/main/java/cn/ybdata/core/{auth,security}/**`、迁移 `V7__*.sql`、`server/core/src/test/**` 中 auth/security 相关测试 | core 8091,vite 5312,库 `ybdata_login` |
| **users**(用户与权限,需求 3) | `web/src/pages/A12Permissions.vue`、`web/src/pages/A12/**`、`web/src/mock/A12.ts`、`server/core/src/main/java/cn/ybdata/core/domain/UserDomain.java`(新建)、迁移 `V8__*.sql`、新建的相关测试 | core 8092,vite 5313,库 `ybdata_users` |
| **appearance**(外观配置,需求 4) | `web/src/pages/A15Appearance.vue`、`web/src/pages/A15/**`、`web/src/mock/A15.ts`、`web/src/app/appearance.ts`、`web/src/style.css`(只允许新增外观相关的变量/规则) | vite 5314,无后端 |

协调者(我)之后负责:`web/e2e/**`、文档、把外观同步接入壳层、同步到 `chs-datapub`、提交推送。
不要提交或推送;不要 `npm install` 新依赖(`@lucide/vue` 已有);不要改 V1–V6 迁移。

## 验收
- `cd web && npx vue-tsc -b` 无错误;截图检查改动过的页面(1440×900 与 1280×800),无横向滚动、无折行、无错位;
- Java:`mvn -q -B test -Dyb.build.dir=$SCRATCH/<name>/target`(含你新增的测试)通过;
- 最终报告用中文:做了什么、截图检查了什么、测试数量、需要协调者合并的改动。
