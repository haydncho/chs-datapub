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
// SHOTS=1 时在关键画面截图(e2e/out/closure-*.png),便于目视检查
const shot = (p, n) => (process.env.SHOTS ? p.screenshot({ path: `e2e/out/closure-${n}.png` }) : undefined)
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

const todoId = async (p, group, part) => {
  const r = await api(p, 'GET', '/publish/todos')
  return r.body.find((g) => g.group === group).items.find((i) => i.name.includes(part)).id
}

const L = await session('lihua') // 行政管理组:可提交,不可审批
const C = await session('ca', 'CONVENER') // 召集人

// ================================================================ 闭环 1:指标上线审批
const name = `闭环指标${Date.now() % 100000}`
await L.goto(BASE + '/a4')
await L.click('[data-testid=new-ind]')
await L.waitForSelector('[data-testid=formula-input]')
const f0 = await L.inputValue('[data-testid=formula-input]')
await L.fill('[data-testid=formula-input]', f0.replace(/^\S+ =/, `${name} =`))
await L.waitForSelector('[data-testid=formula-status][data-ok=true]')
await L.waitForFunction((n) => document.querySelector('[data-testid=wiz-title]')?.textContent?.includes(n), name)
await L.click('[data-step="4"]')
await L.click('[data-testid=wiz-submit]')
await toast(L, '已提交上线审批 · 审批单 ZB-')
const no1 = await text(L, '[data-testid=approval-no]')
step(`A4 提交上线审批 ${no1}`)

// A8:待办出现;行政管理组不能审批(403 + 提示)
await L.goto(BASE + '/a8')
await L.click(`[data-group=指标上线审批] [data-todo="${name} 上线"]`)
await L.waitForSelector('[data-testid=indicator-approval]')
assert.equal(await text(L, '[data-testid=ind-approval-no]'), no1)
await shot(L, 'indicator-approval')
assert.equal(await L.isDisabled('[data-testid=ind-approve]'), true)
assert.equal(await L.isDisabled('[data-testid=ind-reject]'), true)
step('指标上线审批待办;行政管理组批准 / 驳回按钮置灰')

// 召集人:驳回意见必填 → 驳回
await C.goto(BASE + '/a8')
await C.click(`[data-todo="${name} 上线"]`)
await C.waitForSelector('[data-testid=indicator-approval]')
await C.click('[data-testid=ind-reject]')
await C.waitForSelector('text=驳回须填写意见')
const rid = await todoId(C, '指标上线审批', name)
const noOpinion = await api(C, 'POST', `/publish/indicator-requests/${rid}/reject`, {})
assert.equal(noOpinion.status, 400)
assert.equal(noOpinion.body.message, '请填写驳回意见')
await C.fill('[data-testid=ind-opinion]', '公式口径需补充说明,请完善后重新提交')
await C.click('[data-testid=ind-reject]')
await toast(C, '已驳回')
await C.waitForSelector(`[data-todo="${name} 上线"]`, { state: 'detached' })
step('召集人驳回:意见必填,待办移除')

// A4:草稿解锁并显示驳回意见,修改后重新提交(新审批单号)
await L.goto(BASE + '/a4')
await L.click('[data-testid=new-ind]')
await L.waitForSelector('[data-testid=rejected-banner]')
assert.match(await text(L, '[data-testid=rejected-banner]'), new RegExp(`${no1} 已被.*驳回.*公式口径需补充说明`))
await L.click('[data-step="4"]')
await L.click('[data-testid=wiz-submit]')
await toast(L, '已提交上线审批 · 审批单 ZB-')
const no2 = await text(L, '[data-testid=approval-no]')
assert.notEqual(no2, no1, '重新提交生成新审批单号')
step(`A4 显示驳回意见,修改后重新提交 → ${no2}`)

// 召集人批准 → 已上线 + A13 登记档位
await C.goto(BASE + '/a8')
await C.click(`[data-todo="${name} 上线"]`)
await C.waitForSelector('[data-testid=indicator-approval]')
assert.equal(await text(C, '[data-testid=ind-approval-no]'), no2)
await C.click('[data-testid=ind-approve]')
await C.click('[data-testid=confirm-ok]')
await toast(C, '已上线')
await L.goto(BASE + '/a4')
await L.fill('[data-testid=ind-search]', name)
await L.waitForSelector(`tr[data-ind="${name}"]`)
assert.match(await text(L, `tr[data-ind="${name}"]`), /已上线/)
await C.goto(BASE + '/a13')
await C.waitForSelector(`[data-tier="${name}"] [aria-checked=true]`)
assert.equal(await text(C, `[data-tier="${name}"] [aria-checked=true]`), '匿名分位')
step('召集人批准:A4 列表「已上线」,A13 对标档位表登记(匿名分位)')

// ================================================================ 闭环 2:发布 → 签收 → 归档
await L.goto(BASE + '/a5')
await L.click('[data-testid=gen-report]')
await toast(L, '进入发布工作流第 3 步')
const draftTodo = '[data-group=月告知] [data-todo*="草稿"]'
const openDraftFlow = async (p) => {
  await p.goto(BASE + '/a8')
  await p.click(draftTodo)
  await p.waitForSelector('[data-testid=flow-name]')
  await p.click('[data-testid=a8-tabs] button:has-text("审批与留痕")')
}
await openDraftFlow(L)
await L.click('[data-testid=submit-btn]')
await toast(L, '已提交至第 4 步')
await L.click('[data-testid=submit-btn]')
await toast(L, '已提交召集人审批')
step('A5 报告草稿进入 A8,经办提交至第 5 步')

await openDraftFlow(C)
await C.fill('[data-testid=approval-opinion]', '同意发布')
await C.click('[data-testid=approve-btn]')
await C.click('[data-testid=confirm-ok]')
await toast(C, '已批准')
step('召集人批准发布 → 第 6 步')

await openDraftFlow(L)
assert.match(await text(L, '[data-testid=advance-hint]'), /生成发布报告/)
await L.click('[data-testid=advance-btn]')
await toast(L, '已进入第 7 步')
assert.match(await text(L, '[data-testid=sign-stats]'), /^签收 0\/\d+$/)
const total = Number((await text(L, '[data-testid=sign-stats]')).split('/')[1])
await shot(L, 'advance-signing')
step(`执行定向发布:按范围向 ${total} 家机构生成发布报告,签收 0/${total}`)

// 机构(B4)签收
const P = await session('limin')
await P.goto(BASE + '/b4')
const rpt = P.locator('[data-report]', { hasText: '2026年8月 月度运行报告' }).first()
await rpt.waitFor()
await rpt.click()
await P.waitForSelector('[data-testid=sign-btn]')
await P.click('[data-testid=sign-btn]')
await toast(P, '已签收')
step('机构在 B4 看到新发布报告并签收')

await openDraftFlow(L)
assert.match(await text(L, '[data-testid=sign-stats]'), new RegExp(`^签收 1/${total}$`))
await L.click('[data-testid=advance-btn]')
await toast(L, '已进入第 8 步')
step(`A8「签收查阅」读到签收 1/${total},结束签收期 → 意见申诉`)

// 意见申诉期:机构提交意见(进入 A10);答复整改阶段未答复不能归档
const sub = await api(P, 'POST', '/portal/opinions', { refId: 1, category: '数据异议', text: '发布报告中本院病例数与台账不一致,请核实' })
assert.equal(sub.status, 200, JSON.stringify(sub.body))
const ticket = sub.body.no
await openDraftFlow(L)
assert.match(await text(L, '[data-testid=advance-hint]'), /待答复 1 条/)
await L.click('[data-testid=advance-btn]')
await toast(L, '已进入第 9 步')
assert.equal(await L.isDisabled('[data-testid=advance-btn]'), true)
assert.match(await text(L, '[data-testid=advance-blocked]'), /还有 1 条意见未答复/)
await shot(L, 'advance-blocked')
const fid = await todoId(L, '月告知', '草稿')
const early = await api(L, 'POST', `/publish/flows/${fid}/advance`)
assert.equal(early.status, 409)
assert.match(early.body.message, /还有 1 条意见未答复/)
step(`机构提交意见 ${ticket};答复整改阶段有未答复意见 → 不能归档`)

await L.goto(BASE + '/a10')
await L.click(`[data-ticket=${ticket}]`)
await L.fill('[data-testid=reply-input]', '已核实,将在更正版本中调整')
await L.click('[data-testid=reply-btn]')
await toast(L, '已答复')
await openDraftFlow(L)
await L.waitForFunction(() => !document.querySelector('[data-testid=advance-btn]')?.disabled)
await L.click('[data-testid=advance-btn]')
await toast(L, '已归档')
await L.goto(BASE + '/a8')
assert.match(await text(L, `${draftTodo} [data-testid=todo-status]`), /已归档/)
step('A10 答复后可归档;A8 待办显示「已归档」')

// ================================================================ 闭环 3:A6 提醒函 → A11
await L.goto(BASE + '/a6')
await L.click('[data-tab=anom]')
await L.waitForSelector('[data-anomaly]')
await L.click('[data-anomaly="5"] [data-act=letter]')
await toast(L, '进入预警与整改(待发出)')
assert.match(await text(L, '[data-anomaly="5"] [data-testid=alert-status]'), /预警与整改 · 待发出/)
await shot(L, 'a6-letter')
await L.goto(BASE + '/a11')
const row = L.locator('[data-testid=triggers] tbody tr', { hasText: '丙区人民医院' }).first()
await row.waitFor()
assert.match(await row.textContent(), /待发出/)
await row.click()
assert.match(await text(L, '[data-testid=letter]'), /丙区人民医院/)
await L.click('[data-testid=send-letter]')
await L.click('[data-testid=confirm-ok]')
await toast(L, '提醒函已发出')
await L.fill('[data-testid=receipt-text-a11]', '电话回执:已组织科室分析并整改')
await L.click('[data-testid=receipt-btn]')
await toast(L, '的回执')
await L.goto(BASE + '/a6')
await L.click('[data-tab=anom]')
await L.waitForSelector('[data-anomaly="5"] [data-testid=alert-status]')
assert.match(await text(L, '[data-anomaly="5"] [data-testid=alert-status]'), /预警与整改 · 已回执/)
// 已有触发记录的异常:关联而不重复建
await L.click('[data-anomaly="1"] [data-act=letter]')
await toast(L, '关联预警与整改已有触发记录')
step('A6 生成提醒函 → A11 待发出 → 发出 → 回执;A6 同步显示进度;已有记录不重复建')

// ================================================================ 补充 1:机构端提交预警回执(B5)
await P.goto(BASE + '/b5')
await P.waitForSelector('[data-testid=alert-letters]')
const letterRow = P.locator('[data-letter]').first()
assert.match(await letterRow.textContent(), /医保外费用占比 · 同级分位/)
assert.equal((await letterRow.locator('[data-testid=letter-status]').textContent()).trim(), '待回执')
await P.click('[data-testid=receipt-submit]')
await toast(P, '请填写回执说明')
const lid = Number(await letterRow.getAttribute('data-letter'))
assert.equal((await api(P, 'POST', `/portal/alerts/${lid}/receipt`, { text: '  ' })).status, 400, '服务端同样校验必填')
await P.fill('[data-testid=receipt-text]', '已组织医保办与药学部分析,主要原因为自费耗材使用增加,下月起执行耗材使用审批。')
await P.click('[data-testid=receipt-submit]')
await toast(P, '回执已提交')
assert.equal((await letterRow.locator('[data-testid=letter-status]').textContent()).trim(), '已回执')
assert.equal((await api(P, 'POST', `/portal/alerts/${lid}/receipt`, { text: '重复' })).status, 409, '已回执不能重复提交')
await L.goto(BASE + '/a11')
const arow = L.locator('[data-testid=triggers] tbody tr', { hasText: '示例市第一人民医院' }).first()
await arow.click()
assert.match(await arow.textContent(), /已回执/)
await L.waitForSelector('text=回执摘要:已组织医保办与药学部分析')
step('机构在 B5 提交预警回执(必填、不可重复),A11 同步显示已回执与回执摘要')

// ================================================================ 补充 2:撤回 → B4 显示「已撤回」
const corr = await api(L, 'POST', `/publish/flows/${fid}/corrections`, { action: '撤回', reason: '发布包中指标口径有误,撤回后重发' })
assert.equal(corr.status, 200, JSON.stringify(corr.body))
const wid = corr.body.id
assert.equal((await api(L, 'POST', `/publish/flows/${wid}/submit`)).status, 200)
assert.equal((await api(L, 'POST', `/publish/flows/${wid}/submit`)).status, 200)
assert.equal((await api(L, 'POST', `/publish/flows/${wid}/approve`, { opinion: '同意' })).status, 403, '行政管理组不能批准撤回')
assert.equal((await api(C, 'POST', `/publish/flows/${wid}/approve`, { opinion: '同意撤回' })).status, 200)
assert.equal((await api(L, 'POST', `/publish/flows/${wid}/advance`)).status, 200)
await P.goto(BASE + '/b4')
const wrow = P.locator('[data-report]', { hasText: '2026年8月 月度运行报告' }).first()
await wrow.waitFor()
assert.equal(await wrow.getAttribute('data-status'), 'WITHDRAWN')
await wrow.click()
await P.waitForSelector('[data-testid=corrected-ribbon]')
assert.equal((await text(P, '[data-testid=corrected-ribbon]')), '已撤回')
assert.equal(await P.locator('[data-testid=sign-btn]').count(), 0, '已撤回的报告不再需要签收')
step('A8 撤回已发布版本:B4 原报告显示「已撤回」,只读保留,不再需要签收')

await browser.close()
assert.deepEqual(errors, [], '页面脚本错误:\n' + errors.join('\n'))
console.log('\n闭环 e2e 全部通过')
