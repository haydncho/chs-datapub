// regional 分组端到端（演示模式、种子数据）：node e2e/regional.mjs
// 覆盖：C1 县区视图（本县机构排序、其他县区仅汇总、医共体指标、导出审批、越权调用省级接口 403）、
//       C2 省级汇总（KPI、逾期行、表头升 / 降序、无机构级字段）、
//       C3 外部监督（倒计时每秒刷新、材料切换、预览水印、禁用右键 / 打印、无导出、到期失效）。
// 环境变量：BASE（默认 http://localhost:5206）、CHROME（浏览器路径）、
//           REGIONAL_DB=dpub_regional（可选：直接改库验证服务端到期 403，结束后恢复）
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'

const BASE = process.env.BASE ?? 'http://localhost:5206'
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined })
const errors = []

async function session(who, { clock = false } = {}) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const p = await ctx.newPage()
  if (clock) await p.clock.install()
  p.on('pageerror', (e) => errors.push(`${who}: ${e.message}`))
  await p.goto(BASE + '/login')
  await p.waitForSelector('[data-testid=login-card]')
  await p.click('text=账号 + 短信验证码')
  await p.fill('#lg-user', who)
  await p.fill('#lg-pwd', 'demo')
  await p.fill('#lg-code', '482916')
  await p.click('[data-testid=login-submit]')
  await p.waitForSelector('[data-testid=side-nav]')
  return p
}

const toast = async (p, text) => p.waitForSelector(`[data-sonner-toast]:has-text("${text}")`, { timeout: 5000 })
const navKeys = (p) => p.$$eval('[data-nav]', (els) => els.map((e) => e.getAttribute('data-nav')))
const api = (p, path) =>
  p.evaluate(async (u) => {
    const r = await fetch(u, { headers: { Authorization: `Bearer ${localStorage.getItem('dpub.token')}` } })
    return { status: r.status, body: await r.json().catch(() => null) }
  }, '/api/v1' + path)
const col = (p, table, idx) => p.$$eval(`[data-testid=${table}] tbody tr`, (rows, i) => rows.map((r) => r.children[i].textContent.trim()), idx)
const step = (name) => console.log('✓', name)

async function exportOnce(p, content) {
  await p.click('[data-testid=export-btn]')
  await p.waitForSelector(`[data-testid=export-content]:has-text("${content}")`)
  await p.click('[data-testid=export-submit]')
  const wm = (await p.textContent('[data-testid=export-wm]')).trim()
  assert.match(wm, /^WM-\d{8}-\d{4}$/)
  await p.keyboard.press('Escape')
  return wm
}

// ================================================================ C1 县区（钱丽 · 甲县医保局）
const q = await session('qianli')
assert.match(q.url(), /\/c1$/)
assert.deepEqual(await navKeys(q), ['C1', 'E1'])
assert.equal(await q.$('[data-testid=zone-tag]'), null)
await q.waitForSelector('[data-testid=c1-institutions] tbody tr')
const INST = '[data-testid=c1-institutions]'
assert.equal((await col(q, 'c1-institutions', 0))[0], '甲县人民医院')
assert.equal((await q.textContent(`${INST} [data-sort=cases] [data-sort-arrow]`)).trim(), '↓')
step('C1 登录 → 首页县区视图,默认按病例数降序')

await q.click(`${INST} [data-sort=listQcPct] button`)
assert.deepEqual(await col(q, 'c1-institutions', 4), ['97.1%', '96.8%', '95.4%', '94.2%', '92.6%', '91.2%'])
assert.equal((await q.textContent(`${INST} [data-sort=listQcPct] [data-sort-arrow]`)).trim(), '↓')
await q.click(`${INST} [data-sort=listQcPct] button`)
assert.deepEqual(await col(q, 'c1-institutions', 4), ['91.2%', '92.6%', '94.2%', '95.4%', '96.8%', '97.1%'])
assert.equal((await q.textContent(`${INST} [data-sort=listQcPct] [data-sort-arrow]`)).trim(), '↑')
await q.click(`${INST} [data-sort=avgDiff] button`)
assert.deepEqual(await col(q, 'c1-institutions', 3), ['+712 元', '+96 元', '−24 元', '−41 元', '−62 元', '−138 元'])
assert.equal(await q.locator(`${INST} [data-sort=listQcPct] [data-sort-arrow]`).count(), 0)
step('C1 数值列表头排序:降序 ↓ → 再点升序 ↑ → 换列重新降序')

assert.deepEqual(await col(q, 'c1-counties', 1), ['第 1', '第 2', '第 3', '第 4'])
assert.equal(await q.getAttribute('[data-testid=c1-counties] tr[data-self]', 'data-county'), '甲县')
assert.equal(await q.getAttribute('[data-testid=c1-counties] tr[data-self]', 'data-on'), 'true')
const ov = await api(q, '/county/overview')
assert.equal(ov.status, 200)
assert.ok(ov.body.institutions.every((i) => i.name.startsWith('甲县')), '只下发本县区机构')
assert.ok(!JSON.stringify(ov.body).includes('乙县人民医院') && !(await q.content()).includes('乙县人民医院'))
assert.ok(ov.body.counties.every((c) => Object.keys(c).every((k) => ['name', 'rank', 'avgDiff', 'avgDiffText', 'listQcPct', 'score', 'self'].includes(k))))
step('C1 其他县区仅汇总 + 排名(本县高亮);接口只含本县区机构明细')

assert.equal(await q.locator('[data-testid=c1-monitors] [data-monitor]').count(), 14)
assert.equal(await q.locator('[data-testid=c1-monitors] [data-status=warn]').count(), 3)
assert.equal(await q.getAttribute('[data-monitor=例均基金差额]', 'data-status'), 'bad')
step('C1 医共体 14 项监测指标 7×2 网格(正常 10 / 关注 3 / 预警 1)')

const wm1 = await exportOnce(q, '甲县县区视图')
step(`C1 导出审批 → ${wm1}`)

assert.equal((await api(q, '/province/summary')).status, 403)
assert.equal((await api(q, '/supervision/seat')).status, 403)
await q.goto(BASE + '/c2')
await q.waitForURL(/\/c1$/)
step('C1 身份调用 /api/v1/province/** → 403;前端不渲染无权页面')

// ================================================================ C2 省级（王磊 · 省医保局）
const w = await session('wanglei')
assert.match(w.url(), /\/c2$/)
assert.deepEqual(await navKeys(w), ['C2', 'E1'])
await w.waitForSelector('[data-testid=c2-regions] tbody tr')
const kpis = await w.textContent('[data-testid=c2-kpis]')
for (const t of ['7', '/ 8 个统筹区', '统筹区06 逾期 12 天', '92.8%', '较上期 +1.6 pt', '71.0%', '低于 60% 的统筹区 2 个', '3.5%', '1 个统筹区当期赤字']) {
  assert.ok(kpis.includes(t), `KPI 缺少「${t}」`)
}
assert.match(await w.textContent('[data-testid=c2-identity]'), /只看汇总层。本视图不提供任何机构级数据/)
assert.equal(await w.locator('[data-testid=c2-insights] > div').count(), 3)
step('C2 身份提示条、4 KPI、3 张洞察卡')

const REG = '[data-testid=c2-regions]'
assert.deepEqual(await col(w, 'c2-regions', 0), ['统筹区04', '示例市', '统筹区08', '统筹区02', '统筹区07', '统筹区03', '统筹区05', '统筹区06'])
const late = w.locator(`${REG} tr[data-overdue]`)
assert.equal(await late.count(), 1)
assert.equal(await late.getAttribute('data-region'), '统筹区06')
assert.equal((await late.locator('td').nth(2).textContent()).trim(), '逾期 12 天')
const lateBg = await late.locator('td').first().evaluate((el) => getComputedStyle(el).backgroundColor)
const normBg = await w.locator(`${REG} tr[data-region=统筹区04] td`).first().evaluate((el) => getComputedStyle(el).backgroundColor)
assert.notEqual(lateBg, normBg, '逾期行应为浅橙底')
step('C2 默认按签收率降序;逾期统筹区整行浅橙底、橙字「逾期 12 天」')

await w.click(`${REG} [data-sort=signPct] button`)
await w.waitForFunction(() => document.querySelector('[data-testid=c2-regions] tbody tr td')?.textContent?.trim() === '统筹区06')
assert.equal((await w.textContent(`${REG} [data-sort=signPct] [data-sort-arrow]`)).trim(), '↑')
await w.click(`${REG} [data-sort=avgDiff] button`)
await w.waitForFunction(() => document.querySelector('[data-testid=c2-regions] [data-sort=avgDiff] [data-sort-arrow]')?.textContent === '↓')
await w.waitForFunction(() => document.querySelector('[data-testid=c2-regions] tbody tr td')?.textContent?.trim() === '统筹区06')
await w.click(`${REG} [data-sort=avgDiff] button`)
await w.waitForFunction(() => document.querySelector('[data-testid=c2-regions] tbody tr td')?.textContent?.trim() === '统筹区04')
assert.deepEqual(await col(w, 'c2-regions', 8), ['−205 元', '−112 元', '−76 元', '−38 元', '−21 元', '+64 元', '+138 元', '+322 元'])
await w.click(`${REG} [data-sort=period] button`)
await w.waitForFunction(() => document.querySelector('[data-testid=c2-regions] [data-sort=period] [data-sort-arrow]')?.textContent === '↓')
await w.click(`${REG} [data-sort=period] button`)
await w.waitForFunction(() => document.querySelector('[data-testid=c2-regions] tbody tr td')?.textContent?.trim() === '统筹区05')
step('C2 表头点击升 / 降序(签收率、例均差额、期次按时间序)')

const ps = await api(w, '/province/summary?sort=signPct&dir=desc')
const ROW_KEYS = ['name', 'self', 'lastPeriod', 'dateLabel', 'overdue', 'overdueDays', 'signPct', 'readPct', 'readLow', 'replyPct', 'balancePct', 'deficit', 'coveragePct', 'avgDiff', 'avgDiffText']
assert.ok(ps.body.rows.every((r) => Object.keys(r).every((k) => ROW_KEYS.includes(k))), '接口不含机构级字段')
assert.ok(!/医院|卫生服务中心|institution|org/i.test(JSON.stringify(ps.body)), '接口不含机构级字段')
assert.equal((await api(w, '/province/summary?sort=orgName')).status, 400)
assert.equal((await api(w, '/county/overview')).status, 403)
const wm2 = await exportOnce(w, '各统筹区发布与监测汇总表')
step(`C2 接口无机构级字段;导出审批 → ${wm2}`)

// ================================================================ C3 外部监督（刘代表 · 市人大代表）
const s = await session('liudaibiao')
assert.match(s.url(), /\/c3$/)
assert.deepEqual(await navKeys(s), ['C3'])
await s.waitForSelector('[data-testid=c3-heading]')
const cd1 = (await s.textContent('[data-testid=c3-countdown]')).trim()
assert.match(cd1, /^(\d+ 天 )?\d{2}:\d{2}:\d{2}$/)
await s.waitForTimeout(1300)
const cd2 = (await s.textContent('[data-testid=c3-countdown]')).trim()
assert.notEqual(cd1, cd2, '倒计时应每秒刷新')
assert.match(await s.textContent('[data-testid=c3-banner]'), /截止 \d{4}-\d{2}-\d{2} 18:00.*不提供下载、打印、导出/)
step(`C3 剩余有效期倒计时每秒刷新(${cd1} → ${cd2})`)

assert.match(await s.textContent('[data-testid=c3-heading]'), /2026年前三季度 示例市医保基金运行情况/)
await s.click('[data-material="2"]')
await s.waitForSelector('[data-testid=c3-heading]:has-text("示例市医保局发布2026年前三季度医保数据")')
assert.equal(await s.getAttribute('[data-material="2"]', 'aria-pressed'), 'true')
await s.click('[data-material="3"]')
await s.waitForSelector('[data-testid=c3-heading]:has-text("统计公报")')
assert.match(await s.textContent('[data-testid=c3-preview]'), /只读预览/)
assert.match(await s.textContent('[data-testid=c3-paper-wm]'), /刘代表 外部监督 只读/)
assert.equal(await s.getAttribute('[data-testid=c3-preview]', 'data-paper'), '')
const ratio = await s.$eval('[data-testid=c3-preview]', (el) => el.clientWidth / el.clientHeight)
assert.ok(Math.abs(ratio - 1.6) < 0.02, `预览应为 16:10(实际 ${ratio.toFixed(3)})`)
step('C3 材料切换 → 16:10 只读预览,叠加水印')

await s.click('[data-testid=c3-play]')
await s.waitForSelector('[data-testid=c3-play]:has-text("正在回看")')
await s.click('[data-testid=c3-play]')
step('C3 发布会录像限时回看(在线播放,不可下载)')

assert.equal(await s.locator('[data-testid=export-btn]').count(), 0)
const prevented = await s.evaluate(() => {
  const ev = new MouseEvent('contextmenu', { bubbles: true, cancelable: true })
  document.querySelector('[data-testid=c3-preview]').dispatchEvent(ev)
  return ev.defaultPrevented
})
assert.ok(prevented, '右键应被禁用')
await toast(s, '只读席位不提供右键菜单')
await s.emulateMedia({ media: 'print' })
assert.equal(await s.isVisible('[data-testid=c3-root]'), false, '打印时隐藏内容')
await s.emulateMedia({ media: 'screen' })
assert.equal(await s.isVisible('[data-testid=c3-root]'), true)
step('C3 无导出入口;右键禁用;打印输出隐藏内容')

assert.equal((await api(s, '/county/overview')).status, 403)
assert.equal((await api(s, '/exports/options?scope=C2')).status, 403)
step('C3 越权访问县区 / 导出接口 → 403')

// 到期自动失效（前端）：快进浏览器时钟越过截止时间
const seatInfo = await api(s, '/supervision/seat')
const fx = await session('liudaibiao', { clock: true })
await fx.waitForSelector('[data-testid=c3-heading]')
await fx.clock.fastForward((seatInfo.body.remainingSeconds + 5) * 1000)
await fx.waitForSelector('[data-testid=c3-expired]', { timeout: 5000 })
assert.match(await fx.textContent('[data-testid=c3-expired]'), /席位已失效.*到期自动失效/)
assert.equal(await fx.locator('[data-testid=c3-heading]').count(), 0)
assert.ok(await fx.isDisabled('[data-material="1"]'))
step('C3 倒计时归零 → 席位自动失效,材料不再预览')

// 到期后服务端 403（可选：直接改库）
if (process.env.REGIONAL_DB) {
  const psql = (sql) =>
    execFileSync('psql', ['-h', 'localhost', '-U', 'dpub', '-d', process.env.REGIONAL_DB, '-qtAc', sql], {
      env: { ...process.env, PGPASSWORD: process.env.PGPASSWORD ?? 'dpub' },
    })
  const saved = psql('select valid_until from rg_seat limit 1').toString().trim()
  psql("update rg_seat set valid_until = now() - interval '1 minute'")
  try {
    const r = await api(s, '/supervision/materials/1')
    assert.equal(r.status, 403)
    assert.equal(r.body.code, 'SEAT_EXPIRED')
    await s.reload()
    await s.waitForSelector('[data-testid=c3-expired]:has-text("到期自动失效")')
    assert.equal(await s.locator('[data-testid=c3-heading]').count(), 0)
  } finally {
    psql(`update rg_seat set valid_until = '${saved}'`)
  }
  step('C3 服务端校验有效期:到期后接口 403 SEAT_EXPIRED,前端显示失效状态')
}

await browser.close()
assert.deepEqual(errors, [], '页面脚本错误:\n' + errors.join('\n'))
console.log('\n全部通过')
