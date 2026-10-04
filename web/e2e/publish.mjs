// A8 发布工作流端到端（演示模式、种子数据）：BASE=http://localhost:5203 CHROME=/opt/pw-browsers/chromium node e2e/publish.mjs
// 需三端已启动且数据库为新灌入的种子数据（重置见 docs/parallel-dev.md）。
// 覆盖：行政管理组可提交、点批准被拒（403）；召集人调整定向范围（引擎实时覆盖数）→ 驳回不填意见被拦 → 批准发布；
// A5 报告草稿进入「月告知」待办；提交至召集人审批后驳回至「分析成稿」；发起更正；批准档位切换后 A13 档位变化。
import { chromium } from 'playwright'
import assert from 'node:assert/strict'

const BASE = process.env.BASE ?? 'http://localhost:5203'
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
  await p.waitForSelector('[data-testid=side-nav]')
  return p
}

const toast = async (p, text) => p.waitForSelector(`[data-sonner-toast]:has-text("${text}")`, { timeout: 8000 })
const step = (name) => console.log('✓', name)
const text = async (p, sel) => (await p.textContent(sel)).trim()
const tab = (p, name) => p.click(`[data-testid=a8-tabs] button:has-text("${name}")`)
const todo = (p, name) => p.click(`[data-todo="${name}"]`)
const flowIs = (p, name) => p.waitForSelector(`[data-flow="${name}"]`)
const stepIs = (p, label) => p.waitForSelector(`[data-testid=flow-step]:has-text("当前:${label}")`)
const api = (p, method, path, body) =>
  p.evaluate(
    async ([m, u, b]) => {
      const r = await fetch('/api/v1' + u, {
        method: m,
        headers: { Authorization: `Bearer ${localStorage.getItem('dpub.token')}`, 'Content-Type': 'application/json' },
        body: b ? JSON.stringify(b) : undefined,
      })
      return { status: r.status, body: await r.json().catch(() => null) }
    },
    [method, path, body],
  )

const M8 = '2026年8月 DRG月度运行告知'

// ---------------------------------------------------------------- 行政管理组：可提交，不可审批
const l = await session('lihua')
await l.goto(BASE + '/a8')
await flowIs(l, M8)
await stepIs(l, '第 5 步 · 召集人审批')
assert.equal(await l.locator('[data-testid=step-bar] li').count(), 10)
assert.equal(await text(l, '[data-testid=gate-badge]'), '未批准不外发')
assert.equal(await l.getAttribute('[data-step="5"]', 'data-state'), 'on')
await tab(l, '审批与留痕')
await l.waitForSelector('[data-testid=role-hint]')
await l.fill('[data-testid=approval-opinion]', '同意')
await l.click('[data-testid=approve-btn]')
await toast(l, '仅召集人可审批')
await stepIs(l, '第 5 步 · 召集人审批')
assert.equal((await api(l, 'POST', '/publish/tier-requests/1/approve', {})).status, 403)
step('行政管理组 lihua:点「批准发布」被拒(403),仍在第 5 步;档位切换审批同样被拒')

await todo(l, '2026年8月 运行预警汇总')
await flowIs(l, '2026年8月 运行预警汇总')
await l.click('[data-testid=submit-btn]')
await toast(l, '已提交至第 4 步')
await stepIs(l, '第 4 步 · 专家组审核')
await l.waitForSelector('[data-todo="2026年8月 运行预警汇总"]:has-text("第4步 · 专家组审核")')
step('行政管理组可提交:运行预警汇总 第 3 步 → 第 4 步')

// ---------------------------------------------------------------- 召集人
const c = await session('ca', 'CONVENER')

// A5 报告草稿 → A8「月告知」待办（第 3 步）
await c.goto(BASE + '/a5')
await c.click('[data-testid=gen-report]')
await toast(c, '进入发布工作流第 3 步')
await c.goto(BASE + '/a8')
await flowIs(c, M8)
const draft = c.locator('[data-group="月告知"] [data-todo*="草稿"]')
assert.equal(await draft.count(), 1)
assert.match(await draft.textContent(), /第3步 · 分析成稿/)
assert.match(await draft.textContent(), /报告草稿/)
step('A5 生成的报告草稿作为「月告知」待办出现在第 3 步')

// 发布包与定向范围：仅内部项自动排除；定向范围实时覆盖（引擎）
assert.match(await text(c, '[data-testid=pkg-excluded]'), /已自动排除 2 项.*单病例费用明细、参保人就医轨迹/)
assert.equal(await text(c, '[data-testid=coverage-count]'), '52')
await c.click('[data-dim=tiers] [data-opt=一级]')
await c.waitForSelector('[data-testid=coverage-count]:text-is("30")')
await c.click('[data-dim=group] [data-opt=收治GG19]')
await c.waitForSelector('[data-testid=coverage-count]:text-is("7")')
assert.match(await text(c, '[data-testid=coverage-names]'), /某肛肠专科医院/)
await c.click('[data-dim=alliance] [data-opt=甲县医共体]')
await c.waitForSelector('[data-testid=coverage-count]:text-is("0")')
await c.click('[data-dim=alliance] [data-opt=不限]')
await c.click('[data-dim=group] [data-opt=不限]')
await c.waitForSelector('[data-testid=coverage-count]:text-is("30")')
await c.click('[data-testid=toggle-names]')
assert.equal(await c.locator('[data-testid=coverage-names] span').count(), 30)
step('定向范围:去掉一级 → 30 家;收治GG19 → 7 家;叠加甲县医共体 → 0 家;覆盖数与名单由引擎实时计算')

await tab(c, '分受众版本预览')
assert.equal(await c.locator('[data-testid=audience-versions] [data-version]').count(), 3)
await c.waitForSelector('[data-version=甲县医保局版]:has-text("其他县区汇总")')
step('分受众版本三列并排预览')

// 审批：驳回意见必填；批准 → 第 6 步并追加留痕
await tab(c, '审批与留痕')
await c.click('[data-testid=reject-btn]')
await c.waitForSelector('[data-testid=opinion-required]')
await stepIs(c, '第 5 步 · 召集人审批')
const empty = await api(c, 'POST', '/publish/flows/1/reject', { opinion: '  ' })
assert.equal(empty.status, 400)
assert.equal(empty.body.message, '请填写驳回意见')
step('驳回不填意见被拦(界面提示 + 服务端 400)')

await c.fill('[data-testid=approval-opinion]', '同意发布')
await c.click('[data-testid=approve-btn]')
await toast(c, '已定向发布至 30 家机构')
await stepIs(c, '第 6 步 · 定向发布')
assert.equal(await c.getAttribute('[data-step="5"]', 'data-state'), 'done')
const logs = await text(c, '[data-testid=approval-logs]')
assert.match(logs, /陈志远 · 召集人\s*批准发布:同意发布/)
assert.match(logs, /定向发布至 30 家机构/)
await c.waitForSelector(`[data-todo="${M8}"]:has-text("第6步 · 定向发布")`)
await tab(c, '发布包与定向范围')
assert.equal(await c.locator('[data-dim=tiers] button:disabled').count(), 5)
step('召集人批准发布 → 第 6 步「定向发布」,留痕追加,定向范围锁定')

// 更正：原版保留 + 现行版本；发起更正新建流程
await tab(c, '更正与撤回')
await c.waitForSelector('[data-release="1"]:has-text("现行版本")')
await c.click('[data-testid=init-correct]')
await c.fill('[data-testid=correction-reason]', '第 2.1 节一级医院分位口径更正,受影响机构 4 家。')
await c.click('[data-testid=correction-submit]')
await toast(c, '已发起更正流程')
await flowIs(c, '2026年8月 DRG月度运行报告 更正')
await stepIs(c, '第 3 步 · 分析成稿')
await c.waitForSelector('[data-group="更正与撤回"] [data-todo="2026年8月 DRG月度运行报告 更正"]')
await todo(c, '2026年7月月度报告更正')
await tab(c, '更正与撤回')
await c.waitForSelector('[data-release="1"]:has-text("已更正 · 原版保留")')
await c.waitForSelector('[data-release="2"]:has-text("现行版本")')
assert.match(await text(c, '[data-testid=correction-note]'), /\+1,320 元更正为 \+960 元/)
step('更正与撤回:原版保留 + 现行版本 + 更正说明;发起更正新建流程至第 3 步')

// 提交至召集人审批 → 驳回至「分析成稿」
await todo(c, 'BR25 脑缺血性疾患专题')
await flowIs(c, 'BR25 脑缺血性疾患专题')
await tab(c, '审批与留痕')
await c.click('[data-testid=submit-btn]')
await toast(c, '已提交召集人审批')
await stepIs(c, '第 5 步 · 召集人审批')
await c.fill('[data-testid=approval-opinion]', '差异归因口径需与专家组再核')
await c.click('[data-testid=reject-btn]')
await toast(c, '已驳回至第 3 步「分析成稿」')
await stepIs(c, '第 3 步 · 分析成稿')
assert.match(await text(c, '[data-testid=approval-logs]'), /驳回至「分析成稿」:差异归因口径需与专家组再核/)
step('BR25 专题:提交召集人审批 → 驳回至第 3 步,意见留痕')

// 对标档位切换：召集人批准 → A13 档位变化
await todo(c, '14天再住院率 档位切换')
await c.waitForSelector('[data-testid=tier-approval]')
assert.equal(await text(c, '[data-testid=tier-indicator]'), '14天再住院率')
await c.click('[data-testid=tier-approve]')
await toast(c, '对标档位切换为匿名编号')
await c.waitForSelector('[data-todo="14天再住院率 档位切换"]', { state: 'detached' })
await c.goto(BASE + '/a13')
await c.waitForSelector('[data-tier="14天再住院率"]')
assert.equal(await text(c, '[data-tier="14天再住院率"] [aria-checked=true]'), '匿名编号')
assert.doesNotMatch(await text(c, '[data-tier="14天再住院率"]'), /审批中/)
step('批准档位切换:14天再住院率 匿名分位 → 匿名编号,A13 同步变化')

await browser.close()
assert.deepEqual(errors, [], '页面脚本错误:\n' + errors.join('\n'))
console.log('\n全部通过')
