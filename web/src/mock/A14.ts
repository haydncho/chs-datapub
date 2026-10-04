/** A14 审计日志 — seed data (same shape as GET /api/v1/pages/A14). */

export type A14EventType = '查阅' | '审批' | '配置' | '导出' | '权限' | '发布' | '登录' | '删除' | '其他'

/** one changed field of a configuration event (A13 / A15) */
export interface A14Change {
  /** field key (minOrg, c, …) */
  field: string
  /** Chinese field name */
  label: string
  from: string
  to: string
}

export interface A14Log {
  id: string
  /** time (today hh:mm:ss, or "昨天 17:40") */
  time: string
  type: A14EventType
  who: string
  role: string
  object: string
  ip: string
  /** browser / terminal */
  terminal: string
  /** watermark trace id */
  watermark: string
  /** chained-signature verification passed */
  signatureOk: boolean
  /** config / permission change: before → after */
  diff: { from: string; to: string } | null
  /** anomaly: off-hours bulk access, already pushed to security officer */
  risk: boolean
  // ── optional, filled from the live audit API (GET /api/v1/audit) ──
  /** full date yyyy-mm-dd (live rows span several days) */
  date?: string
  /** 22:00–06:00 platform time */
  offHours?: boolean
  /** text of the anomaly banner when it differs from the seed's */
  riskNote?: string
  /** page code and action name the event was recorded for */
  page?: string
  action?: string
  /** SHA-256 chain: this event's hash and its predecessor's */
  hash?: string
  prevHash?: string
  /** field-level before/after of configuration events */
  changes?: A14Change[]
}

export interface A14Data {
  /** header subtitle */
  summary: string
  /** date shown in the detail panel */
  date: string
  /** event-type filter chips (first = 全部) */
  filters: string[]
  logs: A14Log[]
  /** id of the initially selected log */
  selectedId: string
}

export const A14_SEED: A14Data = {
  summary: '全量留存 6 年 · 防篡改链式签名 · 今日 1,284 条',
  date: '2026-10-04',
  filters: ['全部', '查阅', '配置', '导出', '权限', '删除'],
  logs: [
    { id: 'L01', time: '09:12:04', type: '查阅', who: '李敏', role: '定点医疗机构', object: '2026年8月 DRG月度运行报告 §2', ip: '10.86.31.12', terminal: 'Chrome 128', watermark: 'WM-7F3A-11C2', signatureOk: true, diff: null, risk: false },
    { id: 'L02', time: '09:02:18', type: '审批', who: '陈志远', role: '召集人', object: 'BR25 专题 → 机构核对', ip: '10.86.12.20', terminal: 'Chrome 128', watermark: 'WM-7F3A-12C2', signatureOk: true, diff: null, risk: false },
    { id: 'L03', time: '08:58:40', type: '配置', who: '李华', role: '行政管理组', object: '医保外费用占比 对标档位', ip: '10.86.12.47', terminal: 'Chrome 128', watermark: 'WM-7F3A-13C2', signatureOk: true, diff: { from: '匿名分位', to: '具名PK与排行' }, risk: false },
    { id: 'L04', time: '08:47:11', type: '导出', who: '王倩', role: '行政管理组', object: '意见汇总表 2026-09', ip: '10.86.12.51', terminal: 'Chrome 128', watermark: 'WM-7F3A-14C2', signatureOk: true, diff: null, risk: false },
    { id: 'L05', time: '02:14:36', type: '查阅', who: '孙磊', role: '定点医疗机构', object: '本院全息 × 38 次', ip: '10.86.77.9', terminal: 'Chrome 128', watermark: 'WM-7F3A-15C2', signatureOk: true, diff: null, risk: true },
    { id: 'L06', time: '昨天 17:40', type: '权限', who: '陈志远', role: '召集人', object: '张悦 · 数据范围', ip: '10.86.12.20', terminal: 'Chrome 128', watermark: 'WM-7F3A-16C2', signatureOk: true, diff: { from: '聚合数据', to: '受控环境' }, risk: false },
    { id: 'L07', time: '昨天 16:22', type: '发布', who: '系统', role: '—', object: '2026年8月 月告知 → 52 家', ip: '—', terminal: 'Chrome 128', watermark: 'WM-7F3A-17C2', signatureOk: true, diff: null, risk: false },
    { id: 'L08', time: '昨天 15:05', type: '登录', who: '钱丽', role: '县区医保', object: '政务微信扫码', ip: '10.86.90.4', terminal: 'Chrome 128', watermark: 'WM-7F3A-18C2', signatureOk: true, diff: null, risk: false },
  ],
  selectedId: 'L03',
}

/**
 * ACTIONS: none — the audit log is read-only (filtering / selecting rows are pure UI).
 * Live data comes from the audit API instead of sendAction:
 *   GET /audit?type=&actor=&from=&to=&offHours=&cursor=&limit= → { items, nextCursor, today }
 *   GET /audit/{id} · GET /audit/verify → { valid, checked, brokenAt }
 *   GET /audit/export.csv?(same filters) — UTF-8 BOM CSV; the server records the export itself as A14 exportAudit.
 */
