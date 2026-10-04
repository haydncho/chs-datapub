import type { Component } from 'vue'
import type { PageCode } from './nav'

/** Shell layout a page wants. */
export type Layout =
  /** light workbench: top bar + stage nav + sub-tabs */
  | 'workbench'
  /** dark cockpit: top bar + stage nav, no sub-tabs */
  | 'cockpit'
  /** full-screen, no shell (login) */
  | 'bare'

export interface PageDef {
  title: string
  layout: Layout
  load: () => Promise<{ default: Component }>
}

export const PAGES: Record<PageCode, PageDef> = {
  cockpit: { title: '医保数据全息图', layout: 'cockpit', load: () => import('@/pages/Cockpit.vue') },
  A1: { title: '登录与身份', layout: 'bare', load: () => import('@/pages/A1Login.vue') },
  A3: { title: '数据归集中心', layout: 'workbench', load: () => import('@/pages/A3DataHub.vue') },
  A4: { title: '指标配置', layout: 'workbench', load: () => import('@/pages/A4Indicators.vue') },
  A5: { title: '图表与报告模板', layout: 'workbench', load: () => import('@/pages/A5Templates.vue') },
  A6: { title: '智能推荐', layout: 'workbench', load: () => import('@/pages/A6Recommend.vue') },
  A7: { title: '病种专题工作台', layout: 'workbench', load: () => import('@/pages/A7Topic.vue') },
  A8: { title: '发布工作流', layout: 'workbench', load: () => import('@/pages/A8Publish.vue') },
  A9: { title: '流程设计器', layout: 'workbench', load: () => import('@/pages/A9FlowDesigner.vue') },
  A10: { title: '意见与申诉', layout: 'workbench', load: () => import('@/pages/A10Feedback.vue') },
  A11: { title: '预警提醒', layout: 'workbench', load: () => import('@/pages/A11Alerts.vue') },
  A12: { title: '用户权限', layout: 'workbench', load: () => import('@/pages/A12Permissions.vue') },
  A13: { title: '展示策略', layout: 'workbench', load: () => import('@/pages/A13DisplayPolicy.vue') },
  A14: { title: '审计日志', layout: 'workbench', load: () => import('@/pages/A14Audit.vue') },
  A15: { title: '外观配置', layout: 'workbench', load: () => import('@/pages/A15Appearance.vue') },
  B1: { title: '本院全息', layout: 'workbench', load: () => import('@/pages/B1Hospital.vue') },
  B2: { title: '病组下钻', layout: 'workbench', load: () => import('@/pages/B2DrgDrill.vue') },
  B3: { title: '对标PK', layout: 'workbench', load: () => import('@/pages/B3Benchmark.vue') },
  B4: { title: '报告中心', layout: 'workbench', load: () => import('@/pages/B4Reports.vue') },
  B5: { title: '意见核对', layout: 'workbench', load: () => import('@/pages/B5Verify.vue') },
  B6: { title: '政策培训', layout: 'workbench', load: () => import('@/pages/B6Training.vue') },
  B7: { title: '区域外患者', layout: 'workbench', load: () => import('@/pages/B7OffsitePatients.vue') },
  C3: { title: '外部监督', layout: 'workbench', load: () => import('@/pages/C3Oversight.vue') },
  D1: { title: '移动端', layout: 'workbench', load: () => import('@/pages/D1Mobile.vue') },
}
