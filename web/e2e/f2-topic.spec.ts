import { expect, test } from '@playwright/test'
import { openPage, toast, waitForAction } from './support'

/**
 * F2 病种专题: A6 采纳一个待评估选题 → A7 七段逐段审定 → 提交核对与审核.
 * A6 is being reworked (analytics-backed scoring), so the candidate is picked
 * by behaviour (the first card that offers 采纳) instead of by title.
 */
test.describe('F2 病种专题', () => {
  test('A6 采纳 → A7 七段审定 → 提交', async ({ page }) => {
    await openPage(page, 'A6')
    await expect(page.getByText('智能推荐').first()).toBeVisible()

    const adoptBtn = page.getByRole('button', { name: /采纳/ })
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
    expect(req.postDataJSON()).toHaveProperty('id')
    await expect(toast(page, /已采纳/)).toBeVisible()
    await expect(page).toHaveURL(/#\/A7/, { timeout: 5_000 })

    // A7 病种专题工作台
    const ws = page.locator('[data-screen-label="A7 病种专题工作台"]')
    await expect(ws).toBeVisible()
    await page.waitForResponse(r => r.url().includes('/api/v1/pages/A7'), { timeout: 5_000 }).catch(() => null)
    await expect(page.getByText('七段式结构')).toBeVisible()

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
      await btn.click()
      await act
    }
    await expect(page.getByRole('button', { name: '审定本段' })).toHaveCount(0)
    await expect(page.getByText('✓ 已人工审定')).toHaveCount(7)
    await expect(page.getByText('7/7').first()).toBeVisible()

    // 提交
    const submit = waitForAction(page, 'A7', 'submitReview')
    await submitBtn.click()
    const sreq = await submit
    expect((sreq.postDataJSON() as { approved: number[] }).approved).toEqual([0, 1, 2, 3, 4, 5, 6])
    await expect(toast(page, /已提交:机构核对/)).toBeVisible()
  })
})
