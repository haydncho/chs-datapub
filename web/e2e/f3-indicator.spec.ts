import { expect, test } from '@playwright/test'
import { openPage, toast, waitForAction } from './support'

/** F3 指标上线: A4 + 新建指标 → 向导四步 (定义指标 · 设规则 · 绑呈现 · 预览提交) → 提交上线审批. */
test.describe('F3 指标上线', () => {
  test('A4 向导四步 → 提交上线审批', async ({ page }) => {
    await openPage(page, 'A4')
    await expect(page.getByText('指标配置').first()).toBeVisible()

    await page.getByRole('button', { name: '+ 新建指标' }).click()
    const wiz = page.getByRole('dialog', { name: '新建指标' })
    await expect(wiz).toBeVisible()

    const next = wiz.getByRole('button', { name: '下一步' })

    // 1 定义指标
    await expect(wiz.getByText('分子 / 分母')).toBeVisible()
    await expect(wiz.getByText(/口径校验通过/)).toBeVisible()
    await next.click()

    // 2 设规则: pick a non-default tier → approval hint; toggle 仅内部 on and off again
    await expect(wiz.getByText('对标档位')).toBeVisible()
    await wiz.getByText('匿名编号', { exact: true }).click()
    await expect(wiz.getByText(/非默认档位需召集人审批/)).toBeVisible()
    await wiz.getByText('匿名分位', { exact: true }).click()
    await expect(wiz.getByText(/非默认档位需召集人审批/)).toBeHidden()
    const sw = wiz.getByRole('switch')
    await sw.click()
    await expect(sw).toHaveAttribute('aria-checked', 'true')
    await expect(wiz.getByText('仅内部指标 · 该身份下不渲染')).toBeVisible()
    await sw.click()
    await expect(sw).toHaveAttribute('aria-checked', 'false')
    await next.click()

    // 3 绑呈现
    await expect(wiz.getByText('图表模板')).toBeVisible()
    await expect(wiz.getByText('粒度上限')).toBeVisible()
    await next.click()

    // 4 预览提交
    await expect(wiz.getByText('提交前确认')).toBeVisible()
    await expect(next).toBeHidden()
    const submit = waitForAction(page, 'A4', 'submitIndicator')
    await wiz.getByRole('button', { name: '提交上线审批' }).click()
    const req = await submit
    const body = req.postDataJSON() as { name: string; tier: string; internalOnly: boolean; approvalNo: string }
    expect(body.internalOnly).toBe(false)
    expect(body.approvalNo).toBeTruthy()
    await expect(toast(page, `已提交上线审批 · ${body.approvalNo}`)).toBeVisible()
    await expect(wiz.getByText(new RegExp(`✓ 已提交上线审批 ${body.approvalNo}`))).toBeVisible()
    await expect(wiz.getByRole('button', { name: '提交上线审批' })).toBeHidden()

    // close; reopening starts at step 1 again
    await wiz.getByText('关闭 · 草稿已自动保存').click()
    await expect(wiz).toBeHidden()
    await page.getByRole('button', { name: '+ 新建指标' }).click()
    await expect(page.getByRole('dialog', { name: '新建指标' }).getByText('分子 / 分母')).toBeVisible()
  })
})
