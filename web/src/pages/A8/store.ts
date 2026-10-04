import { computed, inject, provide, reactive, ref, watch, type InjectionKey, type Ref } from 'vue'
import { sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { pad } from '@/lib/format'
import type { A8Data, A8LogEntry, A8Tab, A8Task } from '@/mock/A8'

export type QueueFilter = '全部' | '待我审批' | '即将到期'
export type SegBatch = '全部' | '第一批' | '第二批'
export type SegGroup = '不限' | '收治 BR25' | '收治 GG19'

const nowStamp = () => {
  const d = new Date()
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 剩 1 天 / 逾期 → urgent */
export const isUrgent = (t: A8Task) => /剩 1 天|逾期/.test(t.due)

export function createA8Store(data: Ref<A8Data>) {
  const d = computed(() => data.value)

  // ---- per-session demo state (seeded from data) ----
  const flowId = ref(data.value.currentTaskId)
  const flowStep = reactive<Record<string, number>>({})
  const logs = ref<A8LogEntry[]>([])
  const seedFromData = () => {
    for (const k of Object.keys(flowStep)) delete flowStep[k]
    for (const t of data.value.tasks) flowStep[t.id] = t.step
    logs.value = data.value.logs.map(l => ({ ...l }))
    if (!data.value.tasks.some(t => t.id === flowId.value)) flowId.value = data.value.currentTaskId
  }
  seedFromData()
  watch(data, seedFromData)

  const tab = ref<A8Tab>('pkg')
  const q = ref('')
  const qf = ref<QueueFilter>('全部')
  const sTiers = ref<number[]>([0, 1, 2, 3, 4])
  const sDist = ref<string[]>([...data.value.districts])
  const sBatch = ref<SegBatch>('全部')
  const sGrp = ref<SegGroup>('不限')
  const apText = ref('')
  const rjTo = ref(3)
  const pvi = ref(0)
  const urged = reactive<Record<string, boolean>>({})
  const confirmOpen = ref(false)

  const cur = computed(() => d.value.tasks.find(t => t.id === flowId.value) ?? d.value.tasks[0]!)
  const step = computed(() => flowStep[cur.value.id] ?? cur.value.step)
  const stepName = (i: number) => d.value.steps[i - 1] ?? ''

  // ---- queue ----
  const filterMatch = (t: A8Task, f: QueueFilter) =>
    f === '全部' || (f === '待我审批' ? flowStep[t.id] === 5 : isUrgent(t))
  const queueGroups = computed(() => {
    const kw = q.value.trim()
    return (['月告知', '季公布', '年通报', '专题', '提醒函', '更正'] as const)
      .map(g => ({
        g,
        items: d.value.tasks.filter(t => t.group === g && (!kw || t.name.includes(kw)) && filterMatch(t, qf.value)),
      }))
      .filter(g => g.items.length)
  })
  const queueCount = computed(() => queueGroups.value.reduce((a, g) => a + g.items.length, 0))
  const filterCounts = computed(() =>
    (['全部', '待我审批', '即将到期'] as QueueFilter[]).map(l => ({ l, n: d.value.tasks.filter(t => filterMatch(t, l)).length })),
  )
  const selectTask = (id: string) => {
    flowId.value = id
    confirmOpen.value = false
  }

  // ---- scope ----
  const coverage = computed(() =>
    d.value.institutions.filter(
      h =>
        sTiers.value.includes(h.tier) &&
        sDist.value.includes(h.district) &&
        (sBatch.value === '全部' || h.batch === sBatch.value) &&
        (sGrp.value === '不限' || (sGrp.value === '收治 BR25' ? h.br25 : h.gg19)),
    ),
  )
  const covN = computed(() => coverage.value.length)
  const covNames = computed(
    () => coverage.value.slice(0, 8).map(h => h.name).join('、') + (covN.value > 8 ? ' 等 ' + covN.value + ' 家' : ''),
  )
  const missTiers = computed(() =>
    d.value.tiers
      .map((_, i) => i)
      .filter(i => !sTiers.value.includes(i) && d.value.institutions.some(h => h.tier === i && h.br25)),
  )
  const toggle = <T>(arr: Ref<T[]>, v: T) => {
    arr.value = arr.value.includes(v) ? arr.value.filter(x => x !== v) : [...arr.value, v]
  }
  const fixMiss = () => {
    sTiers.value = [...sTiers.value, ...missTiers.value]
  }

  // ---- publish / sign ----
  const posted = computed(() => step.value >= 6 && step.value < 99)
  const sigN = computed(() => (posted.value ? Math.round(covN.value * d.value.signRate) : 0))
  const unsigned = computed(() => coverage.value.slice(sigN.value).slice(0, 6))
  const urge = (name: string) => {
    if (urged[name]) return
    urged[name] = true
    sendAction('A8', 'urgeSign', { taskId: cur.value.id, institution: name })
    say('已向 ' + name + ' 发送催办 · 政务微信 + 短信')
  }
  const urgeAll = () => {
    const names = unsigned.value.map(h => h.name)
    names.forEach(n => (urged[n] = true))
    sendAction('A8', 'urgeSignAll', { taskId: cur.value.id, institutions: names, count: covN.value - sigN.value })
    say('已向 ' + (covN.value - sigN.value) + ' 家未签收机构发送催办')
  }

  // ---- checklist ----
  const checks = computed(() => [
    ...d.value.checks,
    {
      text: '覆盖 ' + covN.value + ' 家 · ' + (covN.value >= d.value.minCoverage ? '范围合理' : '低于常规覆盖'),
      ok: covN.value >= d.value.minCoverage,
      tab: 'scope' as A8Tab,
    },
  ])

  // ---- approval ----
  const addPhrase = (p: string) => {
    apText.value = (apText.value ? apText.value + ';' : '') + p
  }
  const approve = () => {
    const id = cur.value.id
    const n = covN.value
    sendAction('A8', 'approvePublish', {
      taskId: id,
      comment: apText.value,
      coverage: n,
      institutions: coverage.value.map(h => h.name),
      filters: { tiers: [...sTiers.value], districts: [...sDist.value], batch: sBatch.value, drg: sGrp.value },
    })
    confirmOpen.value = false
    tab.value = 'sig'
    flowStep[id] = 6
    logs.value = [
      ...logs.value,
      { time: nowStamp(), who: '陈志远', tag: '召集人 · 批准', what: '批准发布' + (apText.value ? ':' + apText.value : '') },
      { time: nowStamp(), who: '系统', tag: '定向发布', what: '推送至 ' + n + ' 家机构 · 签收期 ' + d.value.signPeriod },
    ]
    apText.value = ''
    say('已批准 · 定向发布至 ' + n + ' 家机构')
  }
  const reject = () => {
    if (!apText.value.trim()) {
      say('驳回需填写审批意见')
      return
    }
    const id = cur.value.id
    const to = rjTo.value
    sendAction('A8', 'rejectPublish', { taskId: id, toStep: to, comment: apText.value })
    flowStep[id] = to
    logs.value = [...logs.value, { time: nowStamp(), who: '陈志远', tag: '召集人 · 驳回', what: '退回「' + stepName(to) + '」:' + apText.value }]
    apText.value = ''
    say('已驳回至第 ' + to + ' 步「' + stepName(to) + '」')
  }
  const fixAct = (kind: 'correction' | 'withdraw') => {
    sendAction('A8', kind === 'correction' ? 'startCorrection' : 'startWithdraw', { taskId: cur.value.id })
    say('已发起,将重新经过专家组审核与召集人审批')
  }

  return reactive({
    d, flowId, flowStep, logs, tab, q, qf, sTiers, sDist, sBatch, sGrp, apText, rjTo, pvi, urged, confirmOpen,
    cur, step, stepName, queueGroups, queueCount, filterCounts, selectTask,
    coverage, covN, covNames, missTiers, toggle: (k: 'sTiers' | 'sDist', v: number | string) =>
      k === 'sTiers' ? toggle(sTiers, v as number) : toggle(sDist, v as string),
    fixMiss, posted, sigN, unsigned, urge, urgeAll, checks, addPhrase, approve, reject, fixAct,
  })
}

export type A8Store = ReturnType<typeof createA8Store>
const KEY: InjectionKey<A8Store> = Symbol('A8Store')
export const provideA8 = (s: A8Store) => provide(KEY, s)
export const useA8 = () => inject(KEY)!

/** soft chip: brand-soft when on */
export const softChip = (on: boolean) =>
  on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3'
