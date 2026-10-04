import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { ALIAS, type PageCode } from './nav'
import { PAGES } from './pages'
import { landingOf, session } from './session'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/cockpit' },
  ...Object.entries(ALIAS).map(([from, to]) => ({ path: `/${from}`, redirect: `/${to}` })),
  ...(Object.keys(PAGES) as PageCode[]).map(code => ({
    path: `/${code}`,
    name: code,
    component: PAGES[code].load,
    meta: { code, layout: PAGES[code].layout, title: PAGES[code].title },
  })),
  { path: '/:pathMatch(.*)*', redirect: '/cockpit' },
]

export const router = createRouter({
  // hash history: deep links like #/A7 work from any static host
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// Logged-in identities only reach the pages their role grants (the server enforces the same matrix).
// Without a session the platform runs as the open demo.
router.beforeEach(to => {
  const s = session.current
  const code = to.meta.code as string | undefined
  if (!s || !code || code === 'A1' || s.pages.includes(code)) return true
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
