/** A4 指标配置 — seed data (same JSON as GET /api/v1/pages/A4). */

/** 监测维度: 钱 基金运行 / 效 支付效率 / 错 质量监管 */
export type A4Group = '钱' | '效' | '错'
/** 指标来源: 国家底稿(必选) / 地方增选 / 仅内部 */
export type A4Source = 'national' | 'local' | 'internal'
/** 对标档位 */
export type A4Tier = 'pct' | 'anon' | 'named' | 'none'
export type A4Status = 'on' | 'review' | 'draft' | 'hold'

/** one entry of an indicator's own 版本记录 (newest first) */
export interface A4Version {
  version: string
  date: string
  author: string
  note: string
  /** an open request (e.g. 档位变更审批中) */
  pending?: boolean
}

export interface A4Indicator {
  name: string
  group: A4Group
  /** 监测子域 (目录二级) */
  domain: string
  source: A4Source
  tier: A4Tier
  freq: string
  version: string
  status: A4Status
  /** 被引用报告数 */
  refs: number
  numerator: string
  denominator: string
  /** 版本记录 of this indicator, newest first */
  history: A4Version[]
  /** server state: requested 对标档位, waiting for the 召集人 */
  pendingTier?: A4Tier
  /** server state: already in the target 发布包 */
  inPackage?: boolean
  /** wizard submissions (server state): 审批编号 and the wizard fields */
  approvalNo?: string
  internalOnly?: boolean
  filters?: string[]
  dims?: string[]
  chart?: string
  granularity?: string
  note?: string
  submittedBy?: string
}

export interface A4WizardTier {
  id: Exclude<A4Tier, 'none'>
  name: string
  desc: string
  tag: string
}

export interface A4Data {
  /** 全库统计 (header subtitle) */
  libraryTotal: number
  libraryNational: number
  libraryLocal: number
  libraryInternal: number
  /** 加入发布包的目标包名 */
  targetPackage: string
  indicators: A4Indicator[]
  /** 受众可见性矩阵的 7 类受众 */
  audiences: string[]
  /** 新建指标向导 */
  wizard: {
    /** placeholder of the name field */
    name: string
    draftVersion: string
    approver: string
    /** next 审批编号 the server will issue (server-side; refreshed after each submission) */
    approvalNo: string
    tiers: A4WizardTier[]
    previewOrgs: string[]
    /** option lists (the server validates against the same lists) */
    freqs: string[]
    dims: string[]
    defaultDims: string[]
    charts: string[]
    granularities: string[]
  }
}

export const A4_SEED: A4Data = {
  libraryTotal: 46,
  libraryNational: 28,
  libraryLocal: 16,
  libraryInternal: 2,
  targetPackage: '2026年8月 月告知发布包',
  indicators: [
    { name: '例均基金差额', group: '钱', domain: '费用与支付', source: 'national', tier: 'pct', freq: '月', version: 'v2.1', status: 'on', refs: 14, numerator: '(Σ医保记账金额 − ΣDRG支付标准)', denominator: '出院病例数', history: [{ version: 'v2.1', date: '2026-07-02', author: '李华', note: '同级分组由四档调整为五档' }, { version: 'v2.0', date: '2026-03-15', author: '王倩', note: '小样本阈值 3 → 5 家,经召集人审批' }] },
    { name: '次均总费用', group: '钱', domain: '费用与支付', source: 'national', tier: 'pct', freq: '月', version: 'v1.4', status: 'on', refs: 12, numerator: 'Σ住院总费用', denominator: '出院病例数', history: [{ version: 'v1.4', date: '2026-06-18', author: '李华', note: '剔除特例单议病例' }, { version: 'v1.3', date: '2026-01-10', author: '李华', note: '口径对齐结算清单 v2026.1' }] },
    { name: '医保外费用占比', group: '钱', domain: '费用与支付', source: 'local', tier: 'pct', freq: '季', version: 'v1.0', status: 'review', refs: 3, numerator: 'Σ医保外费用', denominator: 'Σ住院总费用', history: [{ version: 'v1.0', date: '2026-08-20', author: '王倩', note: '新建 · 提交上线审批 ZB-2026-0902' }] },
    { name: 'CMI值', group: '效', domain: '病组结构', source: 'national', tier: 'anon', freq: '月', version: 'v1.2', status: 'on', refs: 11, numerator: 'Σ(病组权重 × 病例数)', denominator: '总病例数', history: [{ version: 'v1.2', date: '2026-05-06', author: '李华', note: '对标档位由匿名分位调整为匿名编号,经召集人审批' }, { version: 'v1.1', date: '2025-12-01', author: '李华', note: '病组权重更新为 2026 版' }] },
    { name: '费用消耗指数', group: '效', domain: '效率指数', source: 'national', tier: 'pct', freq: '月', version: 'v1.0', status: 'on', refs: 9, numerator: 'Σ(本院例均 ÷ 全市例均 × 病例数)', denominator: '总病例数', history: [{ version: 'v1.0', date: '2025-11-20', author: '李华', note: '首版上线' }] },
    { name: '时间消耗指数', group: '效', domain: '效率指数', source: 'national', tier: 'pct', freq: '月', version: 'v1.0', status: 'on', refs: 9, numerator: 'Σ(本院住院日 ÷ 全市住院日 × 病例数)', denominator: '总病例数', history: [{ version: 'v1.0', date: '2025-11-20', author: '李华', note: '首版上线' }] },
    { name: '床日付费例均费用', group: '效', domain: '床日付费', source: 'local', tier: 'pct', freq: '月', version: 'v0.8', status: 'draft', refs: 0, numerator: 'Σ床日费用', denominator: '床日数', history: [{ version: 'v0.8', date: '2026-09-02', author: '王倩', note: '草稿 · 补充床日数口径' }] },
    { name: '结算清单质控率', group: '错', domain: '编码质量', source: 'local', tier: 'named', freq: '月', version: 'v1.3', status: 'on', refs: 8, numerator: '质控通过清单数', denominator: '上传清单数', history: [{ version: 'v1.3', date: '2026-04-12', author: '李华', note: '对标档位调整为具名PK与排行,经召集人审批' }, { version: 'v1.2', date: '2026-01-08', author: '李华', note: '质控规则增至 214 条' }] },
    { name: '14天再住院率', group: '错', domain: '医疗质量', source: 'local', tier: 'pct', freq: '季', version: 'v0.9', status: 'hold', refs: 2, numerator: '14天内非计划再住院人次', denominator: '出院人次', history: [{ version: 'v0.9', date: '2026-08-30', author: '王倩', note: '本期暂缓:电子病案依赖待复核' }] },
    { name: '异地就医基金支出占比', group: '钱', domain: '异地就医', source: 'national', tier: 'pct', freq: '季', version: 'v1.0', status: 'on', refs: 5, numerator: 'Σ异地就医基金支出', denominator: 'Σ统筹基金支出', history: [{ version: 'v1.0', date: '2026-02-14', author: '李华', note: '首版上线' }] },
    { name: '中医优势病种例均费用', group: '钱', domain: '专病专项', source: 'local', tier: 'pct', freq: '季', version: 'v1.1', status: 'on', refs: 2, numerator: 'Σ中医优势病种费用', denominator: '病例数', history: [{ version: 'v1.1', date: '2026-06-01', author: '王倩', note: '病种目录增至 12 个' }, { version: 'v1.0', date: '2025-12-20', author: '王倩', note: '首版上线' }] },
    { name: '单病例费用明细', group: '错', domain: '稽核明细', source: 'internal', tier: 'none', freq: '月', version: 'v1.0', status: 'on', refs: 0, numerator: '—', denominator: '—', history: [{ version: 'v1.0', date: '2025-10-09', author: '李华', note: '首版 · 仅内部' }] },
    { name: '参保人就医轨迹', group: '错', domain: '稽核明细', source: 'internal', tier: 'none', freq: '月', version: 'v1.0', status: 'on', refs: 0, numerator: '—', denominator: '—', history: [{ version: 'v1.0', date: '2025-10-09', author: '李华', note: '首版 · 仅内部' }] },
  ],
  audiences: ['市三级', '县三级', '二级', '一级', '县区医保', '专家组', '省级汇总'],
  wizard: {
    name: '术前平均住院日',
    draftVersion: 'v0.1',
    approver: '陈志远(召集人)',
    approvalNo: 'ZB-2026-0917',
    tiers: [
      { id: 'pct', name: '匿名分位', desc: '机构仅见本院在同级中的分位', tag: '默认' },
      { id: 'anon', name: '匿名编号', desc: '如“三级医院A”,不具名横比', tag: '需审批' },
      { id: 'named', name: '具名PK与排行', desc: '显示机构名称与名次', tag: '需审批' },
    ],
    previewOrgs: ['第一人民医院(市三级)', '甲县人民医院(县三级)', '召集人全量'],
    freqs: ['月', '季', '年'],
    dims: ['机构', '等级', '病组', '时间', '县区', '险种'],
    defaultDims: ['机构', '等级', '病组', '时间'],
    charts: ['分位条', '趋势线', '分组柱', '散点'],
    granularities: ['统筹区', '县区', '机构', '病组'],
  },
}

/**
 * ACTIONS:
 * addToPackage({ indicators: string[], package: string }) — 加入发布包: add the checked (visible) indicators
 *   (never 仅内部 ones) to the current month's publish package; refused when all are already in it;
 * requestTierChange({ indicator: string, from: A4Tier, to: A4Tier }) — 提交审批: request a change of
 *   the indicator's 对标档位; pending (indicators[].pendingTier) until the 召集人 approves, publishing keeps
 *   the old tier meanwhile; one open request per indicator;
 * submitIndicator({ name, group, domain, freq, numerator, denominator, filters: string[], dims: string[],
 *   tier: A4Tier, internalOnly: boolean, chart, granularity, note }) — 提交上线审批 from the 新建指标 wizard.
 *   The server validates (required / length / special characters / duplicate name / 仅内部 ⇒ tier none),
 *   issues the 审批编号 (ZB-<year>-<seq>, never reused) and lists the indicator as 审批中 (status review).
 */
