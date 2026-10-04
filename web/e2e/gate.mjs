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

// ================================================================ 发布门禁
// 数据期次处于已发布状态时,机构 / 县区 / 省级可读;撤回批准后立即 409,内部全息图不受影响
const H = await session('limin') // 机构
const Q = await session('qianli') // 县区
const W = await session('wanglei') // 省级
const C = await session('ca', 'CONVENER')

const reads = async () => [
  (await api(H, 'GET', '/portal/holo')).status,
  (await api(H, 'GET', '/portal/groups')).status,
  (await api(Q, 'GET', '/county/overview')).status,
  (await api(W, 'GET', '/province/summary')).status,
]
assert.deepEqual(await reads(), [200, 200, 200, 200])
step('期次已发布:机构 / 县区 / 省级均可读')

// 先批准本期月度发布(成为现行版本),再发起撤回并走完审批
assert.equal((await api(C, 'POST', '/publish/flows/1/approve', { opinion: '同意发布' })).status, 200)
const created = await api(C, 'POST', '/publish/flows/1/corrections', { action: '撤回', reason: '数据口径发现错误,先行撤回' })
assert.equal(created.status, 200)
const cid = created.body.id
for (let i = 0; i < 8; i++) {
  const d = (await api(C, 'GET', `/publish/flows/${cid}`)).body
  if (d.flow.step >= d.flow.gateIdx) break
  assert.equal((await api(C, 'POST', `/publish/flows/${cid}/submit`)).status, 200)
}
assert.deepEqual(await reads(), [200, 200, 200, 200], '撤回未批准前外部仍可读')
assert.equal((await api(C, 'POST', `/publish/flows/${cid}/approve`, { opinion: '同意撤回' })).status, 200)
const after = await reads()
assert.deepEqual(after, [409, 409, 409, 409])
assert.match((await api(H, 'GET', '/portal/holo')).body.message, /已撤回/)
step('撤回批准即时生效:机构 / 县区 / 省级读取一律 409「数据已撤回」')

assert.equal((await api(C, 'GET', '/holo/overview?period=月')).status, 200)
step('分析监测区内部全息图不受影响')

await H.goto(BASE + '/b1')
await H.waitForSelector('text=已撤回')
step('机构端本院全息图提示数据已撤回')

assert.deepEqual(errors.filter((e) => !/409|Failed to load/.test(e)), [])
console.log('发布门禁 e2e 全部通过')
await browser.close()
