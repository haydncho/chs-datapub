import { createRouter, createWebHistory } from 'vue-router'
import { PAGES, pagePath } from '@/lib/nav'
import { notify } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

const views: Record<string, () => Promise<unknown>> = {
  // 第一批
  A2: () => import('@/views/holo/A2HoloView.vue'),
  A4: () => import('@/views/indicator/A4IndicatorView.vue'),
  A6: () => import('@/views/indicator/A6RecommendView.vue'),
  A8: () => import('@/views/publish/A8PublishView.vue'),
  B1: () => import('@/views/holo/B1HospitalHoloView.vue'),
  C2: () => import('@/views/regional/C2ProvinceView.vue'),
  // 第三批
  B2: () => import('@/views/portal/B2GroupDrillView.vue'),
  B3: () => import('@/views/portal/B3BenchmarkView.vue'),
  B4: () => import('@/views/portal/B4ReportsView.vue'),
  B5: () => import('@/views/portal/B5OpinionView.vue'),
  B6: () => import('@/views/portal/B6PolicyView.vue'),
  B7: () => import('@/views/portal/B7OffsiteView.vue'),
  C1: () => import('@/views/regional/C1CountyView.vue'),
  C3: () => import('@/views/regional/C3SupervisionView.vue'),
  // 第二批
  A3: () => import('@/views/A3DataHubView.vue'),
  A5: () => import('@/views/A5TemplatesView.vue'),
  A7: () => import('@/views/A7TopicView.vue'),
  A9: () => import('@/views/A9FlowView.vue'),
  A10: () => import('@/views/A10OpinionsView.vue'),
  A11: () => import('@/views/A11AlertsView.vue'),
  A12: () => import('@/views/A12PermissionsView.vue'),
  A13: () => import('@/views/A13PolicyView.vue'),
  A14: () => import('@/views/A14AuditView.vue'),
  E1: () => import('@/views/ExportsView.vue'),
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    // D1 移动端（政务 APP 内嵌，390 宽独立布局，无侧栏）
    { path: '/m', name: 'D1', component: () => import('@/views/portal/D1MobileView.vue'), meta: { page: 'D1' } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', name: 'home', component: { render: () => null } },
        ...PAGES.filter((p) => !p.path).map((p) => ({ path: pagePath(p).slice(1), name: p.key, component: views[p.key], meta: { page: p.key } })),
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

/** 登录守卫 + 菜单裁剪：无权页面不渲染（服务端同样按身份拒绝并记录越权尝试）。 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const auth = useAuthStore()
  if (!auth.token) return { name: 'login' }
  let me
  try {
    me = await auth.load()
  } catch {
    auth.end()
    return { name: 'login' }
  }
  if (!me) return { name: 'login' }
  const page = to.meta.page as string | undefined
  if (!page || !me.pages.includes(page)) {
    if (page) notify('当前身份无权访问该页面')
    const first = me.pages[0]
    return first ? { name: first } : { name: 'login' }
  }
  return true
})

export default router
