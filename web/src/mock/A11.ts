/** A11 预警提醒 — seed data (same shape as GET /api/v1/pages/A11). */

export type AlertLevel = 'high' | 'mid' | 'low'
export type AlertStatus = 'unsent' | 'sent' | 'ack'

export interface A11Alert {
  id: string
  org: string
  metric: string
  /** current value as displayed, e.g. "+23.6%" */
  value: string
  /** trigger condition, e.g. "环比 > 15%" */
  threshold: string
  level: AlertLevel
  /** last 12 months (10月 … 9月), the last point is the current period */
  trend: number[]
  /** numeric threshold drawn as the dashed line on the trend chart */
  thresholdValue: number
  unit: string
  status: AlertStatus
  /** set when computed live by the analytics rule engine (alert_rule.id / org.id / rule kind) */
  ruleId?: string
  orgId?: string
  kind?: 'mom_pct' | 'gt_p90' | 'gt' | 'lt'
  /** 归因提示 for this alert (seed); live alerts get one generated from their trend */
  attribution?: string
  /** 提醒函 sent on (MM-DD), by whom, and the 10-working-day 回执 deadline — from the server */
  sentAt?: string
  sentBy?: string
  receiptDue?: string
  /** the institution's own 回执 (status ack) — from the server */
  receipt?: { category: string; text: string; at?: string; by?: string }
}

export interface A11Data {
  period: string
  ruleCount: number
  scanTime: string
  /** 回执率, % — computed by the server: 已回执 / (已发提醒函 + 已回执) */
  ackRate: number
  months: string[]
  /** generic fallback 归因提示 (each alert normally carries its own) */
  attribution: string
  /** fallback notes when an alert has no 提醒函 date / 回执 on record */
  sentNote: string
  ackNote: string
  alerts: A11Alert[]
  /** live rule-engine run (GET /api/v1/analytics/alerts/evaluate): data batch id + time */
  batch?: string
  computedAt?: string
}

/** GET /api/v1/analytics/alerts/evaluate */
export interface A11Evaluation {
  /** evaluated month, YYYY-MM */
  periodKey: string | null
  months: string[]
  ruleCount: number
  batch: string
  computedAt: string
  alerts: A11Alert[]
}

export const A11_SEED: A11Data = {
  period: '2026年8月期',
  ruleCount: 18,
  scanTime: '每日 07:00 扫描',
  ackRate: 25,
  months: ['10', '11', '12', '1', '2', '3', '4', '5', '6', '7', '8', '9'],
  attribution: '归因提示:指标连续超出阈值,建议核实是否存在新开展术式、编码变化或患者结构变化。',
  sentNote: '提醒函已发送 · 机构需在 10 个工作日内回执说明原因与整改措施',
  ackNote: '机构已回执 · 下期自动复查。',
  alerts: [
    { id: 'AL-01', org: '某肛肠专科医院', metric: 'GG19 次均总费用', value: '+23.6%', threshold: '环比 > 15%', level: 'high', trend: [7.1, 7.0, 7.3, 7.2, 7.4, 7.3, 7.5, 7.6, 7.4, 7.7, 7.6, 9.4], thresholdValue: 8.5, unit: '万元 · 千元', status: 'unsent', attribution: '归因提示:9 月次均总费用由 7.6 千元升至 9.4 千元,环比 +23.6%;前 11 个月稳定在 7.0–7.7 千元,属单月突增。建议核实是否新开展吻合器类术式或高值耗材收费变化。' },
    { id: 'AL-02', org: '甲县人民医院', metric: 'ES35 14天再住院率', value: '8.7%', threshold: '> P90 (6.2%)', level: 'high', trend: [4.8, 5.1, 5.0, 5.4, 5.2, 5.6, 5.9, 6.0, 6.4, 6.8, 7.6, 8.7], thresholdValue: 6.2, unit: '%', status: 'sent', attribution: '归因提示:14 天再住院率连续 12 个月上升,由 4.8% 升至 8.7%,高于同级 P90(6.2%)。建议核查计划内再入院(化疗周期等)是否被计入、是否存在分解住院。' },
    { id: 'AL-03', org: '示例市第三人民医院', metric: 'BR25 临界区病例占比', value: '41%', threshold: '> 35%', level: 'mid', trend: [28, 30, 29, 31, 33, 32, 34, 35, 36, 38, 39, 41], thresholdValue: 35, unit: '%', status: 'unsent', attribution: '归因提示:BR25 临界区病例占比由 28% 逐月升至 41%,近 6 个月均高于 35%。建议抽查临界区病例的主诊断与并发症编码。' },
    { id: 'AL-04', org: '乙县人民医院', metric: '结算清单质控率', value: '93.8%', threshold: '< 95%', level: 'mid', trend: [96.2, 96.0, 95.8, 95.9, 95.4, 95.6, 95.1, 94.8, 94.6, 94.2, 94.0, 93.8], thresholdValue: 95, unit: '%', status: 'ack', attribution: '归因提示:结算清单质控率自 3 月起低于 95%,9 月为 93.8%。主要扣分项为主要诊断选择与手术操作编码缺失。' },
    { id: 'AL-05', org: '丙区第2医院', metric: '医保外费用占比', value: '11.2%', threshold: '> P90 (9.4%)', level: 'mid', trend: [8.1, 8.4, 8.2, 8.9, 9.0, 9.3, 9.6, 9.8, 10.2, 10.6, 10.9, 11.2], thresholdValue: 9.4, unit: '%', status: 'unsent', attribution: '归因提示:医保外费用占比由 8.1% 升至 11.2%,高于同级 P90(9.4%)。建议核实自费药品耗材的告知同意与特需服务收费。' },
    { id: 'AL-06', org: '示例市中医院', metric: 'IU29 例均基金差额', value: '+1,620', threshold: '> +1,500 元', level: 'low', trend: [980, 1040, 1100, 1180, 1220, 1260, 1310, 1380, 1420, 1480, 1560, 1620], thresholdValue: 1500, unit: '元', status: 'sent', attribution: '归因提示:IU29 例均基金差额由 +980 元升至 +1,620 元,连续 2 个月超过 +1,500 元。建议核实高值耗材使用与新开展术式的编码。' },
    { id: 'AL-07', org: '示例市第一人民医院', metric: 'IU29 例均基金差额', value: '+1,620', threshold: '> +1,500 元', level: 'low', trend: [1010, 1060, 1120, 1170, 1230, 1280, 1330, 1390, 1440, 1500, 1560, 1620], thresholdValue: 1500, unit: '元', status: 'sent', attribution: '归因提示:IU29 例均基金差额 12 个月持续上升,9 月 +1,620 元越过 +1,500 元阈值。近 3 月高值耗材使用率由 12% 升至 21%,建议核实新开展术式或编码变化。' },
  ],
}

/**
 * ACTIONS:
 * sendReminder({ alertId: string, org: string }) — 发送提醒函; alert status unsent → sent (机构 10 个工作日内回执). Only for an
 *   existing alert or one the rule engine really raises (AL-{rule}-{org}); org / metric come from the server, never the client.
 *   A 县区 identity only sees, and may only remind, institutions of its own district.
 * addToTopic({ alertId: string, metric: string }) — 转专题选题: put the alert into the A6 智能推荐 选题池 (status unchanged).
 * (忽略 only shows a hint that a reason + 召集人 confirmation is required; no server call.)
 */
