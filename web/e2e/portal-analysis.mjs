// 端到端：机构门户分析（B2 病组下钻与专题 / B3 对标与PK / B7 区域外数据）。需三端已启动且为种子数据。
// 运行：BASE=http://localhost:5204 CHROME=/opt/pw-browsers/chromium node e2e/portal-analysis.mjs
// 可选：PGDB=dpub_portal_analysis（默认）——用于模拟 A13 改档审批通过，验证 B3 随 benchmark_tier 变化，结束后还原。
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'

const BASE = process.env.BASE ?? 'http://localhost:5204'
const PGDB = process.env.PGDB ?? 'dpub_portal_analysis'
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined })
const errors = []
const step = (name) => console.log('✓', name)
const toast = async (p, text) => p.waitForSelector(`[data-sonner-toast]:has-text("${text}")`, { timeout: 5000 })
const text = async (p, sel) => (await p.locator(sel).first().innerText()).replace(/\s+/g, ' ').trim()

async function session(who) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
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

/** 调用后端接口（带当前会话令牌），返回 {status, body}。 */
const api = (p, path) =>
  p.evaluate(async (path) => {
    const r = await fetch('/api/v1' + path, { headers: { Authorization: 'Bearer ' + localStorage.getItem('dpub.token') } })
    return { status: r.status, body: await r.text() }
  }, path)

const OTHER_HOSPITALS = ['示例市妇幼保健院', '示例市第二人民医院', '示例市中医院', '示例市第三人民医院', '示例市肿瘤医院']
const noOtherNames = (s, where) => OTHER_HOSPITALS.forEach((n) => assert.ok(!s.includes(n), `${where} 不应出现他院名称 ${n}`))

const p = await session('limin')
const nav = await p.$$eval('[data-nav]', (els) => els.map((e) => e.getAttribute('data-nav')))
for (const k of ['B2', 'B3', 'B7']) assert.ok(nav.includes(k), `菜单应包含 ${k}`)
step('limin 账号 + 短信验证码登录,机构门户菜单含 B2 / B3 / B7')

// ---------------------------------------------------------------- B2 病组下钻与专题
await p.click('[data-nav=B2]')
await p.waitForSelector('[data-testid=group-detail][data-code=BR25]')
assert.deepEqual(await p.$$eval('[data-group]', (els) => els.map((e) => e.getAttribute('data-group'))), ['BR25', 'IU29', 'BR11', 'ES35', 'FM19'])
assert.equal(await text(p, '[data-testid=kpi-diff]'), '+2,219 元')
assert.equal(await text(p, '[data-testid=kpi-pct]'), 'P89')
assert.match(await p.getAttribute('[data-testid=kpi-pct]', 'class'), /text-warning/)
assert.equal(await p.locator('[data-testid=behaviors] [data-concern=true]').count(), 4)
assert.match(await text(p, '[data-testid=gaps]'), /例均费用 15,194 元 12,459 元 \+2,735 元/)
step('B2 默认 BR25:KPI 4 宫格(逆差红、P89 橙)、关键行为偏差橙、标杆差距')

await p.click('[data-group=FM19]')
await p.waitForSelector('[data-testid=group-detail][data-code=FM19]')
assert.equal(await text(p, '[data-testid=group-name]'), '经皮心血管操作及支架置入')
assert.equal(await text(p, '[data-testid=kpi-diff]'), '−2,432 元')
assert.match(await p.getAttribute('[data-testid=kpi-diff]', 'class'), /text-success/)
assert.match(await p.getAttribute('[data-testid=kpi-pct]', 'class'), /text-primary/)
await p.click('[data-group=ES35]')
await p.waitForSelector('[data-testid=group-detail][data-code=ES35]')
assert.equal(await text(p, '[data-testid=kpi-diff]'), '+276 元')
step('B2 切换病组 FM19(结余绿、P38 蓝)→ ES35')

const small = await text(p, '[data-testid=small-groups]')
for (const c of ['AH29', 'IB39', 'NS15', 'JR15']) assert.ok(small.includes(c))
assert.equal(await p.locator('[data-testid=small-groups] >> text=已并入其他').count(), 4)
assert.equal(await text(p, '[data-testid=other-total]'), '“其他”合计 54 例 · 例均差额 +180 元')
const sg = await api(p, '/portal/groups/AH29')
assert.equal(sg.status, 404)
assert.match(JSON.parse(sg.body).message, /已并入其他/)
const idx = JSON.parse((await api(p, '/portal/groups')).body)
assert.deepEqual(Object.keys(idx.small[0]).sort(), ['code', 'name'])
step('B2 小样本病组(病例 < 30)只显示“已并入其他”,后端不下发其数值,单独请求 404')

await p.click('[data-testid=export-btn]')
await p.waitForSelector('[data-testid=export-content]')
assert.match(await text(p, '[data-testid=export-content]'), /本院病组下钻/)
await p.click('[data-testid=export-submit]')
assert.match(await text(p, '[data-testid=export-wm]'), /^WM-\d{8}-\d{4}$/)
await p.keyboard.press('Escape')
await p.click('[data-topic=B5]')
await p.waitForURL(/\/b5$/)
step('B2 导出走审批返回水印编号;相关专题“去核对”跳转 B5')

// ---------------------------------------------------------------- B3 对标与PK
await p.click('[data-nav=B3]')
await p.waitForSelector('[data-testid=bench-detail][data-tier="0"]')
const tiers = await p.$$eval('[data-indicator]', (els) => els.map((e) => [e.getAttribute('data-indicator'), e.getAttribute('data-tier')]))
assert.deepEqual(tiers, [['例均基金差额', '0'], ['次均总费用', '0'], ['CMI值', '1'], ['费用消耗指数', '0'], ['结算清单质控率', '2'], ['14天再住院率', '0']])
assert.equal(await text(p, '[data-testid=bench-title]'), '例均基金差额')
assert.equal(await text(p, '[data-testid=own-value]'), '+486 元')
assert.equal(await text(p, '[data-testid=quartiles]'), '−210 / +173 / +579 元')
assert.equal(await text(p, '[data-testid=own-pct]'), 'P60')
assert.equal(await p.getAttribute('[data-testid=pct-bar]', 'data-concern'), 'false')
assert.equal(await p.locator('[data-testid=pk-disabled]').count(), 1)
await p.click('[data-testid=pk-disabled]', { force: true }) // aria-disabled:真实点击仍会给出提示
await toast(p, '该指标未开放具名对比')
step('B3 档位读 benchmark_tier;匿名分位 → 分位条(P60 蓝);“发起 PK”置灰并提示')

await p.click('[data-indicator=费用消耗指数]')
await p.waitForSelector('[data-testid=bench-title]:has-text("费用消耗指数")')
assert.equal(await text(p, '[data-testid=own-pct]'), 'P80')
assert.equal(await p.getAttribute('[data-testid=pct-bar]', 'data-concern'), 'true')
step('B3 费用消耗指数 P80 且高为差 → 本院标记橙色')

await p.click('[data-indicator=CMI值]')
await p.waitForSelector('[data-testid=bench-detail][data-tier="1"]')
const anon = await p.$$eval('[data-testid=anon-rows] > div', (els) => els.map((e) => e.innerText.replace(/\s+/g, ' ').trim()))
assert.deepEqual(anon, ['三级医院A 1.31', '三级医院B 1.18', '本院 1.12', '三级医院C 1.06', '三级医院D 0.98', '三级医院E 0.91'])
assert.equal(await p.locator('[data-testid=anon-rows] [data-own=true]').count(), 1)
assert.equal(await p.locator('[data-testid=pk-disabled]').count(), 1)
noOtherNames(await text(p, '[data-testid=bench-detail]'), 'B3 匿名编号档页面')
for (const ind of ['例均基金差额', 'CMI值', '14天再住院率']) {
  const r = await api(p, '/portal/benchmark/' + encodeURIComponent(ind))
  assert.equal(r.status, 200)
  noOtherNames(r.body, `B3 ${ind} 接口`)
  assert.ok(!r.body.includes('"named"'))
}
step('B3 匿名编号 → 横条(本院高亮,他院三级医院A–E);非具名档接口不含他院名称')

await p.click('[data-indicator=结算清单质控率]')
await p.waitForSelector('[data-testid=bench-detail][data-tier="2"]')
assert.equal(await p.locator('[data-testid=pk-open]').count(), 1)
assert.equal(await p.locator('[data-pk]').count(), 5)
await p.click('[data-pk=示例市中医院]')
assert.match(await text(p, '[data-testid=pk-card]'), /示例市中医院 96\.2% 同级第 4 名/)
assert.match(await text(p, '[data-testid=pk-cards]'), /示例市第一人民医院 96\.4% 同级第 3 名/)
assert.equal(await p.locator('[data-testid=rank-rows] > div').count(), 6)
step('B3 具名对比与排行 → 选择对比医院 → 两卡并排 + 同级排行')

// 模拟 A13 改档单经 A8 审批通过：CMI值 匿名编号 → 具名对比与排行；B3 随之变化（结束后还原）
const psql = (sql) => execFileSync('psql', ['-h', 'localhost', '-U', 'dpub', '-d', PGDB, '-qtc', sql], { env: { ...process.env, PGPASSWORD: 'dpub' } })
psql("update benchmark_tier set tier = 2 where indicator = 'CMI值'")
try {
  await p.reload()
  await p.waitForSelector('[data-indicator=CMI值][data-tier="2"]')
  await p.click('[data-indicator=CMI值]')
  await p.waitForSelector('[data-testid=bench-detail][data-tier="2"]')
  assert.match(await text(p, '[data-testid=rank-rows]'), /^1 示例市肿瘤医院 1\.31/)
} finally {
  psql("update benchmark_tier set tier = 1 where indicator = 'CMI值'")
}
step('B3 档位变更(benchmark_tier)后即时按新档位呈现')

// ---------------------------------------------------------------- B7 区域外数据
await p.click('[data-nav=B7]')
await p.waitForSelector('[data-testid=source-banner]')
assert.match(await text(p, '[data-testid=source-banner]'), /^来源:省平台回流,截至 2026-08-31/)
assert.match(await text(p, '[data-testid=offsite-kpis]'), /4,286.*4\.87 亿.*11\.6%.*1,104 人次/)
assert.equal(await p.locator('[data-testid=diseases] > div').count(), 5)
assert.deepEqual(await p.$$eval('[data-scope]', (els) => els.map((e) => e.getAttribute('data-scope'))), ['省内', '省内', '省外', '省外', '省外', '—'])
assert.match(await text(p, '[data-testid=levels]'), /三级 71% · 二级 21% · 其他 8%/)
const off = (await api(p, '/portal/offsite')).body
assert.ok(!/医院|org|institution/i.test(off), 'B7 接口不应返回就医地机构明细')
step('B7 固定来源横幅、3 KPI、外流病种 Top5、流向地区(省内蓝/省外橙)、等级构成;无机构明细')

// ---------------------------------------------------------------- 越权：非机构身份 403
const q = await session('lihua')
for (const path of ['/portal/groups', '/portal/benchmark', '/portal/offsite']) assert.equal((await api(q, path)).status, 403)
step('非机构身份访问 /portal/** 一律 403')

await browser.close()
if (errors.length) {
  console.error(errors)
  process.exit(1)
}
console.log('portal-analysis e2e 全部通过')
