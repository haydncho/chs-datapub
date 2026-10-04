import { createRouter, createWebHistory } from 'vue-router'
import { PAGES } from '@/lib/nav'
import { notify } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

const views: Record<string, () => Promise<unknown>> = {
  A3: () => import('@/views/A3DataHubView.vue'),
  A5: () => import('@/views/A5TemplatesView.vue'),
  A7: () => import('@/views/A7TopicView.vue'),
  A9: () => import('@/views/A9FlowView.vue'),
  A10: () => import('@/views/A10OpinionsView.vue'),
  A11: () => import('@/views/A11AlertsView.vue'),
  A12: () => import('@/views/A12PermissionsView.vue'),
  A13: () => import('@/views/A13PolicyView.vue'),
  A14: () => import('@/views/A14AuditView.vue'),
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', name: 'home', component: { render: () => null } },
        ...PAGES.map((p) => ({ path: p.key.toLowerCase(), name: p.key, component: views[p.key], meta: { page: p.key } })),
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
