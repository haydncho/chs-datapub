/** B3 对标 PK (机构端) — seed data (same shape as GET /api/v1/pages/B3). */

/** 档位: pct = 匿名分位 (only own position), anon = 匿名编号, named = 具名 */
export type B3Tier = 'pct' | 'anon' | 'named'

export interface B3Metric {
  name: string
  tier: B3Tier
  /** larger value is better */
  higherIsBetter: boolean
  /**
   * one value per peer, same order as `peers` (stored payload / bureau view, and 具名 metrics).
   * A hospital viewer never gets it for 匿名分位 / 匿名编号 metrics — see `own` / `others`.
   */
  values?: number[]
  /** hospital view of an anonymous metric: 本院's value … */
  own?: number
  /** … and the other institutions' values as an unordered distribution (ascending, not tied to `peers`) */
  others?: number[]
  /** formatted own value shown on the right */
  ownValue: string
}

export interface B3Data {
  subtitle: string
  /** 同级组 institutions (full names) */
  peers: string[]
  /** index of 本院 in `peers` (-1: the viewer's institution is not in this peer group) */
  ownIndex: number
  metrics: B3Metric[]
  /** metric shown in the 具名排行 panel */
  ranking: { metric: string; title: string; note: string }
  /** set by the server when the viewer's institution has no data here */
  noOwnData?: boolean
}

export const B3_SEED: B3Data = {
  subtitle: '同级组 市三级 · 6 家 · 档位由医保局按指标设定',
  peers: ['示例市妇幼保健院', '示例市第二人民医院', '示例市第一人民医院', '示例市中医院', '示例市第三人民医院', '示例市肿瘤医院'],
  ownIndex: 2,
  metrics: [
    { name: 'CMI值', tier: 'pct', higherIsBetter: true, values: [1.31, 1.18, 1.12, 1.06, 0.98, 0.91], ownValue: '1.12' },
    { name: '费用消耗指数', tier: 'pct', higherIsBetter: false, values: [0.92, 0.97, 1.04, 1.01, 1.08, 1.12], ownValue: '1.04' },
    { name: '时间消耗指数', tier: 'pct', higherIsBetter: false, values: [0.94, 0.99, 0.97, 1.0, 1.03, 1.06], ownValue: '0.97' },
    { name: '次均费用(千元)', tier: 'anon', higherIsBetter: false, values: [11.8, 12.4, 12.9, 13.4, 14.1, 15.2], ownValue: '12.9' },
    { name: '医保外费用占比(%)', tier: 'anon', higherIsBetter: false, values: [3.1, 4.2, 6.8, 5.0, 5.6, 7.4], ownValue: '6.8%' },
    { name: '结算清单质控率(%)', tier: 'named', higherIsBetter: true, values: [98.3, 97.4, 96.4, 96.2, 95.1, 94.0], ownValue: '96.4%' },
  ],
  ranking: { metric: '结算清单质控率(%)', title: '具名排行 · 结算清单质控率', note: '经召集人审批开放具名' },
}

/** before the server answers, a hospital outside the stored data sees no institution's figures */
export const B3_EMPTY: B3Data = {
  ...B3_SEED,
  subtitle: '暂无本院对标数据',
  peers: [],
  ownIndex: -1,
  metrics: B3_SEED.metrics.map(m => ({ name: m.name, tier: m.tier, higherIsBetter: m.higherIsBetter, others: [], ownValue: '—' })),
  ranking: { ...B3_SEED.ranking, metric: '' },
}

/**
 * ACTIONS: none — B3 is read-only.
 */
