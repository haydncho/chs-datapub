import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { ALIAS, type PageCode } from './nav'
import { PAGES } from './pages'
import { encodeRedirect } from './guard'
import { landingOf, session } from './session'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/A1' },
  ...Object.entries(ALIAS).map(([from, to]) => ({ path: `/${from}`, redirect: `/${to}` })),
  ...(Object.keys(PAGES) as PageCode[]).map(code => ({
    path: `/${code}`,
    name: code,
    component: PAGES[code].load,
    meta: { code, layout: PAGES[code].layout, title: PAGES[code].title },
  })),
  { path: '/:pathMatch(.*)*', redirect: '/A1' },
]

export const router = createRouter({
  // hash history: deep links like #/A7 work from any static host
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

const PAGE_CODES = Object.keys(PAGES)

// 路由守卫:
//  · 没有会话 → 只能访问 A1(登录页),其余一律跳到 #/A1?redirect=<原目标>(有后端用 token 会话,无后端用本地演示会话,逻辑相同);
//  · 已登录 → 只能打开该身份有权访问的页面(后端按同一矩阵校验),否则回到自己的落地页。
router.beforeEach(to => {
  const s = session.current
  const code = to.meta.code as string | undefined
  if (!code || code === 'A1') return true
  if (!s) return { path: '/A1', query: { redirect: encodeRedirect(to.fullPath, PAGE_CODES) } }
  if (s.pages.includes(code)) return true
  const home = landingOf(s.identity)
  return home.code === code ? true : { path: `/${home.code}`, query: home.query }
})

router.afterEach(to => {
  const t = to.meta.title as string | undefined
  document.title = t ? `${t} · 医保数据公开平台` : '医保数据公开平台'
})

/** Navigate to a page by code (accepts prototype aliases). Optional query, e.g. { who: 'org' }. */
export function goPage(code: string, query?: Record<string, string>) {
  const target = (ALIAS[code] ?? code) as PageCode
  if (!(target in PAGES)) return
  router.push({ path: `/${target}`, query })
}
