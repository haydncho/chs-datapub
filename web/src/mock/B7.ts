/** B7 区域外患者 — seed data (same shape as GET /api/v1/pages/B7). */

export type B7Tone = 'ok' | 'warn' | 'info'
export type B7Icon = 'users' | 'coin' | 'map' | 'trend'

export interface B7Kpi {
  label: string
  /** value with optional unit after a space ("506 人次") */
  value: string
  sub: string
  tone: B7Tone
  icon: B7Icon
}

/** source region; `group` drives the bar colour */
export interface B7Source {
  name: string
  count: number
  group: 'county' | 'city' | 'province' | 'other'
}

export interface B7Drg {
  code: string
  name: string
  cases: number
  /** average cost per case, offsite patients (元) */
  offsiteAvg: number
  /** average cost per case, local patients (元) */
  localAvg: number
}

export interface B7Data {
  subtitle: string
  kpis: B7Kpi[]
  sources: B7Source[]
  drgs: B7Drg[]
}

export const B7_SEED: B7Data = {
  subtitle: '非本统筹区参保患者 · 2026年8月 · 来源:省平台异地结算回流',
  kpis: [
    { label: '区域外患者', value: '506 人次', sub: '占本院出院 14.8%', tone: 'info', icon: 'users' },
    { label: '医保记账', value: '812 万', sub: '异地结算', tone: 'info', icon: 'coin' },
    { label: '省内 / 省外', value: '92% / 8%', sub: '本省为主', tone: 'ok', icon: 'map' },
    { label: '次均费用差', value: '+6.4%', sub: '高于本地患者', tone: 'warn', icon: 'trend' },
  ],
  sources: [
    { name: '甲县', count: 186, group: 'county' },
    { name: '乙县', count: 142, group: 'county' },
    { name: '邻市', count: 98, group: 'city' },
    { name: '省会市', count: 41, group: 'city' },
    { name: '外省 A', count: 22, group: 'province' },
    { name: '其他', count: 17, group: 'other' },
  ],
  drgs: [
    { code: 'BR25', name: '脑缺血性疾患', cases: 64, offsiteAvg: 15840, localAvg: 14820 },
    { code: 'ES35', name: '呼吸系统感染', cases: 52, offsiteAvg: 10210, localAvg: 9640 },
    { code: 'IU29', name: '骨病及关节病', cases: 38, offsiteAvg: 11960, localAvg: 11020 },
    { code: 'FM19', name: '经皮心血管操作', cases: 31, offsiteAvg: 39100, localAvg: 38200 },
    { code: 'RE19', name: '恶性肿瘤化疗', cases: 27, offsiteAvg: 7420, localAvg: 7010 },
  ],
}

/**
 * ACTIONS: none — read-only view.
 */
