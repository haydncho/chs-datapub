/** A7 病种专题工作台 — seed data (same shape as GET /api/v1/pages/A7). */

export type A7Tone = 'brand' | 'violet' | 'warn'
export type A7TrackState = 'done' | 'cur' | 'todo'

export interface A7Topic {
  code: string
  name: string
  /** green pill, e.g. 选题推荐 · 得分 97 · 已采纳 */
  recommendTag: string
  /** brand pill */
  envTag: string
  /** grey meta line next to the pills */
  meta: string
}

export interface A7Collaborator { name: string; role: string; tone: A7Tone }

export interface A7TrackStep {
  label: string
  /** small grey text; ignored when `progress` is true (rendered as approved/7) */
  sub: string
  state: A7TrackState
  /** this step shows the live 七段成稿 progress */
  progress?: boolean
  /** A8 发布流程 step this track step corresponds to (the track follows the A8 task) */
  a8Step?: number
}

/** one traced number: the value as written in the draft and where it comes from */
export interface A7Source { value: string; source: string }

export interface A7Section {
  name: string
  /** LLM draft as alternating parts: even index = prose, odd index = data-bound number */
  draft: string[]
  /** 数据核查: every data-bound number of this section with its source */
  sources: A7Source[]
}

export interface A7Comment {
  /** 0-based section index */
  section: number
  who: string
  role: string
  text: string
  time: string
  resolved: boolean
  /** server state: pushed to 意见与申诉 (A10) as this item */
  pushedAs?: string
}

export type A7KpiTone = 'plain' | 'bad'
export interface A7Kpi {
  label: string
  value: string
  sub: string
  /** bad = red card / red value */
  tone: A7KpiTone
  /** sub line coloured red (e.g. 同比 +8.4%) */
  subBad?: boolean
}

export interface A7Tier { name: string; pct: number }
export interface A7StructureRow { name: string; shares: number[] }
export interface A7Behaviour { name: string; rate: number; multiplier: number }

export type A7WaterfallKind = 'city' | 'patient' | 'behaviour' | 'deviant'
export interface A7WaterfallBar {
  label: string
  /** start of the bar (元) */
  base: number
  /** bar length (元) */
  value: number
  display: string
  kind: A7WaterfallKind
}

export interface A7Dumbbell {
  label: string
  benchmark: number
  deviant: number
  min: number
  max: number
  gap: string
}

export interface A7OptPart { name: string; value: number; pct: number }
export interface A7Advice { side: string; tone: 'brand' | 'ok'; items: string[] }
export interface A7Version { title: string; meta: string; current: boolean }

/** an adopted topic (server state from A6) */
export interface A7TopicRef {
  id: string
  code: string
  title: string
  kind: string
  score: number
  facts: { k: string; v: string }[]
  taskId?: string
}

/** review state of one topic (server state) */
export interface A7Review {
  approved: number[]
  resolved: number[]
  /** comment index → A10 item id */
  pushed: Record<string, string>
  submitted: boolean
  submittedAt?: string
  submittedBy?: string
  /** newest first: 重新生成 entries added on top of the seeded versions */
  versions: A7Version[]
}

export interface A7Data {
  /** id of the topic this seeded manuscript belongs to (A6 id) */
  topicId: string
  topic: A7Topic
  collaborators: A7Collaborator[]
  track: A7TrackStep[]
  docMeta: string
  sections: A7Section[]
  /** sections already approved (0-based) */
  approved: number[]
  checkNote: string
  comments: A7Comment[]
  versions: A7Version[]
  overview: { kpis: A7Kpi[]; tierTitle: string; tiers: A7Tier[] }
  structure: { categories: string[]; rows: A7StructureRow[] }
  behaviour: { headers: string[]; rateMax: number; multMin: number; multMax: number; items: A7Behaviour[] }
  waterfall: { scaleMax: number; bars: A7WaterfallBar[]; note: string }
  dumbbell: { benchmarkLegend: string; deviantLegend: string; rows: A7Dumbbell[] }
  optimisation: { notice: string; label: string; total: string; unit: string; parts: A7OptPart[] }
  advice: A7Advice[]
  export: { docNo: string; subtitle: string; title: string; fileName: string; footNote: string }
  /** server state: adopted topics by id (A6) */
  topics?: Record<string, A7TopicRef>
  /** server state: review per topic id */
  reviews?: Record<string, A7Review>
  /** server state: A8 step of each topic's 专题 task */
  taskSteps?: Record<string, number>
}

/**
 * BR25 · 2026年8月. Same caliber as A6 / the analytics service (drg_group): 1,420 例, 次均 14,200 元,
 * 例均基金差额 +1,860 元 → 逆差 264.1 万元, 综合得分 97. Cross-section checks:
 * §04 全市均值 14,200 + 患者差异 1,850 + 行为差异 3,010 = 偏离组均值 19,060 (= §05 偏离组例均费用);
 * §05 标杆组 11,860 → 差 7,200; §06 3,010 元 × 偏离组月均 356 例 × 12 = 1,286 万元 = 412 + 368 + 506.
 */
export const A7_SEED: A7Data = {
  topicId: 'T-BR25',
  topic: {
    code: 'BR25',
    name: '脑缺血性疾患,伴并发症 · 2026年8月专题',
    recommendTag: '选题推荐 · 得分 97 · 已采纳',
    envTag: '受控分析环境',
    meta: '18 家机构 · 1,420 例 · 2026年8月',
  },
  collaborators: [
    { name: '张悦', role: '分析', tone: 'brand' },
    { name: '李华', role: '编辑', tone: 'violet' },
    { name: '刘教授', role: '审阅', tone: 'warn' },
  ],
  track: [
    { label: '选题采纳', sub: '09-15', state: 'done', a8Step: 1 },
    { label: '七段成稿', sub: '', state: 'cur', progress: true, a8Step: 3 },
    { label: '机构核对 ∥ 专家组审核', sub: '18 家 · 3 人', state: 'todo', a8Step: 4 },
    { label: '召集人审批', sub: '', state: 'todo', a8Step: 5 },
    { label: '定向发布', sub: '收治 18 家', state: 'todo', a8Step: 6 },
  ],
  docMeta: '示例市医保数据工作组 · 病种专题 · 核对稿 v3 · 2026-10-03',
  sections: [
    {
      name: '整体描述',
      draft: ['本月 BR25 全市收治 ', '1,420 例', ',次均总费用 ', '14,200 元', ',同比上升 ', '8.4%', '。例均基金差额 ', '+1,860 元', ',逆差总额 ', '264.1 万元', ',为本期逆差总额最大的病组;病例集中在市三级机构,占 ', '52%', '。'],
      sources: [
        { value: '1,420 例', source: '结算明细 · 批次 B20260905-01' },
        { value: '14,200 元', source: '指标卡 次均总费用 v1.4' },
        { value: '8.4%', source: '指标卡 次均总费用 v1.4 · 同比 2025年8月' },
        { value: '+1,860 元', source: '指标卡 例均基金差额 v2.1' },
        { value: '264.1 万元', source: '1,420 例 × 1,860 元(例均基金差额 v2.1)' },
        { value: '52%', source: '机构等级维表 2026.09' },
      ],
    },
    {
      name: '费用结构',
      draft: ['偏离组药品与耗材合计占比 ', '44%', ',高于标杆组 ', '13 个百分点', ';治疗及护理类占比偏低,提示费用结构以检查与药品驱动为主。'],
      sources: [
        { value: '44%', source: '结算明细费用分类 · 偏离组 5 家 · 药品 33% + 耗材 11%' },
        { value: '13 个百分点', source: '标杆组 4 家 药品 24% + 耗材 7% = 31%' },
      ],
    },
    {
      name: '关键行为',
      draft: ['五项行为中,“转入ICU”费用倍率最高(', '×2.38', '),但发生率仅 ', '6.3%', ';“使用辅助用药”发生率最高(', '34.1%', '),对总费用影响最大。'],
      sources: [
        { value: '×2.38', source: '受控环境聚合结果 · 诊疗行为模型 B20260906-01 · 已脱敏' },
        { value: '6.3%', source: '电子病案 · 转科记录 · 批次 B20260906-01' },
        { value: '34.1%', source: '结算明细 · 辅助用药目录 2026 版' },
      ],
    },
    {
      name: '差异归因',
      draft: ['偏离组例均费用 ', '19,060 元', ',比全市均值 ', '14,200 元', ' 高 ', '4,860 元', ',其中患者差异约 ', '1,850 元', ',行为差异约 ', '3,010 元', '。行为差异主要来自重复检查与辅助用药。'],
      sources: [
        { value: '19,060 元', source: '偏离组 5 家结算明细 · 批次 B20260905-01' },
        { value: '14,200 元', source: '指标卡 次均总费用 v1.4(同 §01)' },
        { value: '4,860 元', source: '19,060 − 14,200' },
        { value: '1,850 元', source: '多层回归 · 患者特征项(年龄、合并症、入院途径)' },
        { value: '3,010 元', source: '多层回归 · 诊疗行为项(R² = 0.64)' },
      ],
    },
    {
      name: '标杆对比',
      draft: ['标杆组 4 家机构例均费用 ', '11,860 元', ',偏离组 5 家为 ', '19,060 元', ';偏离组重复检查发生率是标杆组的 ', '2.7 倍', '。'],
      sources: [
        { value: '11,860 元', source: '标杆组 4 家结算明细 · 批次 B20260905-01' },
        { value: '19,060 元', source: '偏离组 5 家结算明细(同 §04)' },
        { value: '2.7 倍', source: '重复检查发生率 38% ÷ 14%(电子病案 · 检查记录)' },
      ],
    },
    {
      name: '优化空间',
      draft: ['按行为差异 ', '3,010 元/例', ' × 偏离组月均 ', '356 例', ' × 12 个月测算,理论优化空间年化约 ', '1,286 万元', '。该数值为理论测算,不作为控费指标下达。'],
      sources: [
        { value: '3,010 元/例', source: '§04 行为差异' },
        { value: '356 例', source: '偏离组 5 家 2026年1–8月 月均病例数' },
        { value: '1,286 万元', source: '3,010 × 356 × 12 = 1,285.9 万元' },
      ],
    },
    {
      name: '医保侧与医院侧建议',
      draft: ['建议医保侧复核分组边界并完善审核规则;医院侧推进检查结果互认与辅助用药管理。'],
      sources: [],
    },
  ],
  approved: [0, 1, 2],
  checkNote: '蓝色高亮数字绑定数据源;审定后转为正文样式',
  comments: [
    { section: 0, who: '刘教授', role: '专家组', text: '“病例集中在市三级”建议补充县三级占比,便于县区理解。', time: '10-02 15:20', resolved: false },
    { section: 3, who: '甲县人民医院', role: '机构核对', text: '本院特例单议 12 例应剔除,否则偏离组均值偏高。', time: '10-03 09:12', resolved: false },
    { section: 3, who: '王倩', role: '行政管理组', text: '已核实,批复文号已附,将在 v4 剔除。', time: '10-03 11:40', resolved: false },
    { section: 5, who: '刘教授', role: '专家组', text: '优化空间口径请在方法卡中写明,避免被理解为控费目标。', time: '10-02 16:05', resolved: false },
  ],
  versions: [
    { title: 'v3 · 核对稿', meta: '10-03 · 张悦 · 更新 §4 剔除特例单议', current: true },
    { title: 'v2', meta: '09-28 · 李华 · 补充 §5 标杆对比', current: false },
    { title: 'v1 · 大模型初稿', meta: '09-20 · 系统生成', current: false },
  ],
  overview: {
    kpis: [
      { label: '病例数', value: '1,420', sub: '18 家收治', tone: 'plain' },
      { label: '次均总费用', value: '14,200', sub: '同比 +8.4%', tone: 'plain', subBad: true },
      { label: '例均基金差额', value: '+1,860', sub: '逆差 264.1 万', tone: 'bad' },
      { label: '平均住院日', value: '10.8', sub: '同级中位 9.6', tone: 'plain' },
    ],
    tierTitle: '病例按机构等级分布',
    tiers: [
      { name: '市三级', pct: 52 },
      { name: '县三级', pct: 23 },
      { name: '二级', pct: 19 },
      { name: '一级', pct: 6 },
    ],
  },
  structure: {
    categories: ['药品', '耗材', '检查检验', '治疗', '护理及其他'],
    rows: [
      { name: '全市', shares: [31, 9, 22, 18, 20] },
      { name: '标杆组', shares: [24, 7, 20, 24, 25] },
      { name: '偏离组', shares: [33, 11, 25, 15, 16] },
    ],
  },
  behaviour: {
    headers: ['诊疗行为', '发生率', '费用倍率(×1.0 – ×2.5)'],
    rateMax: 40,
    multMin: 1,
    multMax: 2.5,
    items: [
      { name: '入院72小时内重复检查', rate: 27.4, multiplier: 1.29 },
      { name: '使用辅助用药', rate: 34.1, multiplier: 1.22 },
      { name: '使用高值耗材', rate: 18.2, multiplier: 1.6 },
      { name: '转入ICU', rate: 6.3, multiplier: 2.38 },
      { name: '康复治疗介入', rate: 15.0, multiplier: 1.3 },
    ],
  },
  waterfall: {
    scaleMax: 20000,
    bars: [
      { label: '全市均值', base: 0, value: 14200, display: '14,200', kind: 'city' },
      { label: '患者差异', base: 14200, value: 1850, display: '+1,850', kind: 'patient' },
      { label: '行为差异', base: 16050, value: 3010, display: '+3,010', kind: 'behaviour' },
      { label: '偏离组均值', base: 0, value: 19060, display: '19,060', kind: 'deviant' },
    ],
    note: '单位:元/例 · 多层回归 R² = 0.64 · 患者差异含年龄、合并症、入院途径',
  },
  dumbbell: {
    benchmarkLegend: '标杆组(4 家,匿名)',
    deviantLegend: '偏离组(5 家,匿名)',
    rows: [
      { label: '例均费用(元)', benchmark: 11860, deviant: 19060, min: 10000, max: 20000, gap: '+7,200' },
      { label: '平均住院日(天)', benchmark: 9.2, deviant: 12.6, min: 8, max: 14, gap: '+3.4' },
      { label: '药品占比(%)', benchmark: 24, deviant: 33, min: 15, max: 40, gap: '+9 pt' },
      { label: '重复检查发生率(%)', benchmark: 14, deviant: 38, min: 0, max: 45, gap: '×2.7' },
    ],
  },
  optimisation: {
    notice: '理论优化空间 · 不作为控费指标下达 · 仅供机构自我对照与医保侧规则评估',
    label: '理论优化空间(年化)',
    total: '1,286',
    unit: '万元',
    parts: [
      { name: '重复检查', value: 412, pct: 32 },
      { name: '辅助用药', value: 368, pct: 29 },
      { name: '住院日延长', value: 506, pct: 39 },
    ],
  },
  advice: [
    { side: '医保侧', tone: 'brand', items: ['1. 复核 BR25 与 BR21/BR23 分组边界', '2. “72 小时内重复检查”纳入智能审核提示', '3. 对偏离组开展病案编码专项质控'] },
    { side: '医院侧', tone: 'ok', items: ['1. 建立院内检查结果互认', '2. 规范辅助用药目录与处方点评', '3. 推进早期康复介入,缩短住院日'] },
  ],
  export: {
    docNo: 'YJD-2026-1004-01',
    subtitle: 'BR25 专题 · 核对稿 v3',
    title: '意见单预览',
    fileName: '意见单_BR25_v3.csv',
    footNote: '导出文件带水印与追溯编号',
  },
}

/**
 * ACTIONS (all take `topic`: the A6 topic id, default the seeded T-BR25; other topics must be adopted in A6):
 * approveSection({ topic, section: number }) — 人工审定第 section 段(0-based),该段数字转为正文样式;
 * regenerateSection({ topic, section: number }) — 基于最新批次重新生成该段初稿;已审定的段落回到「待审定」,
 *   版本记录新增一条;
 * resolveComment({ topic, comment: number }) — 将批注(comments 下标)标记为已处理;
 * exportCommentsExcel({ topic, docNo, fileName }) — 意见单导出(服务端登记并返回追溯编号与水印,前端生成 CSV);
 *   受控分析环境(委托分析)身份不可导出(含机构具名明细、未经审核);
 * pushComments({ topic, docNo }) — 将未处理、未推送的批注推送至意见与申诉(A10,生成意见条目);已推送的不重复推送;
 * submitReview({ topic, approved: number[] }) — 7/7 审定后提交(仅一次),A8 专题任务由「分析成稿」进入「专家组审核」。
 * Server state is merged into the payload as topics / reviews / taskSteps (and approved / comments[].resolved for T-BR25).
 */
