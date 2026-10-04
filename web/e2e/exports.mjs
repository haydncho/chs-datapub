// 流程闭环端到端（合并后）：node e2e/closure.mjs，需三端已启动且为全新种子库（见 docs/parallel-dev.md）。
//   闭环 1  A4 提交指标上线审批 → A8 召集人驳回(意见必填)→ A4 显示驳回意见并重新提交 → A8 批准 → 指标已上线 + A13 登记档位
//   闭环 2  A5 生成报告 → A8 提交 → 召集人批准 → 执行定向发布 → B4 机构签收 → 签收数回写 → 意见申诉 → 答复整改 → 归档
//   闭环 3  A6 生成提醒函 → A11 出现待发出触发记录 → 发出提醒函 → 登记机构回执 → A6 同步显示进度
//   补充    机构在 B5 提交预警回执(A11 同步);A8 撤回已发布版本 → B4 显示「已撤回」
import { chromium } from 'playwright'
import assert from 'node:assert/strict'

const BASE = process.env.BASE ?? 'http://localhost:5173'
const browser = await chromium.launch({ executablePath: process.env.CHROME || undefined })
const errors = []

async function session(who, identity) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
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
  const idStep = await p.waitForSelector('[data-testid=identity-step]', { timeout: 3000 }).catch(() => null)
  if (idStep) {
    if (identity) await p.click(`[data-identity=${identity}]`)
    await p.click('[data-testid=enter-btn]')
  }
  await p.waitForSelector('[data-testid=side-nav], [data-testid=m-sign]')
  return p
}

const step = (name) => console.log('✓', name)
const toast = (p, text) => p.waitForSelector(`[data-sonner-toast]:has-text("${text}")`, { timeout: 8000 })
const text = async (p, sel) => (await p.textContent(sel)).trim()
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

// ================================================================ 导出闭环
// 机构申请(本院全息图) → 召集人在「我的导出·待我审批」批准 → 机构下载(带水印、扣次数、留痕) → 次数用尽后不可再下
// 召集人自己的申请由行政管理组审批;自己审批自己 → 403;驳回须填意见
const H = await session('limin')
const C = await session('ca', 'CONVENER')
const L = await session('lihua')

await H.goto(BASE + '/b1')
await H.click('[data-testid=export-btn]')
await H.click('[data-testid=export-submit]')
const wm = (await text(H, '[data-testid=export-wm]'))
await H.click('text=查看我的导出')
await H.waitForSelector(`[data-export="${wm}"]`)
assert.match(await text(H, `[data-export="${wm}"]`), /待审批/)
assert.equal(await H.locator('[data-testid=download-btn]').count(), 0)
step('机构提交导出申请 → 我的导出显示「待审批」,不可下载')

const mine = (await api(H, 'GET', '/exports/mine')).body
const id = mine.find((x) => x.watermarkNo === wm).id
assert.equal((await api(H, 'POST', `/exports/${id}/approve`, {})).status, 403, '机构不能审批')
assert.equal((await api(L, 'POST', `/exports/${id}/approve`, {})).status, 403, '行政管理组不审批他人的申请')
assert.equal((await api(H, 'GET', `/exports/${id}/download`)).status, 409, '未批准不可下载')

await C.goto(BASE + '/e1')
await C.click('[role=tab]:has-text("待我审批")')
await C.waitForSelector(`[data-export="${wm}"]`)
await C.click(`[data-export="${wm}"] [data-testid=export-reject]`)
await toast(C, '驳回须填写意见')
await C.fill('[data-testid=export-opinion]', '本次不涉及对外材料')
await C.click(`[data-export="${wm}"] [data-testid=export-reject]`)
await toast(C, '已驳回导出申请')
await H.reload()
await H.waitForSelector(`[data-export="${wm}"]`)
assert.match(await text(H, `[data-export="${wm}"]`), /已驳回[\s\S]*本次不涉及对外材料/)
step('召集人驳回须填意见;机构看到「已驳回」及意见')

await H.goto(BASE + '/b1')
await H.click('[data-testid=export-btn]')
await H.click('[data-testid=export-submit]')
const wm2 = await text(H, '[data-testid=export-wm]')
await C.reload()
await C.click('[role=tab]:has-text("待我审批")')
await C.click(`[data-export="${wm2}"] [data-testid=export-approve]`)
await C.click('[data-testid=confirm-ok]')
await toast(C, '已批准导出申请')
await H.goto(BASE + '/e1')
await H.waitForSelector(`[data-export="${wm2}"] [data-testid=download-btn]`)
const [dl] = await Promise.all([H.waitForEvent('download'), H.click(`[data-export="${wm2}"] [data-testid=download-btn]`)])
assert.equal(dl.suggestedFilename(), `${wm2}.csv`)
await toast(H, '已带实名水印并留痕')
await H.reload()
await H.waitForSelector(`[data-export="${wm2}"]`)
assert.equal(await H.locator(`[data-export="${wm2}"] [data-testid=download-btn]`).count(), 0, '1 次下载用尽后不再显示下载')
assert.match(await text(H, `[data-export="${wm2}"]`), /已用尽/)
assert.equal((await api(H, 'GET', `/exports/${(await api(H, 'GET', '/exports/mine')).body.find((x) => x.watermarkNo === wm2).id}/download`)).status, 409)
step('批准后下载 1 次(文件名=水印编号),次数用尽即「已用尽」,再次下载 409')

// 召集人自己的申请:不在自己的待审批里,行政管理组审批
await C.goto(BASE + '/a2')
await C.click('[data-testid=export-btn]')
await C.click('[data-testid=export-submit]')
const wm3 = await text(C, '[data-testid=export-wm]')
const cid = (await api(C, 'GET', '/exports/mine')).body.find((x) => x.watermarkNo === wm3).id
assert.equal((await api(C, 'POST', `/exports/${cid}/approve`, {})).status, 403, '不能批准自己的申请')
assert.equal((await api(C, 'GET', '/exports/pending')).body.some((x) => x.watermarkNo === wm3), false)
await L.goto(BASE + '/e1')
await L.click('[role=tab]:has-text("待我审批")')
await L.click(`[data-export="${wm3}"] [data-testid=export-approve]`)
await L.click('[data-testid=confirm-ok]')
await toast(L, '已批准导出申请')
step('召集人自己的申请由行政管理组审批,本人不能批准自己的')

// 审计:导出审批与下载均留痕
const A = await session('suntao')
const logs = (await api(A, 'GET', '/audit/logs?type=%E5%AF%BC%E5%87%BA&page=1&size=20')).body
assert.ok(logs.rows.some((r) => r.object.includes('下载')), '下载写入审计「导出」')
step('审计日志含导出下载记录')

assert.deepEqual(errors, [])
console.log('导出闭环 e2e 全部通过')
await browser.close()
