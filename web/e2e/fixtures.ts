import { test as base, expect } from '@playwright/test'

export { expect }

/** 会话在 sessionStorage 里的键(web/src/app/session.ts) */
const SESSION_KEY = 'yb.session'

/**
 * 默认夹具:每个用例先通过真实登录接口以「陈志远 · 召集人 · 医保局端」建立会话,
 * 再把会话写入 sessionStorage,页面不会被登录守卫送回 #/A1。
 * 后端不可达时不预置会话(依赖后端状态的用例本来就会跳过)。
 */
export const test = base.extend({
  page: async ({ page, request }, use) => {
    try {
      const r = await request.post('/api/v1/auth/login', {
        data: { method: 'cert', account: 'chenzy', pin: '123456', side: 'bureau' },
      })
      if (r.ok()) {
        const session = JSON.stringify(await r.json())
        await page.addInitScript(([k, v]) => sessionStorage.setItem(k, v), [SESSION_KEY, session])
      }
    } catch { /* 后端未启动 */ }
    await use(page)
  },
})
