import { expect, test } from './fixtures'
import { openPage, postAction, reloadPage, toast, waitForAction } from './support'

/**
 * F3 指标上线: A4 + 新建指标 → 向导四步 (定义指标 · 设规则 · 绑呈现 · 预览提交) → 提交上线审批
 * → the indicator is listed as 审批中 and survives a reload. Wizard submissions are server state:
 * the demo is reset before and after (A4/resetDemo).
 */
test.describe('F3 指标上线', () => {
  test.beforeEach(async ({ request }) => {
    await postAction(request, 'A4', 'resetDemo')
  })
  test.afterEach(async ({ request }) => {
    await postAction(request, 'A4', 'resetDemo')
  })

  test('A4 向导四步 → 提交上线审批', async ({ page }) => {
    const name = '术前平均住院日'
    await openPage(page, 'A4')
    await expect(page.getByText('指标配置').first()).toBeVisible()

    await page.getByRole('button', { name: '+ 新建指标' }).click()
    const wiz = page.getByRole('dialog', { name: '新建指标' })
    await expect(wiz).toBeVisible()

    const next = wiz.getByRole('button', { name: '下一步' })
    await expect(wiz.getByRole('button', { name: '上一步' })).toBeDisabled()

    // 1 定义指标: empty / invalid input is refused
    await expect(wiz.getByText('分子 / 分母')).toBeVisible()
    await next.click()
    await expect(toast(page, '请填写指标名称')).toBeVisible()
    await wiz.getByLabel('指标名称').fill('=HYPERLINK("x")')
    await expect(wiz.getByText('指标名称含有不允许的特殊字符', { exact: true })).toBeVisible()
    await wiz.getByLabel('指标名称').fill('次均总费用')
    await expect(wiz.getByText('已存在同名指标「次均总费用」', { exact: true })).toBeVisible()
    await wiz.getByLabel('指标名称').fill(name)
    await wiz.getByLabel('监测子域').fill('住院效率')
    await wiz.getByLabel('分子').fill('Σ术前住院天数')
    await wiz.getByLabel('分母').fill('手术出院人次')
    await wiz.getByLabel('新增过滤条件').fill('手术标志 = 1')
    await wiz.getByRole('button', { name: '+ 条件' }).click()
    await expect(wiz.getByText('手术标志 = 1')).toBeVisible()
    await wiz.getByRole('button', { name: '县区' }).click()
    await expect(wiz.getByText(/口径校验通过/)).toBeVisible()
    await next.click()

    // 2 设规则: pick a non-default tier → approval hint; 仅内部 disables the tiers
    await expect(wiz.getByText('对标档位')).toBeVisible()
    await wiz.getByText('匿名编号', { exact: true }).click()
    await expect(wiz.getByText(/非默认档位需召集人审批/)).toBeVisible()
    await wiz.getByText('匿名分位', { exact: true }).click()
    await expect(wiz.getByText(/非默认档位需召集人审批/)).toBeHidden()
    const sw = wiz.getByRole('switch')
    await sw.click()
    await expect(sw).toHaveAttribute('aria-checked', 'true')
    await expect(wiz.getByText('仅内部指标 · 该身份下不渲染')).toBeVisible()
    await expect(wiz.getByText(/仅内部指标不可配置对标档位/)).toBeVisible()
    await sw.click()
    await expect(sw).toHaveAttribute('aria-checked', 'false')
    await next.click()

    // 3 绑呈现
    await expect(wiz.getByText('图表模板')).toBeVisible()
    await wiz.getByRole('button', { name: '趋势线' }).click()
    await expect(wiz.getByText('粒度上限')).toBeVisible()
    await next.click()

    // 4 预览提交: the confirmation lists what will be submitted
    await expect(wiz.getByText('提交前确认')).toBeVisible()
    await expect(wiz.getByText('匿名分位', { exact: true })).toBeVisible()
    await expect(wiz.getByText('趋势线 · 粒度上限 病组')).toBeVisible()
    await expect(next).toBeHidden()
    const submit = waitForAction(page, 'A4', 'submitIndicator')
    await wiz.getByRole('button', { name: '提交上线审批' }).click()
    const req = await submit
    const body = req.postDataJSON() as { name: string; tier: string; internalOnly: boolean; dims: string[] }
    expect(body).toMatchObject({ name, tier: 'pct', internalOnly: false })
    expect(body.dims).toContain('县区')
    await expect(toast(page, /^已提交上线审批 · ZB-\d{4}-\d{4}$/)).toBeVisible()
    await expect(wiz.getByText(/✓ 已提交上线审批 ZB-/)).toBeVisible()
    await expect(wiz.getByRole('button', { name: '提交上线审批' })).toBeHidden()

    // close; the indicator is listed as 审批中; reopening starts a new, empty indicator at step 1
    await wiz.getByText('关闭', { exact: true }).click()
    await expect(wiz).toBeHidden()
    const row = page.locator('[role="button"]').filter({ hasText: name })
    await expect(row).toBeVisible()
    await expect(row.getByText('审批中')).toBeVisible()
    await page.getByRole('button', { name: '+ 新建指标' }).click()
    const wiz2 = page.getByRole('dialog', { name: '新建指标' })
    await expect(wiz2.getByText('分子 / 分母')).toBeVisible()
    await expect(wiz2.getByLabel('指标名称')).toHaveValue('')
    await wiz2.getByLabel('指标名称').fill(name)
    await expect(wiz2.getByText(`已存在同名指标「${name}」`, { exact: true })).toBeVisible()
    await page.keyboard.press('Escape')

    // server state survives a reload
    await reloadPage(page, 'A4')
    await expect(page.locator('[role="button"]').filter({ hasText: name })).toBeVisible()
  })
})
