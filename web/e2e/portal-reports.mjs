// 端到端（portal-reports 组：B4 报告中心 / B5 意见与核对 / B6 政策与培训 / D1 移动端）
// 前置：三端已启动且为种子数据（演示模式）。用法：BASE=http://localhost:5205 CHROME=/opt/pw-browsers/chromium node e2e/portal-reports.mjs
// SHOT=1 时另存截图到 e2e/out/（浅色 + 深色，含 D1 390×844）。
import { chromium } from 'playwright'
import assert from 'node:assert/strict'
import { mkdirSync } from 'node:fs'

const BASE = process.env.BASE ?? 'http://localhost:5173'
const SHOT = !!process.env.SHOT
if (SHOT) mkdirSync('e2e/out', { recursive: true })
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined })
const errors = []
const step = (name) => console.log('✓', name)
const toast = async (p, text) => p.waitForSelector(`[data-sonner-toast]:has-text("${text}")`, { timeout: 5000 })

async function login(ctx, who) {
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

/** 以页面会话令牌直接调用接口（验证服务端校验与裁剪）。 */
const api = (p, method, path, body) =>
  p.evaluate(
    async ([m, u, b]) => {
      const r = await fetch('/api/v1' + u, {
        method: m,
        headers: { Authorization: `Bearer ${localStorage.getItem('dpub.token')}`, 'Content-Type': 'application/json' },
        body: b === undefined ? undefined : JSON.stringify(b),
      })
      return { status: r.status, body: await r.json().catch(() => null) }
    },
    [method, path, body],
  )

const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const p = await login(ctx, 'limin')

// ---------------------------------------------------------------- D1 移动端（先看待签收卡）
// 政务 APP 内嵌：390×844 触屏视口（覆盖式滚动条），沿用同一会话令牌
const token = await p.evaluate(() => localStorage.getItem('dpub.token'))
const mctx = await browser.newContext({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 })
await mctx.addInitScript((tk) => localStorage.setItem('dpub.token', tk), token)
const m = await mctx.newPage()
m.on('pageerror', (e) => errors.push(`m: ${e.message}`))
await m.goto(BASE + '/m')
await m.waitForSelector('[data-testid=m-pending]')
assert.match(await m.textContent('[data-testid=m-pending]'), /2026年8月 DRG月度运行报告/)
const signBox = await m.locator('[data-testid=m-sign]').boundingBox()
assert.ok(signBox.height >= 44, '签收按钮高度 ≥ 44px')
assert.equal(await m.locator('[data-testid=side-nav]').count(), 0, '移动端不在 PC 外壳内')
step('D1 /m 独立布局:待签收卡(签收按钮 ≥ 44px)')

// ---------------------------------------------------------------- B4 报告中心
await p.goto(BASE + '/b4')
await p.waitForSelector('[data-testid=report-paper]')
assert.match(await p.textContent('[data-testid=paper-header]'), /定向发布 · 仅限示例市第一人民医院/)
assert.match(await p.getAttribute('[data-testid=paper-watermark]', 'data-text'), /^李敏 示例市第一人民医院 WM-\d{8}-\d{4}$/)
assert.equal(await p.$eval('[data-testid=paper-watermark]', (e) => getComputedStyle(e).opacity), '0.12')
assert.equal(await p.locator('[data-testid=report-paper][data-paper]').count(), 1)
const reports = await api(p, 'GET', '/portal/reports')
assert.equal(reports.body.length, 5, '只返回本院 5 份报告')
assert.equal((await api(p, 'GET', '/portal/reports/6')).status, 404, '他院报告不可见')
await p.click('[data-testid=sign-btn]')
await toast(p, '已签收')
await p.waitForSelector('[data-testid=sign-note]:has-text("已签收")')
await p.waitForSelector('[data-report="1"][data-status=SIGNED]')
await p.click('[data-status=OLD]')
await p.waitForSelector('[data-testid=corrected-ribbon]')
assert.match(await p.textContent('[data-testid=sign-note]'), /原版本只读保留/)
await p.click('[data-tab=体检报告]')
assert.equal(await p.locator('[data-report]').count(), 1)
await p.click('[data-tab=专题报告]')
await p.click('[data-status=CHECK]')
await p.waitForSelector('[data-testid=sign-note]:has-text("核对稿")')
await p.reload()
await p.waitForSelector('[data-report="1"][data-status=SIGNED]')
const audit = await api(p, 'POST', '/portal/reports/1/sign')
assert.equal(audit.status, 409, '重复签收 409')
step('B4 预览:页眉仅限本院、斜铺实名水印 0.12、已更正丝带;签收 → 已签收(持久化)')

// ---------------------------------------------------------------- B5 核对与意见
await p.goto(BASE + '/b5')
await p.waitForSelector('[data-testid=check-card]')
const cd1 = await p.textContent('[data-testid=countdown]')
await p.waitForTimeout(1300)
const cd2 = await p.textContent('[data-testid=countdown]')
assert.notEqual(cd1, cd2, '倒计时每秒刷新')
assert.match(cd2, /\d{2}:\d{2}:\d{2}/)
// 表单校验：未选关联对象 / 未填说明时按钮置灰并提示
assert.ok(await p.isDisabled('[data-testid=opinion-submit]'))
assert.equal((await p.textContent('[data-testid=opinion-hint]')).trim(), '必须关联具体指标或报告段落')
await p.click('[data-ref="指标:14天再住院率"]')
assert.equal((await p.textContent('[data-testid=opinion-hint]')).trim(), '请填写说明')
assert.ok(await p.isDisabled('[data-testid=opinion-submit]'))
// 服务端同样校验
let r = await api(p, 'POST', '/portal/opinions', { text: '无关联对象' })
assert.equal(r.status, 400)
assert.equal(r.body.message, '必须关联具体指标或报告段落')
r = await api(p, 'POST', '/portal/opinions', { refId: 1, text: '  ' })
assert.equal(r.body.message, '请填写说明')
// 核对项：确认 / 有异议（持久化）
await p.click('[data-check="0"] [data-act=ok]')
await p.waitForSelector('[data-check="0"][data-state=OK]:has-text("已确认")')
await p.click('[data-check="1"] [data-act=object]')
await toast(p, '请在下方补充异议说明')
await p.waitForSelector('[data-check="1"][data-state=OBJECT]')
await p.waitForSelector('text=关联核对项:BR25 例均基金差额')
await p.fill('[data-testid=opinion-text]', '本院 BR25 例均基金差额含 12 例已批复特例单议病例,请剔除后重算。')
assert.ok(!(await p.isDisabled('[data-testid=opinion-submit]')))
await p.click('[data-testid=opinion-submit]')
const t = await toast(p, '已提交核对期异议')
const no = (await t.textContent()).match(/YJ-\d{4}-\d{3}/)[0]
await p.waitForSelector(`[data-check="1"]:has-text("${no}")`)
await p.waitForSelector(`[data-opinion="${no}"]:has-text("待答复")`)
// 一般意见：类别芯片
await p.click('[data-ref="指标:14天再住院率"]')
await p.click('[data-testid=cats] button:has-text("咨询")')
await p.fill('[data-testid=opinion-text]', '14天再住院率是否剔除计划性返院?')
await p.click('[data-testid=opinion-submit]')
const t2 = await toast(p, '承办时限 10 个工作日')
const no2 = (await t2.textContent()).match(/YJ-\d{4}-\d{3}/)[0]
await p.waitForSelector(`[data-opinion="${no2}"]:has-text("咨询")`)
// 五星评价（已答复工单）→ 写回 opinion_ticket.rating
await p.click('[data-opinion="YJ-0906-012"] [data-star="4"]')
await toast(p, '感谢评价')
await p.reload()
await p.waitForSelector('[data-opinion="YJ-0906-012"] [data-star]')
assert.equal(await p.locator('[data-opinion="YJ-0906-012"] [data-star].text-chart-amber').count(), 4)
assert.equal((await api(p, 'PUT', '/portal/opinions/YJ-0912-031/rating', { rating: 5 })).status, 409, '未答复不能评价')
await p.reload()
await p.waitForSelector('[data-check="0"][data-state=OK]')
step(`B5 倒计时每秒刷新;关联对象必选 + 说明必填(前后端);确认 / 有异议持久化;提交 ${no}(核对期异议)、${no2}(咨询);五星评价`)

// 医保局 A10 能看到机构提交的工单与评价（写入第二批 opinion_ticket）
const ctxA = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const a = await login(ctxA, 'lihua')
await a.goto(BASE + '/a10')
await a.waitForSelector(`[data-ticket="${no}"]:has-text("核对期异议")`)
assert.match(await a.textContent(`[data-ticket="${no}"]`), /示例市第一人民医院.*周婷/)
await a.click('[data-ticket="YJ-0906-012"]')
await a.waitForSelector('[data-testid=ticket-detail]:has-text("★★★★☆")')
await ctxA.close()
step('A10(lihua)可见机构工单(核对期异议 · 周婷)与机构评价')

// ---------------------------------------------------------------- B6 政策与培训
await p.goto(BASE + '/b6')
await p.waitForSelector('[data-testid=docs] [data-doc]')
assert.equal(await p.locator('[data-doc]').count(), 6)
await p.click('[data-cat=特例单议]')
await p.waitForFunction(() => document.querySelectorAll('[data-doc]').length === 2)
await p.click('button:has-text("全部")')
await p.fill('[data-testid=doc-search]', '点值')
await p.waitForFunction(() => document.querySelectorAll('[data-doc]').length === 1)
await p.click('[data-doc]')
await toast(p, '只读查看器')
assert.equal(await p.locator('[data-course] [role=progressbar]').count(), 3)
const quiz = await api(p, 'GET', '/portal/policy/quiz')
assert.ok(!('answer' in quiz.body.quiz) && !quiz.body.quiz.answered, '作答前不下发正确答案')
await p.click('[data-testid=quiz] [data-opt="0"]')
await p.waitForSelector('[data-testid=quiz-result]:has-text("回答错误,正确答案为 B")')
assert.equal(await p.getAttribute('[data-opt="0"]', 'data-result'), 'wrong')
assert.equal(await p.getAttribute('[data-opt="1"]', 'data-result'), 'right')
assert.match(await p.$eval('[data-opt="1"]', (e) => e.className), /bg-success-soft/)
assert.match(await p.$eval('[data-opt="0"]', (e) => e.className), /bg-danger-soft/)
assert.equal((await api(p, 'POST', '/portal/policy/quiz/1/answer', { picked: 1 })).status, 409)
step('B6 文件按类别 / 关键词检索、只读打开;课件进度条;随堂题即时判分(错误红、正确绿,服务端判分)')

// ---------------------------------------------------------------- D1 移动端：已签收（与 B4 同一数据）、预警详情、水印、点击目标
await m.goto(BASE + '/m')
await m.waitForSelector('[data-testid=m-signed]:has-text("2026年8月 DRG月度运行报告")')
assert.equal(await m.locator('[data-testid=m-pending]').count(), 0)
assert.equal(await m.locator('[data-testid=m-kpis] .grid > div').count(), 4)
assert.equal(await m.locator('[data-testid=m-groups] .text-primary').count(), 3)
await m.waitForSelector('[data-testid=m-check]:has-text("PC 端")')
assert.match(await m.textContent('[data-testid=m-security]'), /禁止截图外传/)
const body = await m.textContent('body')
assert.ok(!/分享|复制链接|二维码/.test(body), '无分享 / 复制链接 / 二维码入口')
const wm = await m.$eval('[data-testid=mobile-watermark]', (e) => ({
  op: getComputedStyle(e).opacity,
  cols: getComputedStyle(e).gridTemplateColumns.split(' ').length,
  row: getComputedStyle(e).gridAutoRows,
  text: e.dataset.text,
  w: e.getBoundingClientRect().width,
  h: e.getBoundingClientRect().height,
}))
assert.deepEqual([wm.op, wm.cols, wm.row], ['0.1', 2, '90px'])
assert.match(wm.text, /^李敏 第一人民医院 \d{2}-\d{2}$/)
assert.ok(wm.w === 390 && wm.h >= 844, `满屏水印 ${wm.w}×${wm.h}`)
assert.ok((await m.evaluate(() => document.documentElement.scrollWidth)) <= 390, '无横向滚动')
const small = await m.$$eval('[data-testid=mobile] button', (els) => els.filter((e) => e.getBoundingClientRect().height < 44).map((e) => e.textContent))
assert.deepEqual(small, [], '所有点击目标 ≥ 44px')
if (SHOT) await m.screenshot({ path: 'e2e/out/D1-light.png', fullPage: true })
await m.click('[data-alert]')
await m.waitForSelector('[data-testid=m-alert]:has-text("医保外费用占比偏高")')
assert.match(await m.textContent('[data-testid=m-alert]'), /6\.8%.*同级 P72 · 关注线 P70/)
const back = await m.locator('[data-testid=m-back]').boundingBox()
assert.ok(back.width >= 44 && back.height >= 44)
if (SHOT) await m.screenshot({ path: 'e2e/out/D1-alert-light.png', fullPage: true })
assert.equal((await api(m, 'GET', '/portal/mobile/alerts/1')).status, 404, '他院预警不可见')
await m.click('[data-testid=m-back]')
await m.waitForSelector('[data-testid=m-kpis]')
step('D1 已签收(与 B4 同一数据);预警卡 → 详情 → 返回;4 KPI、重点病组、核对引导;满屏水印 2 列 × 90px / 0.1;点击目标 ≥ 44px')

// ---------------------------------------------------------------- 截图（浅色 + 深色）
if (SHOT) {
  for (const theme of ['light', 'dark']) {
    await p.evaluate((t) => localStorage.setItem('dpub.theme', t), theme)
    for (const pg of ['B4', 'B5', 'B6']) {
      await p.goto(`${BASE}/${pg.toLowerCase()}`)
      await p.waitForTimeout(900)
      if (pg === 'B4') {
        await p.click('[data-status=OLD]')
        await p.waitForSelector('[data-testid=corrected-ribbon]')
      }
      await p.screenshot({ path: `e2e/out/${pg}-${theme}.png` })
    }
    await m.evaluate((t) => localStorage.setItem('dpub.theme', t), theme)
    await m.goto(BASE + '/m')
    await m.waitForSelector('[data-testid=m-kpis]')
    await m.screenshot({ path: `e2e/out/D1-${theme}.png`, fullPage: true })
  }
  await p.evaluate(() => localStorage.setItem('dpub.theme', 'light'))
  console.log('截图已保存到 e2e/out/')
}

await browser.close()
assert.deepEqual(errors, [], '页面脚本错误:\n' + errors.join('\n'))
console.log('\n全部通过')
