// 端到端冒烟（演示模式、种子数据）：npm run e2e
// 覆盖：A1 认证与身份选择、菜单裁剪、流程 1（A3 → A5）、流程 2（A7 审定提交 + 导出）、A9 必经节点、A10 答复、
// 流程 5（A11 发函 → 回执）、A13 档位审批、越权拦截与 A14 水印溯源、专家组只读。
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
  if (identity) {
    await p.waitForSelector('[data-testid=identity-step]')
    await p.click(`[data-identity=${identity}]`)
    await p.click('[data-testid=enter-btn]')
  }
  await p.waitForSelector('[data-testid=side-nav]')
  return p
}

const toast = async (p, text) => p.waitForSelector(`[data-sonner-toast]:has-text("${text}")`, { timeout: 5000 })
const navKeys = (p) => p.$$eval('[data-nav]', (els) => els.map((e) => e.getAttribute('data-nav')))
const step = (name) => console.log('✓', name)

// ---------------------------------------------------------------- 召集人（UKey）
const c = await session('ca', 'CONVENER')
assert.match(c.url(), /\/a2$/)
assert.deepEqual(await navKeys(c), ['A2', 'A3', 'A5', 'A7', 'A6', 'A4', 'A13', 'A9', 'A8', 'A10', 'A11'])
await c.goto(BASE + '/a3')
assert.match(await c.getAttribute('[data-testid=page-watermark]', 'data-text'), /^陈志远 示例市医疗保障局 \d{4}-\d{2}-\d{2} \d{2}:\d{2}$/)
assert.equal(await c.textContent('[data-testid=zone-tag]'), '分析监测区')
step('A1 UKey 登录 → 选择召集人身份 → 首页 A2,菜单按身份裁剪,实名水印')

// 流程 1：A3 数据到达 → 质量校验 → A5 生成报告
assert.equal(await c.textContent('[data-testid=timeliness]').then((s) => s.trim()), '90%')
assert.equal(await c.locator('[data-testid=qc-btn]').count(), 0)
await c.click('[data-testid=arrive-btn]')
await toast(c, '3 项指标恢复计算')
await c.click('[data-testid=qc-btn]')
await toast(c, '质量校验通过')
await c.waitForSelector('[data-testid=pipeline] [data-state=wait]', { state: 'detached' })
await c.click('[data-testid=go-a5]')
await c.waitForURL(/\/a5$/)
await c.click('[data-testid=gen-report]')
await toast(c, '进入发布工作流第 3 步')
step('流程 1:到数登记 → 依赖指标恢复 → 质量校验 → A5 生成月度报告')

// 流程 2：A7 七段审定后才能提交；导出走审批并返回水印编号
await c.goto(BASE + '/a7')
await c.waitForSelector('[data-testid=draft]')
await c.click('[data-testid=submit-topic]')
await toast(c, '还有 7 段未审定')
for (let i = 1; i <= 7; i++) {
  await c.click(`[data-testid=sections] button:nth-child(${i})`)
  if (i === 4) assert.ok(await c.isVisible('[data-testid=waterfall]'))
  if (i === 6) assert.match(await c.textContent('[data-testid=opt-warning]'), /不作为控费指标下达/)
  await c.click('[data-testid=approve-sec]')
  await toast(c, `第 ${i} 段已审定`)
}
assert.equal((await c.textContent('[data-testid=approved-count]')).trim(), '已审定 7/7 段')
await c.click('[data-testid=submit-topic]')
await toast(c, '机构核对')
await c.click('[data-testid=export-btn]')
await c.waitForSelector('[data-testid=export-content]')
await c.click('[data-testid=export-submit]')
const wm = (await c.textContent('[data-testid=export-wm]')).trim()
assert.match(wm, /^WM-\d{8}-\d{4}$/)
step(`流程 2:七段审定 → 提交审核;导出审批 → ${wm}`)

// A9：必经节点不可删除
await c.goto(BASE + '/a9')
await c.click('[data-node=召集人审批]')
await c.click('[data-testid=delete-node]')
await c.click('[data-testid=confirm-ok]')
await toast(c, '必经节点不可删除')
step('A9 召集人审批为必经节点,不可删除')

// A10：答复工单
await c.goto(BASE + '/a10')
await c.click('[data-ticket=YJ-0912-031]')
await c.fill('[data-testid=reply-input]', '已核实,12 例特例单议病例将在更正版本中剔除。')
await c.click('[data-testid=reply-btn]')
await toast(c, '已答复')
step('A10 核对期异议答复')

// 流程 5：A11 发函 → 机构回执
await c.goto(BASE + '/a11')
await c.click('[data-testid=send-letter]')
await c.click('[data-testid=confirm-ok]')
await toast(c, '提醒函已发出')
await c.fill('[data-testid=receipt-text-a11]', '电话回执:已组织科室分析并整改')
await c.click('[data-testid=receipt-btn]')
await toast(c, '的回执')
step('流程 5:规则触发 → 发出提醒函 → 机构回执')

// A13：档位切换需审批，审批前保持原档位
await c.goto(BASE + '/a13')
await c.click('[data-tier=CMI值] button:has-text("具名对比与排行")')
await c.waitForSelector('[data-testid=tier-dialog]:has-text("具名对比与排行将向同级机构显示")')
await c.fill('[data-testid=tier-reason]', '机构普遍认可该指标口径')
await c.click('[data-testid=tier-submit]')
await toast(c, '审批前保持原档位')
assert.match(await c.textContent('[data-tier=CMI值]'), /审批中:→ 具名对比与排行/)
assert.equal(await c.getAttribute('[data-tier=CMI值] [aria-checked=true]', 'aria-checked'), 'true')
assert.equal((await c.textContent('[data-tier=CMI值] [aria-checked=true]')).trim(), '匿名编号')
step('A13 档位切换提交召集人审批,原档位保持')

// 越权：召集人直接调用审计接口 → 403 并记录
const status = await c.evaluate(async () => (await fetch('/api/v1/audit/logs', { headers: { Authorization: `Bearer ${localStorage.getItem('dpub.token')}` } })).status)
assert.equal(status, 403)
await c.goto(BASE + '/a14')
await c.waitForURL(/\/a2$/)
step('越权:前端不渲染无权页面,接口 403')

// ---------------------------------------------------------------- 专家组列席（同一账号的第二个身份）
const e = await session('ca', 'EXPERT')
assert.deepEqual(await navKeys(e), ['A7'])
assert.equal(await e.locator('[data-testid=approve-sec]').count(), 0)
step('专家组列席身份:仅 A7,只读')

// ---------------------------------------------------------------- 安全管理员 / 审计员（账号 + 短信）
const s = await session('zhaoqiang')
assert.deepEqual(await navKeys(s), ['A12'])
await s.waitForSelector('[data-testid=perm-matrix]')
step('安全管理员:仅 A12(三员分立)')

const a = await session('suntao')
assert.deepEqual(await navKeys(a), ['A14'])
await a.fill('[data-testid=wm-input]', wm)
await a.click('[data-testid=trace-btn]')
await a.waitForSelector('[data-testid=trace-result]:has-text("陈志远")')
await a.click('button:has-text("越权尝试")')
await a.waitForSelector('[data-testid=audit-logs] tbody tr:has-text("/api/v1/audit/logs")')
await a.fill('[data-testid=wm-input]', 'WM-00000000-0000')
await a.click('[data-testid=trace-btn]')
await a.waitForSelector('text=未找到该水印编号')
step('审计员:水印编号溯源到人;越权尝试已记录')

await browser.close()
assert.deepEqual(errors, [], '页面脚本错误:\n' + errors.join('\n'))
console.log('\n全部通过')
