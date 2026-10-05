# 第三轮协作约定:Pad 端适配

工作目录 `/home/claude/repo`(完成后由协调者同步到 `chs-datapub` 并提交)。**所有回复、过程说明、界面文案一律用中文。**
先读 `.claude/agents/TEAM.md` 第 5–9 条(构建目录、端口、清理、验证规则)、`.claude/agents/ROUND2.md` 的"共享约定"一节、`web/PORTING.md`(含"共享构件"与"菜单与导航")。

## 目标
让整个平台在平板(Pad)上可用、好用。现状:所有页面按桌面宽度设计,根容器有 `min-w-[1280px]`,多处固定列宽,顶栏菜单依赖鼠标悬停展开,按钮偏小。

## 适配范围与验收尺寸
| 场景 | 视口(宽×高) |
|---|---|
| 横屏 | 1024×768、1180×820、1366×1024 |
| 竖屏 | 768×1024、820×1180 |
| 桌面(不能退化) | 1440×900、1280×800 |

**验收标准(每个页面,在上面全部视口下):**
1. 没有整页横向滚动(`document.documentElement.scrollWidth <= innerWidth`);宽表格/宽图表只允许在**自己的卡片内部**横向滚动(容器加 `overflow-x-auto`,不要让页面被撑宽)。
2. 多栏布局在窄屏下合理折叠:使用 Tailwind 断点 `lg:`(≥1024)、`xl:`(≥1280),例如 `grid-cols-1 xl:grid-cols-[...]`;侧栏/详情栏在窄屏下改为上下排列或放到主内容下方;**桌面(≥1280)外观与现在完全一致**,不要改变桌面布局。
3. **触屏**:所有交互不能依赖 `hover`;可点击区域主要控件(按钮、标签页、列表行、开关、复选框)的点按高度 ≥ 40px(桌面保持原样,可用 `max-xl:` 前缀只在窄屏放大);相邻可点元素间距足够不误触。
4. 文字不折行、不截断关键数字(沿用已有的 `whitespace-nowrap`/省略规则);数字与单位不错位。
5. 粘性定位(`sticky`)继续使用 `top-(--sticky-top)`、`top-(--sticky-panel)`,不要写死像素。
6. 弹窗/抽屉不超出视口,可滚动,关闭按钮在触屏上够大。
7. 所有改动要截图检查(至少 1024×768 与 768×1024 两个视口,再抽查 1180×820),并确认 1440×900 与桌面没有变化。

## 共享约定
- 断点:Tailwind 默认(`md` 768、`lg` 1024、`xl` 1280)。"Pad"= 宽度 < 1280。用 `max-xl:`、`max-lg:` 这类前缀写"只在窄屏生效"的规则。
- 根容器的最小宽度 `min-w-[1280px]` 由 **shell** 智能体去掉(改为 `min-w-0`);其他智能体不要依赖它。在 shell 完成前,你们测试窄屏时可能被根容器撑出横向滚动,这是预期的——以你自己负责的页面区域为准,先看页面容器内的元素是否溢出(`element.scrollWidth > clientWidth`),shell 完成后再整页复测。
- 全息图(cockpit)是 1920×1080 的等比缩放大屏,保持缩放逻辑;竖屏(宽<高)时在画布上方显示一条提示"竖屏下大屏被缩得很小,建议横屏查看"(可关闭,当次会话记住)。

## 文件归属(只改自己的文件;别人文件里需要的改动写进最终报告)
| 智能体 | 可改文件 | 端口 |
|---|---|---|
| **shell**(壳层、菜单、登录页、共享构件) | `web/src/components/yb/**`(含 AppShell、nav/、PageSection、PageHeader、Panel、StatCard)、`web/src/styles/nav.css`、`web/src/pages/A1Login.vue`、`web/src/pages/A1/**`、`web/src/style.css`(仅新增 Pad 相关的变量/规则)、`web/src/components/ui/**`(仅在必要时,例如 Dialog/Sheet/Select 的触屏尺寸) | vite 5331 |
| **p1**(归集、配置、洞察) | `web/src/pages/A3DataHub.vue`、`A3/**`、`A4Indicators.vue`、`A4/**`、`A5Templates.vue`、`A5/**`、`A6Recommend.vue`、`A6/**`(若存在) | vite 5332 |
| **p2**(专题、发布) | `web/src/pages/A7Topic.vue`、`A7/**`、`A8Publish.vue`、`A8/**`、`A9FlowDesigner.vue`、`A9/**` | vite 5333 |
| **p3**(反馈与设置) | `web/src/pages/A10Feedback.vue`、`A10/**`、`A11Alerts.vue`、`A11/**`(若存在)、`A12Permissions.vue`、`A12/**`、`A13DisplayPolicy.vue`、`A14Audit.vue`、`A14/**`、`A15Appearance.vue`、`A15/**` | vite 5334 |
| **p4**(机构端与全息图) | `web/src/pages/B1Hospital.vue`、`B1/**`、`B2DrgDrill.vue`、`B3Benchmark.vue`、`B4Reports.vue`、`B4/**`、`B5Verify.vue`、`B6Training.vue`、`B7OffsitePatients.vue`、`C3Oversight.vue`、`D1Mobile.vue`、`D1/**`、`Cockpit.vue`、`cockpit/**` | vite 5335 |

协调者之后负责:`web/e2e/**`(加入 Pad 视口的冒烟测试)、文档、同步、提交推送。不要提交推送;不要 `npm install`;不要改后端。

## 如何在本地登录并截图(无后端演示模式)
运行 `cd /home/claude/repo/web && VITE_CACHE_DIR=node_modules/.vite-<代号> API_TARGET=http://localhost:1 npx vite --port <端口> --strictPort`(`API_TARGET` 指向不存在的地址,页面用种子数据,登录页自动用本地演示会话)。
用 Playwright(`NODE_PATH=$(npm root -g)`,`chromium.launch({ executablePath: '/opt/pw-browsers/chromium' })`,可设置 `hasTouch: true`、`isMobile: false`)登录:
```js
await page.goto(base + '/#/A1'); await page.getByTestId('side-bureau').click();   // 机构端页面用 side-org
await page.getByPlaceholder('证书 PIN 码').fill('123456');
await page.getByRole('button', { name: '登录', exact: true }).click();
await page.getByTestId('enter').click();
await page.goto(base + '/#/<页面编码>');
```
医保局端用陈志远(召集人,全部 A 页面);机构端页面(B1–B7、D1、C3)用 `side-org` 登录。草稿目录:`$SCRATCH/<代号>`,`SCRATCH=/tmp/claude-0/-home-claude-repo/f1207aa9-bf02-5b99-a678-870904376d35/scratchpad`。

## 验收与报告
`cd web && npx vue-tsc -b` 无错误;按上面标准逐视口检查并截图;最终中文报告:改了哪些文件、各页面在各视口的检查结果(哪些通过、哪些仍有遗留)、需要协调者或其他智能体合并的改动。
