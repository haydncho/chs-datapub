// 端到端（indicator 分组）：A4 指标可视化配置 + A6 智能推荐中心。需三端已启动且为种子数据。
//   BASE=http://localhost:5202 CHROME=/opt/pw-browsers/chromium node e2e/indicator.mjs
// 可选：SHOTS=1 保存关键状态截图到 e2e/out/indicator-*.png；THEME=dark 深色主题。
// 脚本可重复运行：发布包、选题处理会还原；新建指标每次使用带时间戳的名称。
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE ?? 'http://localhost:5202'
const THEME = process.env.THEME ?? 'light'
const SHOTS = !!process.env.SHOTS
mkdirSync('e2e/out', { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined })
const errors = []
const step = (name) => console.log('✓', name)
const shot = async (p, name) => SHOTS && p.screenshot({ path: `e2e/out/indicator-${name}-${THEME}.png` })

async function session(who) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript((t) => localStorage.setItem('dpub.theme', t), THEME)
  const p = await ctx.newPage()
  p.on('pageerror', (e) => errors.push(`${who}: ${e.message}`))
  await p.goto(BASE + '/login')
  await p.waitForSelector('[data-testid=login-card]')
  await p.click('text=账号 + 短信验证码')
  await p.fill('#lg-user', who)
  await p.fill('#lg-pwd', 'demo')
  await p.fill('#lg-code', '482916')
  await p.click('[data-testid=login-submit]')
  const idStep = await p.waitForSelector('[data-testid=identity-step]', { timeout: 3000 }).catch(() => null)
  if (idStep) await p.click('[data-testid=enter-btn]')
  await p.waitForSelector('[data-testid=side-nav]')
  return p
}
const toast = (p, text) => p.waitForSelector(`[data-sonner-toast]:has-text("${text}")`, { timeout: 6000 })
const rows = (p) => p.$$eval('[data-testid=ind-table] tbody tr[data-ind]', (els) => els.map((e) => e.getAttribute('data-ind')))
const text = async (p, sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim()

// ================================================================ A4 指标可视化配置
const p = await session('lihua')
await p.goto(BASE + '/a4')
await p.waitForSelector('[data-testid=ind-table] tbody tr[data-ind]')
assert.match(await text(p, '[data-testid=ind-count]'), /^本页 13 条 · 共 \d+ 条/)
assert.equal((await rows(p))[0], '例均基金差额')
await shot(p, 'a4-list')
step('A4 列表:分页 13 条/页,首行例均基金差额')

// 仅内部行置灰 + 发布包禁用（服务端同样拒绝）
const internalRow = p.locator('tr[data-ind=单病例费用明细]')
assert.ok(await internalRow.evaluate((e) => e.classList.contains('is-internal')))
assert.equal(await internalRow.locator('.dim').first().evaluate((e) => getComputedStyle(e).opacity), '0.55')
assert.equal(await internalRow.locator('[data-pkg=disabled]').evaluate((e) => getComputedStyle(e).cursor), 'not-allowed')
await internalRow.locator('[data-pkg=disabled]').click({ force: true })
await toast(p, '仅内部指标不可加入发布包')
step('仅内部:整行置灰 0.55,“加入发布包”禁用')

// 本期暂缓：悬停说明依赖数据源未到达
const late = p.locator('tr[data-ind=异地就医基金支出占比] [data-status-tip]')
assert.equal((await late.textContent()).trim(), '本期暂缓')
await late.hover()
await p.waitForSelector('[data-testid=status-tip]:has-text("依赖数据源「异地就医」本期未按时到达")')
step('本期暂缓:悬停提示依赖数据源未按时到达')

// 加入发布包 → 已加入 → 移出（还原）
if (await p.locator('tr[data-ind=次均总费用] [data-pkg=remove]').count()) {
  await p.click('tr[data-ind=次均总费用] [data-pkg=remove]')
  await p.waitForSelector('tr[data-ind=次均总费用] [data-pkg=add]')
}
await p.click('tr[data-ind=次均总费用] [data-pkg=add]')
await toast(p, '「次均总费用」已加入 2026年8月 月告知发布包')
await p.waitForSelector('tr[data-ind=次均总费用] [data-pkg=remove]')
await p.click('tr[data-ind=次均总费用] [data-pkg=remove]')
await toast(p, '已移出')
step('加入发布包 / 移出(持久化)')

// 筛选：分组芯片、主题域、标签、搜索、排序、分页
await p.click('[data-testid=grp-chips] button:has-text("错")')
await p.waitForFunction(() => document.querySelector('[data-testid=ind-count]')?.textContent?.includes('共 14 条'))
await p.click('[data-testid=tag-filter]')
await p.click('[role=menuitemradio]:has-text("仅内部")')
await p.waitForFunction(() => document.querySelector('[data-testid=ind-count]')?.textContent?.includes('共 5 条'))
assert.ok((await rows(p)).includes('参保人就医轨迹'))
await p.click('[data-testid=tag-filter]')
await p.click('[role=menuitemradio]:has-text("全部标签")')
await p.click('[data-testid=grp-chips] button:has-text("全部")')
await p.click('[data-testid=domain-filter]')
await p.click('[role=menuitemradio]:has-text("异地就医")')
await p.waitForFunction(() => document.querySelector('[data-testid=ind-count]')?.textContent?.includes('共 3 条'))
await p.click('[data-testid=domain-filter]')
await p.click('[role=menuitemradio]:has-text("全部主题域")')
await p.fill('[data-testid=ind-search]', 'IND-X0')
await p.waitForFunction(() => document.querySelector('[data-testid=ind-count]')?.textContent?.includes('共 9 条'))
await p.fill('[data-testid=ind-search]', '')
await p.waitForFunction(() => /共 4\d 条/.test(document.querySelector('[data-testid=ind-count]')?.textContent ?? ''))
await p.click('[data-testid=sort-tier]')
await p.click('[data-testid=sort-tier]')
await p.waitForFunction(() => document.querySelector('[data-testid=ind-table] tbody tr td:nth-child(6)')?.textContent?.includes('—'))
await p.click('[data-testid=sort-tier]')
await p.click('[data-testid=pager] button:has-text("4")')
await p.waitForFunction(() => document.querySelector('[data-testid=pager] [aria-current=true]')?.textContent?.trim() === '4')
await p.click('[data-testid=pager] button:has-text("1")')
step('筛选:分组 / 标签 / 主题域 / 搜索编码 / 档位排序 / 翻页')

// 指标卡抽屉
await p.click('tr[data-ind=例均基金差额] td:nth-child(3)')
await p.waitForSelector('[data-testid=drawer-formula]')
assert.equal(await p.locator('[data-testid=ind-drawer]').evaluate((e) => e.getBoundingClientRect().width), 440)
assert.match(await text(p, '[data-testid=drawer-formula]'), /Σ 医保记账金额 − Σ DRG支付标准/)
assert.match(await text(p, '[data-testid=drawer-lineage]'), /指标卡 v2\.1→取数批次 B20260905-03→settle_detail_202608/)
assert.match(await text(p, '[data-testid=drawer-changes]'), /初版 · 2025-11-20 · 工作组/)
await shot(p, 'a4-drawer')
await p.click('[data-testid=drawer-mask]', { position: { x: 200, y: 400 } })
await p.waitForSelector('[data-testid=ind-drawer]', { state: 'detached' })
step('指标卡抽屉 440px:公式、取数规则版本、血缘、变更记录;遮罩关闭')

// ---------------------------------------------------------------- 四步向导
await p.click('[data-testid=new-ind]')
await p.waitForSelector('[data-testid=step-1]')
await p.click('[data-testid=restart]')
await toast(p, '已按默认配置重新开始')
await p.waitForSelector('[data-testid=formula-status][data-ok=true]')
assert.equal(await text(p, '[data-testid=formula-status]'), '✓ 语法校验通过 · 引用口径:结算清单 v2026.1 · 预计取数 18,420 例')
assert.equal(await text(p, '[data-testid=wiz-title]'), '新建指标:术前平均住院日')
await shot(p, 'a4-step1')

// 公式校验：GROUP BY 引用未选维度 → 逐行报错
await p.click('[data-testid=dims] [data-dim=时间]')
await p.waitForSelector('[data-testid=formula-status][data-ok=false]')
assert.match(await text(p, '[data-testid=formula-status]'), /✗ 第 5 行:维度「时间」未在左侧选中/)
await p.click('[data-testid=dims] [data-dim=时间]')
await p.waitForSelector('[data-testid=formula-status][data-ok=true]')
// 编辑公式：改名（带时间戳，脚本可重复运行）+ 未知原子指标报错
const name = `术前平均住院日E${Date.now() % 100000}`
const f0 = await p.inputValue('[data-testid=formula-input]')
await p.fill('[data-testid=formula-input]', f0.replace('COUNT(手术出院人次)', 'COUNT(手术人数)'))
await p.waitForSelector('[data-testid=formula-status]:has-text("第 3 行:未知原子指标「手术人数」")')
await p.fill('[data-testid=formula-input]', f0.replace(/^\S+ =/, `${name} =`))
await p.waitForSelector('[data-testid=formula-status][data-ok=true]')
await p.waitForFunction((n) => document.querySelector('[data-testid=wiz-title]')?.textContent?.includes(n), name)
// 函数按钮插入
await p.click('[data-testid=formula-input]')
await p.keyboard.press('Control+End')
await p.click('[data-fn=SUM]')
await p.waitForSelector('[data-testid=formula-status][data-ok=false]')
await p.click('[data-testid=gen-formula]')
await p.waitForSelector('[data-testid=formula-status][data-ok=true]')
assert.match(await p.inputValue('[data-testid=formula-input]'), new RegExp(`^${name} =\\n  SUM\\(术前住院天数\\)\\n  / COUNT\\(手术出院人次\\)\\nWHERE 手术标志 = 1 AND 离院方式 != '死亡'\\nGROUP BY 机构, 等级, 病组, 时间$`))
step('第 1 步:原子指标 / 维度 / 过滤 / 公式编辑器(函数按钮、按配置生成、引擎逐行校验)')

// 第 2 步：五档同级分组（县三级 4 家 · 抑制）、阈值联动、对标档位审批提示
await p.click('[data-testid=wiz-next]')
await p.waitForSelector('[data-testid=step-2]')
assert.equal(await text(p, '[data-peer=县三级]'), '县三级4 家 · 抑制')
await p.fill('[data-testid=min-orgs]', '4')
await p.waitForFunction(() => document.querySelector('[data-peer=县三级]')?.textContent?.trim() === '县三级4 家')
await p.fill('[data-testid=min-orgs]', '5')
await p.click('[data-tier=具名对比与排行]')
assert.match(await text(p, '[data-testid=tier-warn]'), /切换至「具名对比与排行」需提交召集人审批;审批通过前按「匿名分位」发布/)
await shot(p, 'a4-step2')
await p.click('[data-tier=匿名分位]')
assert.equal(await p.locator('[data-testid=tier-warn]').count(), 0)
step('第 2 步:县三级抑制随阈值联动;非默认档提示需召集人审批')

// 第 3 步：模板 + 粒度上限（诊疗行为禁用）
await p.click('[data-step="3"]')
await p.waitForSelector('[data-testid=step-3]')
assert.ok(await p.locator('[data-testid=step-3] [role=radio]:has-text("诊疗行为")').isDisabled())
await shot(p, 'a4-step3')
step('第 3 步:模板、标题单位解读、受众、粒度上限(诊疗行为灰显)')

// 第 4 步：以机构身份预览（后端裁剪）
await p.click('[data-testid=wiz-next]')
await p.waitForSelector('[data-testid=preview-card][data-mode=normal]')
assert.equal(await text(p, '[data-testid=pv-value]'), '2.8 天')
assert.equal(await text(p, '[data-testid=pv-quantiles]'), '1.9 / 2.3 / 2.9 天')
assert.match(await text(p, '[data-testid=pv-pct]'), /本院位于同级 P71/)
await shot(p, 'a4-step4')
await p.click('[data-org=甲县人民医院]')
await p.waitForSelector('[data-testid=preview-card][data-mode=suppressed]')
assert.match(await text(p, '[data-testid=pv-note]'), /同级组仅 4 家,低于抑制阈值 5 家/)
assert.equal(await p.locator('[data-testid=pv-quantiles]').count(), 0)
await shot(p, 'a4-step4-supp')
await p.click('[data-org="召集人(全量)"]')
await p.waitForSelector('[data-testid=preview-card][data-mode=all]')
assert.match(await text(p, '[data-testid=pv-all]'), /县三级4抑制抑制抑制二级甲等91\.72\.22\.8/)
// 机构身份接口只返回本院值：不含他院名称
const leak = await p.evaluate(async () => {
  const h = { Authorization: `Bearer ${localStorage.getItem('dpub.token')}`, 'Content-Type': 'application/json' }
  const d = await (await fetch('/api/v1/indicators/drafts', { method: 'POST', headers: h })).json()
  const r = await (await fetch(`/api/v1/indicators/drafts/${d.id}/preview`, { method: 'POST', headers: h, body: JSON.stringify({ org: '某肛肠专科医院' }) })).text()
  return r
})
assert.ok(leak.includes('某肛肠专科医院') && !leak.includes('示例市第一人民医院') && !leak.includes('"groups"'))
step('第 4 步:以机构身份预览(P71 / 小样本抑制 / 召集人全量),机构接口不含他院数据')

// 仅内部：机构身份下不渲染
await p.click('[data-step="2"]')
await p.click('[data-testid=internal-switch]')
await p.click('[data-step="3"]')
await p.waitForSelector('[data-testid=aud-locked]')
await p.click('[data-step="4"]')
await p.click('[data-org=示例市第一人民医院]')
await p.waitForSelector('[data-testid=preview-card][data-mode=internal]')
await p.waitForSelector('[data-testid=pv-internal]')
await p.click('[data-step="2"]')
await p.click('[data-testid=internal-switch]')
await p.click('[data-step="4"]')
await p.waitForSelector('[data-testid=preview-card][data-mode=normal]')
step('仅内部开关:受众锁定;机构身份预览不返回数值')

// 提交上线审批 → 审批单号（持久化）
await p.click('[data-testid=wiz-submit]')
await toast(p, '已提交上线审批 · 审批单 ZB-')
const no = (await p.textContent('[data-testid=approval-no]')).trim()
assert.match(no, /^ZB-\d{4}-\d{4}$/)
await p.reload()
await p.click('[data-testid=new-ind]').catch(() => {})
await p.goto(BASE + '/a4')
await p.fill('[data-testid=ind-search]', name)
await p.waitForSelector(`tr[data-ind="${name}"]`)
assert.match(await text(p, `tr[data-ind="${name}"]`), /地方增选效DRG月度运行电子病案月匿名分位 ?全市定点机构v0\.1审批中/)
await p.click(`tr[data-ind="${name}"] td:nth-child(3)`)
await p.waitForSelector(`[data-testid=drawer-changes]:has-text("${no}")`)
await p.click('[data-testid=drawer-close]')
step(`提交上线审批 → ${no};新指标以“审批中 v0.1”出现在列表,变更记录可溯`)

// ================================================================ A6 智能推荐中心
await p.goto(BASE + '/a6')
await p.waitForSelector('[data-topic=BR25]')
assert.equal(await text(p, '[data-testid=cand-badge]'), '候选 · 需人工确认')
const scores = await p.$$eval('[data-testid=topics] [data-testid=score]', (els) => els.map((e) => +e.textContent))
assert.deepEqual(scores, [92, 88, 84, 79, 76, 71, 63])
assert.match(await text(p, '[data-topic=GG19]'), /趋势变化大\s*收治结构Z分数异常\s*机构推荐集中/)
// 先把可能残留的处理状态撤销（可重复运行）
for (const code of ['BR25', 'GG19']) {
  if (await p.locator(`[data-topic=${code}] [data-act=undo]`).count()) {
    await p.click(`[data-topic=${code}] [data-act=undo]`)
    await p.waitForSelector(`[data-topic=${code}] [data-act=adopt]`)
  }
}
await shot(p, 'a6-topic')
await p.click('[data-topic=BR25] [data-act=adopt]')
await toast(p, '已采纳 BR25,进入病组专题工作台')
await p.click('[data-topic=GG19] [data-act=reject]')
await toast(p, '已否决 GG19')
assert.equal(await p.locator('[data-topic=GG19]').evaluate((e) => getComputedStyle(e).opacity), '0.5')
await p.reload()
await p.waitForSelector('[data-topic=BR25] [data-testid=topic-status]:has-text("已采纳")')
await p.waitForSelector('[data-topic=GG19] [data-testid=topic-status]:has-text("已否决")')
await shot(p, 'a6-topic-done')
await p.click('[data-topic=BR25] [data-act=undo]')
await toast(p, '已撤销 BR25')
await p.click('[data-topic=GG19] [data-act=undo]')
await p.waitForSelector('[data-topic=GG19] [data-act=adopt]')
step('选题:综合得分 92…63;采纳 / 否决持久化(刷新仍在),可撤销')

// 修改
await p.click('[data-topic=KS15] [data-act=edit]')
await p.waitForSelector('[data-testid=edit-dialog]')
const hadNote = (await p.inputValue('[data-testid=edit-note]')).length > 0
await p.fill('[data-testid=edit-note]', hadNote ? '' : '限定收治 KS15 ≥ 30 例的二级及以上机构')
await p.click('[data-testid=edit-save]')
await toast(p, '已修改 KS15')
step('选题:修改入选理由与选题范围')

// 方法卡
await p.click('[data-testid=open-method]')
await p.waitForSelector('[data-testid=method-dialog]:has-text("方法卡 · 选题推荐")')
assert.match(await text(p, '[data-testid=method-dialog]'), /拟合度\s*综合得分与近 4 期专家选题一致率 0\.81/)
await p.waitForTimeout(400)
await shot(p, 'a6-method')
await p.keyboard.press('Escape')
step('方法卡弹窗:适用条件、样本量、拟合度、已知局限')

// 归因：编码质量不达标暂不输出
await p.click('[data-tab=attr]')
await p.waitForSelector('[data-attr=GG19]')
assert.match(await text(p, '[data-attr=BR25]'), /机构间例均费用差异 4,860 元的拆分\s*患者差异 38%\s*行为差异 62%\s*主要行为:入院72小时内重复检查\(\+1,120 元\)、使用辅助用药\(\+860 元\)/)
assert.match(await text(p, '[data-attr=GG19]'), /暂不输出/)
assert.match(await text(p, '[data-attr=GG19] [data-testid=withheld-reason]'), /合并症编码率 41%,低于 60% 的输出门槛;某肛肠专科医院结算清单质控率 82%/)
await shot(p, 'a6-attr')
step('归因:患者 / 行为差异拆分;GG19 编码质量不达标“暂不输出”及原因')

// 标杆：阈值步进实时重算
await p.click('[data-tab=bench]')
await p.waitForSelector('[data-bench=一级]')
assert.equal(await text(p, '[data-bench=县三级] [data-testid=bench-small]'), '同级机构数 < 5,样本不足,不分组')
const count = async () => text(p, '[data-bench=一级] [data-testid=bench-counts]')
assert.equal(await count(), '3 / 14 / 5 家')
await p.click('[data-testid=th-down]')
await p.click('[data-testid=th-down]')
await p.click('[data-testid=th-down]')
await p.waitForFunction(() => document.querySelector('[data-testid=bench-rule]')?.textContent?.includes('P60'))
assert.equal(await count(), '3 / 10 / 9 家')
assert.ok(await p.locator('[data-testid=th-down]').isDisabled())
for (let i = 0; i < 6; i++) await p.click('[data-testid=th-up]')
await p.waitForFunction(() => document.querySelector('[data-testid=th-value]')?.textContent === 'P90')
await p.waitForFunction(() => document.querySelector('[data-testid=bench-rule]')?.textContent?.includes('P90'))
assert.equal(await count(), '3 / 17 / 2 家')
assert.ok(await p.locator('[data-testid=th-up]').isDisabled())
await p.click('[data-testid=th-down]')
await p.click('[data-testid=th-down]')
await p.click('[data-testid=th-down]')
await p.waitForFunction(() => document.querySelector('[data-testid=bench-rule]')?.textContent?.includes('P75'))
await shot(p, 'a6-bench')
step('标杆:P60–P90 步长 5,标杆/中间/偏离实时重算;县三级样本不足不分组')

// 异常：生成提醒函（持久化）
await p.click('[data-tab=anom]')
await p.waitForSelector('[data-anomaly]')
const btn = p.locator('[data-anomaly] [data-act=letter]').first()
if (await btn.count()) {
  await btn.click()
  await toast(p, '关联预警与整改已有触发记录')
}
await p.waitForSelector('[data-testid=letter-no]')
await shot(p, 'a6-anom')
await p.click('[data-tab=pres]')
await p.waitForSelector('[data-testid=pres]:has-text("推荐:归因瀑布")')
step('异常:机构仅医保局可见,生成提醒函草稿;呈现推荐')

// 越权：机构身份访问本组接口 → 403
const m = await session('limin')
const st = await m.evaluate(async () => {
  const h = { Authorization: `Bearer ${localStorage.getItem('dpub.token')}` }
  return [(await fetch('/api/v1/indicators', { headers: h })).status, (await fetch('/api/v1/recommend/anomalies', { headers: h })).status]
})
assert.deepEqual(st, [403, 403])
step('机构身份访问 /indicators、/recommend → 403')

await browser.close()
if (errors.length) {
  console.error('页面错误：\n' + errors.join('\n'))
  process.exit(1)
}
console.log('indicator e2e 全部通过')
