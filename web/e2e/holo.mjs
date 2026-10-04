// 全息图端到端（演示模式、种子数据）：BASE=http://localhost:5201 CHROME=/opt/pw-browsers/chromium node e2e/holo.mjs
// 覆盖：A2 召集人 UKey 登录 → 气泡全景（关键少数、选气泡、下钻条、月/季/年口径）→ 五图层（效/错外环、区域外流向与机构排名、公开状态矩阵）
//       → 快捷专区 → 导出审批；B1 limin 账号 + 短信 → 本院气泡（同级灰底、小样本并入“其他”）→ 六项分位 → 记账偏离 → 导出；
//       双向越权 403；B1 接口不下发他院字段。
// SHOT=1 时按图层 / 状态另存截图到 e2e/out/holo-*.png（THEME=dark 截深色）。
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE ?? 'http://localhost:5201'
const SHOT = !!process.env.SHOT
const THEME = process.env.THEME ?? 'light'
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined })
const errors = []
if (SHOT) mkdirSync('e2e/out', { recursive: true })

async function session(who) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript((t) => localStorage.setItem('dpub.theme', t), THEME)
  const p = await ctx.newPage()
  p.on('pageerror', (e) => errors.push(`${who}: ${e.message}`))
  await p.goto(BASE + '/login')
  await p.waitForSelector('[data-testid=login-card]')
  if (who === 'ca') {
    await p.waitForSelector('[data-testid=cert-card]')
    await p.fill('#lg-pin', '123456')
  } else {
    await p.click('text=账号 + 短信验证码')
    await p.fill('#lg-user', who)
    await p.fill('#lg-pwd', 'demo')
    await p.fill('#lg-code', '482916')
  }
  await p.click('[data-testid=login-submit]')
  // 多身份账号先选身份；单身份账号直接进入工作台
  await p.waitForSelector('[data-testid=identity-step], [data-testid=side-nav]')
  if (await p.isVisible('[data-testid=identity-step]')) {
    if (who === 'ca') await p.click('[data-identity=CONVENER]')
    await p.click('[data-testid=enter-btn]')
    await p.waitForSelector('[data-testid=side-nav]')
  }
  return p
}

const step = (name) => console.log('✓', name)
const text = async (p, sel) => (await p.textContent(sel)).replace(/\s+/g, ' ').trim()
const shot = async (p, name) => SHOT && (await p.waitForTimeout(300), await p.screenshot({ path: `e2e/out/holo-${name}-${THEME}.png` }))
const api = (p, path) =>
  p.evaluate(async (u) => {
    const r = await fetch(u, { headers: { Authorization: `Bearer ${localStorage.getItem('dpub.token')}` } })
    return { status: r.status, body: r.status === 200 ? await r.json() : null }
  }, '/api/v1' + path)

// ================================================================ A2 召集人（UKey）
const c = await session('ca')
await c.goto(BASE + '/a2')
await c.waitForSelector('[data-testid=bubble-chart] [data-code=BR25]')
assert.equal(await c.locator('[data-testid=bubble-chart] [data-code]').count(), 23)
assert.match(await text(c, '[data-testid=key-summary]'), /关键少数病组 6 个 · 合计逆差 554\.8万/)
assert.equal(await text(c, '[data-testid=detail-code]'), 'BR25')
assert.match(await text(c, '[data-testid=detail]'), /关键少数病组/)
await shot(c, 'A2-money')
step('A2 召集人 UKey 登录 → 首页全息图;23 个病组气泡,关键少数 6 个 · 合计逆差 554.8万')

// 选气泡 → 右侧详情 + 下钻条进到“病组”
await c.click('[data-testid=bubble-chart] [data-code=IC29]')
await c.waitForFunction(() => document.querySelector('[data-testid=detail-code]')?.textContent?.trim() === 'IC29')
assert.match(await text(c, '[data-drill=病组]'), /病组\s*IC29/)
assert.equal(await c.getAttribute('[data-drill=病组]', 'aria-current'), 'step')
assert.match(await text(c, '[data-testid=detail]'), /髋、膝关节置换.*关键少数病组|关键少数病组.*髋、膝关节置换/)
await c.click('[data-drill=诊疗行为]')
assert.doesNotMatch(await text(c, '[data-drill=诊疗行为]'), /—$/)
step('点击气泡 IC29 → 右侧详情(费用结构 / 同级分位 / 行为归因 / 标杆差距),下钻条进到病组与诊疗行为')

// 月 / 季 / 年口径
await c.click('[data-testid=period-tabs] button:has-text("季")')
await c.waitForFunction(() => document.querySelector('[data-testid=period-label]')?.textContent?.trim() === '2026年第三季度')
await c.waitForFunction(() => /1,664\.4万/.test(document.querySelector('[data-testid=key-summary]')?.textContent ?? ''))
await c.waitForFunction(() => document.querySelector('[data-testid=detail-cases]')?.textContent?.trim() === '630')
await c.click('[data-testid=period-tabs] button:has-text("年")')
await c.waitForFunction(() => document.querySelector('[data-testid=period-label]')?.textContent?.trim() === '2025年度')
await c.click('[data-testid=period-tabs] button:has-text("月")')
await c.waitForFunction(() => /554\.8万/.test(document.querySelector('[data-testid=key-summary]')?.textContent ?? ''))
step('月 / 季 / 年口径切换:期次文字、合计逆差与病例数按口径换算')

// 图层：效 / 错 外环
await c.click('[data-layer=eff]')
await c.waitForSelector('[data-testid=ring-legend]')
assert.equal(await c.locator('[data-testid=bubble-chart] [data-ring]').count(), 4)
assert.match(await text(c, '[data-testid=ring-legend]'), /时间消耗指数 > 1\.10 4 个/)
await shot(c, 'A2-eff')
await c.click('[data-layer=err]')
await c.waitForFunction(() => /审核扣款/.test(document.querySelector('[data-testid=ring-legend]')?.textContent ?? ''))
assert.equal(await c.locator('[data-testid=bubble-chart] [data-ring]').count(), 4)
for (const code of ['BR25', 'GG19', 'IU29', 'ES35']) assert.equal(await c.locator(`[data-code=${code}] [data-ring]`).count(), 1)
await shot(c, 'A2-err')
step('“效”图层 4 个橙色外环(时间消耗指数 > 1.10);“错”图层 4 个红色外环(审核扣款 / 疑似高套)')

// 区域外
await c.click('[data-layer=out]')
await c.waitForSelector('[data-testid=flow-map] svg path')
assert.equal(await c.locator('[data-flow]').count(), 6)
assert.match(await text(c, '[data-flow=省会市]'), /38\.2% · 1\.86亿/)
assert.match(await text(c, '[data-testid=ranking]'), /仅医保局可见.*省会市某三甲医院A.*1,026 人次/)
assert.equal(await c.locator('[data-testid=detail]').count(), 0)
const w = await c.$$eval('[data-testid=flow-map] svg:not([data-ck-flow]) path', (ps) => ps.map((x) => Number(x.getAttribute('stroke-width'))))
assert.ok(w[0] > w[1] && w[1] > w[4], '线宽 ∝ 占比')
await shot(c, 'A2-out')
step('“区域外”图层:6 条流向(线宽 ∝ 占比)+ 汇总;就医地机构排名标注“仅医保局可见”')

// 公开状态
await c.click('[data-layer=pub]')
await c.waitForSelector('[data-testid=pub-matrix]')
assert.match(await text(c, '[data-testid=pub-counts]'), /3该公开未公开.*4发了没人看.*2意见集中未答复/)
for (const [k, n] of [['np', 3], ['low', 4], ['cmt', 2], ['int', 7]]) assert.equal(await c.locator(`[data-cell=${k}]`).count(), n)
assert.match(await text(c, '[data-testid=pub-status]'), /答复率 78%\(5 条超期\)/)
await shot(c, 'A2-pub')
step('“公开状态”图层:三类问题 3 / 4 / 2,指标 × 受众矩阵,仅内部整行置灰')

// 快捷专区：重点病组 → 回到气泡图并选中逆差最大的关键少数病组
await c.click('[data-shortcut=key]')
await c.waitForSelector('[data-testid=bubble-chart]')
await c.waitForFunction(() => document.querySelector('[data-testid=detail-code]')?.textContent?.trim() === 'BR25')
assert.match(await text(c, '[data-shortcut=key]'), /关键少数 6 个 · 逆差 554\.8万/)
step('快捷专区“重点病组”→ 回到气泡全景并定位 BR25')

// 导出审批
await c.click('[data-testid=export-btn]')
assert.match(await text(c, '[data-testid=export-content]'), /全息图当前视图/)
await c.click('[data-testid=export-submit]')
const wmA = (await c.textContent('[data-testid=export-wm]')).trim()
assert.match(wmA, /^WM-\d{8}-\d{4}$/)
await c.keyboard.press('Escape')
step(`A2 导出审批 → ${wmA}`)

assert.equal((await api(c, '/portal/holo')).status, 403)
step('召集人访问机构门户接口 /portal/holo → 403')

// ================================================================ B1 机构（limin 账号 + 短信）
const h = await session('limin')
let portal = null
h.on('response', async (r) => {
  if (r.url().endsWith('/api/v1/portal/holo') && r.status() === 200) portal = await r.json()
})
await h.waitForURL(/\/b1$/)
if (!portal) await h.reload()
await h.waitForSelector('[data-testid=bubble-chart] [data-code=BR25]')
assert.equal(await text(h, '[data-testid=org-name]'), '示例市第一人民医院')
assert.equal(await h.locator('[data-testid=bubble-chart] [data-code]').count(), 19) // 18 个本院病组 + “其他”
assert.equal(await h.locator('[data-code=AH29]').count(), 0)
assert.match(await text(h, '[data-testid=sel-strip]'), /BR25.*本院 253 例.*\+2,219 元.*全市同组 \+1,860 元/)
await shot(h, 'B1')
step('B1 limin 账号 + 短信 → 本院全息图;18 个本院病组 + 同级灰色背景')

// 后端裁剪：只下发本院 + 同级均值（无他院字段）；小样本不单列
await h.waitForFunction(() => true)
for (let i = 0; i < 20 && !portal; i++) await h.waitForTimeout(100)
assert.ok(portal, '捕获到 /portal/holo 响应')
const raw = JSON.stringify(portal)
for (const other of ['第二人民医院', '第三人民医院', '中医院', '甲县', '某三甲医院']) assert.ok(!raw.includes(other), `不应出现他院:${other}`)
assert.ok(portal.peers.every((x) => Object.keys(x).sort().join() === 'avgDiff,cases,code,radius'))
assert.ok(!portal.own.some((o) => o.cases < 30))
assert.deepEqual(portal.merged, { count: 3, cases: 45, avgDiff: -1421 })
assert.ok(!('ranking' in portal))
step('B1 接口只含本院病组与同级均值(仅编码 / 均值),病例 < 30 的 3 个病组并入“其他”,无他院名称')

await h.click('[data-code=__other__]')
assert.match(await text(h, '[data-testid=merged-note]'), /已并入“其他”/)
step('点击“其他”→ 提示本院病例 < 30,机构门户中已并入“其他”')

assert.equal(await h.locator('[data-indicator]').count(), 6)
assert.match(await text(h, '[data-indicator=医保外费用占比]'), /P72.*高于同级 P70,关注/)
assert.doesNotMatch(await text(h, '[data-indicator=CMI]'), /关注/)
assert.match(await text(h, '[data-testid=ledger-dev]'), /逆差 246\.7 万/)
assert.match(await text(h, '[data-testid=ledger]'), /\+486 元.*同级 P62/)
await h.click('[data-top=IU29]')
assert.match(await text(h, '[data-testid=sel-strip]'), /IU29.*骨病及其他关节病/)
step('六项核心指标同级匿名分位(医保外费用占比 P72 标橙);记账偏离 逆差 246.7 万;逆差贡献 Top 4 可点选')

await h.click('[data-testid=export-btn]')
assert.match(await text(h, '[data-testid=export-content]'), /本院全息图/)
await h.click('[data-testid=export-submit]')
const wmB = (await h.textContent('[data-testid=export-wm]')).trim()
assert.match(wmB, /^WM-\d{8}-\d{4}$/)
await h.keyboard.press('Escape')
step(`B1 导出审批 → ${wmB}`)

await h.click('[data-testid=go-b2]')
await h.waitForURL(/\/b2\?code=IU29$/)
step('病组下钻 › → B2(带病组编码)')

for (const path of ['/holo/overview', '/holo/offsite', '/holo/groups/BR25']) assert.equal((await api(h, path)).status, 403)
step('机构身份访问 A2 接口 → 403(就医地机构排名等医保局数据不下发)')

assert.deepEqual(errors, [])
await browser.close()
console.log('全息图 e2e 通过')
