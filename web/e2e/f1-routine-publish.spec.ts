import { expect, test } from '@playwright/test'
import { getPageModel, openPage, postAction, reloadPage, toast, waitForAction } from './support'

/**
 * F1 例行发布:
 * A3 重新拉取 → (504) 重试 → 完成质量校验 → 生成月度报告 → A8 批准发布 → 签收追踪.
 */
test.describe('F1 例行发布', () => {
  // the approval gate is server state: put the demo task back at step 5 before and after
  test.beforeEach(async ({ request }) => {
    await postAction(request, 'A8', 'resetDemo', { taskId: 'm8' })
  })
  test.afterEach(async ({ request }) => {
    await postAction(request, 'A8', 'resetDemo', { taskId: 'm8' })
  })

  test('A3 归集 → 质量校验 → 生成月报 → A8 批准发布 → 签收追踪', async ({ page, request }) => {
    await openPage(page, 'A3')
    await expect(page.getByText('数据归集中心').first()).toBeVisible()

    // the late source (异地就医) is preselected and shows the overdue notice
    await expect(page.getByText(/未按时到达/).first()).toBeVisible()

    // 生成月度报告 is gated until the quality check is done
    await page.getByRole('button', { name: /生成月度报告/ }).click()
    await expect(toast(page, '请先完成数据到数与质量校验')).toBeVisible()

    // 重新拉取 → first attempt times out (HTTP 504)
    const pull1 = waitForAction(page, 'A3', 'retryPull')
    await page.getByRole('button', { name: '重新拉取' }).click()
    await pull1
    await expect(page.getByText('省平台接口超时 · HTTP 504')).toBeVisible({ timeout: 5_000 })

    // 重试 → succeeds, the source arrives
    await page.getByRole('button', { name: '重试', exact: true }).click()
    await expect(toast(page, /重试成功/)).toBeVisible({ timeout: 5_000 })
    await expect(page.getByText('全部到达')).toBeVisible()

    // 完成质量校验
    const qc = waitForAction(page, 'A3', 'completeQualityCheck')
    await page.getByRole('button', { name: '完成质量校验' }).click()
    await qc
    await expect(toast(page, '质量校验通过,指标集市已刷新')).toBeVisible()
    await expect(page.getByRole('button', { name: '完成质量校验' })).toBeHidden()

    // 生成月度报告 → navigates to A8 发布工作流
    const gen = waitForAction(page, 'A3', 'generateMonthlyReport')
    const a8Model = page.waitForResponse(r => r.url().includes('/api/v1/pages/A8'), { timeout: 10_000 }).catch(() => null)
    await page.getByRole('button', { name: /生成月度报告/ }).click()
    await gen
    await expect(toast(page, /月度报告草稿,进入发布工作流/)).toBeVisible()
    await expect(page).toHaveURL(/#\/A8/, { timeout: 5_000 })
    await expect(page.locator('[data-screen-label="04 发布工作流"]')).toBeVisible()
    await a8Model

    // A8: the current task waits at the approval gate (step 5)
    await expect(page.getByText('待召集人审批').first()).toBeVisible({ timeout: 10_000 })
    const approveBtn = page.getByRole('button', { name: /批准发布 · 推送 \d+ 家/ })
    await expect(approveBtn).toBeVisible()
    const coverage = Number((await approveBtn.innerText()).match(/(\d+) 家/)![1])

    // 批准 → confirm dialog → 确认发布
    await page.getByPlaceholder('审批意见(驳回时必填)').fill('e2e 同意发布')
    await approveBtn.click()
    const dialog = page.getByRole('dialog', { name: '确认定向发布' })
    await expect(dialog).toBeVisible()
    await expect(dialog.getByText(`${coverage} 家`).first()).toBeVisible()
    const approve = page.waitForResponse(
      r => r.request().method() === 'POST' && r.url().includes('/api/v1/actions/A8/approvePublish'),
    )
    await dialog.getByRole('button', { name: '确认发布' }).click()
    const approveRes = await approve
    expect(approveRes.ok(), `approvePublish → HTTP ${approveRes.status()}`).toBeTruthy()
    await expect(dialog).toBeHidden()
    await expect(toast(page, `已批准 · 定向发布至 ${coverage} 家机构`)).toBeVisible()

    // 签收追踪 tab opens with the sign-off ring and the unsigned list
    await expect(page.getByText('已签收', { exact: true })).toBeVisible()
    await expect(page.getByText(/未签收机构 · \d+ 家/)).toBeVisible()
    await expect(page.getByText(/已于本期批准并定向发布至 \d+ 家机构/)).toBeVisible()

    // 一键催办
    const urge = waitForAction(page, 'A8', 'urgeSignAll')
    await page.getByText('一键催办').click()
    await urge
    await expect(toast(page, /家未签收机构发送催办/)).toBeVisible()

    // server state survives a reload: the task moved past the approval gate
    const model = await getPageModel<{ tasks: { id: string; step: number }[] }>(request, 'A8')
    expect(model?.tasks.find(t => t.id === 'm8')?.step).toBeGreaterThanOrEqual(6)
    await reloadPage(page, 'A8')
    await expect(page.getByText(/已于本期批准并定向发布至 \d+ 家机构/)).toBeVisible()
  })

  test('A8 驳回 requires a comment', async ({ page }) => {
    await openPage(page, 'A8')
    await expect(page.getByRole('button', { name: /批准发布 · 推送/ })).toBeVisible({ timeout: 10_000 })
    await page.getByRole('button', { name: /^驳回至「/ }).click()
    await expect(toast(page, '驳回需填写审批意见')).toBeVisible()
  })
})
