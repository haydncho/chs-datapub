import { expect, test } from './fixtures'
import { getPageModel, openPage, postAction, reloadPage, toast, waitForAction } from './support'

/**
 * F2 病种专题: A6 采纳一个待评估选题 → A7 打开该选题的专题工作台 → 七段逐段审定 → 提交核对与审核.
 * A6 decisions and A7 reviews are server state: the demo is reset before and after (A7/resetDemo).
 * The candidate is picked by behaviour (the first card still 待评估) instead of by title.
 */
test.describe('F2 病种专题', () => {
  test.beforeEach(async ({ request }) => {
    await postAction(request, 'A7', 'resetDemo')
  })
  test.afterEach(async ({ request }) => {
    await postAction(request, 'A7', 'resetDemo')
  })

  test('A6 采纳 → A7 七段审定 → 提交', async ({ page, request }) => {
    await openPage(page, 'A6')
    await expect(page.getByText('智能推荐').first()).toBeVisible()

    const adoptBtn = page.getByRole('button', { name: '采纳 · 进入专题工作台' })
    // find a candidate still 待评估 (the default selection may already be 已采纳)
    if (!(await adoptBtn.isVisible())) {
      const open = page.getByText('待评估', { exact: true })
      const n = await open.count()
      expect(n, 'A6 has no 待评估 candidate to adopt').toBeGreaterThan(0)
      await open.first().click()
    }
    await expect(adoptBtn).toBeVisible()

    const adopt = waitForAction(page, 'A6', 'adoptTopic')
    await adoptBtn.click()
    const req = await adopt
    const topicId = (req.postDataJSON() as { id: string }).id
    expect(topicId).toMatch(/^T-/)
    await expect(toast(page, /已采纳/)).toBeVisible()
    // A7 opens the adopted topic, not always BR25
    await expect(page).toHaveURL(new RegExp(`#/A7\\?topic=${topicId}`), { timeout: 5_000 })
    const code = topicId.replace(/^T-/, '').replace(/-.*$/, '')

    // A7 病种专题工作台
    const ws = page.locator('[data-screen-label="A7 病种专题工作台"]')
    await expect(ws).toBeVisible()
    await page.waitForResponse(r => r.url().includes('/api/v1/pages/A7'), { timeout: 5_000 }).catch(() => null)
    await expect(page.getByText('七段式结构')).toBeVisible()
    await expect(ws.locator('.yb-num.text-brand', { hasText: code }).first()).toBeVisible()

    const submitBtn = page.getByRole('button', { name: '提交核对与审核' })

    // submitting early is refused while sections are still drafts
    const pendingBefore = await page.getByRole('button', { name: '审定本段' }).count()
    if (pendingBefore > 0) {
      await submitBtn.click()
      await expect(toast(page, new RegExp(`还有 ${pendingBefore} 段未审定`))).toBeVisible()
    }

    // approve every remaining section (seven in total)
    for (let guard = 0; guard < 10; guard++) {
      const btn = page.getByRole('button', { name: '审定本段' }).first()
      if (!(await btn.count())) break
      const act = waitForAction(page, 'A7', 'approveSection')
      // the view updates only after the server accepted it and the read model was re-read
      const reread = page.waitForResponse(r => r.url().includes('/api/v1/pages/A7') && r.request().method() === 'GET', { timeout: 10_000 })
      await btn.click()
      await act
      await reread
      await page.waitForTimeout(100)
    }
    await expect(page.getByRole('button', { name: '审定本段' })).toHaveCount(0)
    await expect(page.getByText('✓ 已人工审定')).toHaveCount(7)
    await expect(page.getByText('7/7').first()).toBeVisible()

    // 提交 — once; the A8 task moves to 专家组审核 (step 4)
    const submit = waitForAction(page, 'A7', 'submitReview')
    await submitBtn.click()
    const sreq = await submit
    expect((sreq.postDataJSON() as { approved: number[] }).approved).toEqual([0, 1, 2, 3, 4, 5, 6])
    await expect(toast(page, /已提交:机构核对/)).toBeVisible()
    await expect(page.getByRole('button', { name: '已提交 · 核对与审核中' })).toBeDisabled()
    const model = await getPageModel<{ taskSteps?: Record<string, number> }>(request, 'A7')
    if (model) expect(model.taskSteps?.[topicId]).toBe(4)

    // server state survives a reload
    await reloadPage(page, 'A7')
    await expect(page.getByText('✓ 已人工审定')).toHaveCount(7)
    await expect(page.getByRole('button', { name: '已提交 · 核对与审核中' })).toBeVisible()
  })
})
