import { computed, inject, provide, reactive, ref, watch, type InjectionKey, type Ref } from 'vue'
import { getJson, runAction } from '@/api/client'
import { session } from '@/app/session'
import { say } from '@/app/shell'
import { A8_SEED, type A8Data, type A8LogEntry, type A8Tab, type A8Task } from '@/mock/A8'

export type QueueFilter = '全部' | '待我审批' | '即将到期'
export type SegBatch = '全部' | '第一批' | '第二批'
export type SegGroup = '不限' | '收治 BR25' | '收治 GG19'

/** due within a day or overdue → urgent (live `daysLeft` from the server; the offline seed only has the text) */
export const isUrgent = (t: A8Task) =>
  t.daysLeft != null ? t.daysLeft <= 1 : t.daysLeft === undefined && /剩 1 天|逾期|今日到期/.test(t.due)

export interface CheckView {
  text: string
  ok: boolean
  /** advisory only — shown with '!' but does not block 批准 */
  warn: boolean
  tab: A8Tab
}

export function createA8Store(data: Ref<A8Data>) {
  const d = computed(() => data.value)

  /** the logged-in identity decides what the panel offers (the server checks again) */
  const role = computed(() => session.current?.identity.role ?? '')
  const isConvener = computed(() => role.value === 'convener')
  const canWork = computed(() => role.value === 'convener' || role.value === 'admin')
  const actorName = computed(() => session.current?.user.name ?? '')

  const flowId = ref(data.value.currentTaskId)
  const busy = ref(false)

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
  const confirmOpen = ref(false)

  const cur = computed(() => d.value.tasks.find(t => t.id === flowId.value) ?? d.value.tasks[0]!)
  const step = computed(() => cur.value.step)
  const stepName = (i: number) => d.value.steps[i - 1] ?? ''
  const archived = computed(() => step.value >= 10 || cur.value.status === 'archived' || cur.value.status === 'withdrawn')

  /** reload the server read model (after an action, or when the page was stale) */
  async function refresh() {
    try {
      const remote = await getJson<A8Data>('/pages/A8')
      if (remote && typeof remote === 'object') data.value = { ...A8_SEED, ...remote }
    } catch { /* keep what is shown */ }
  }

  /** restore the scope filters to what the task was approved with */
  function loadScope() {
    const f = cur.value.scope?.filters
    if (f) {
      sTiers.value = [...f.tiers]
      sDist.value = [...f.districts]
      sBatch.value = (f.batch as SegBatch) ?? '全部'
      sGrp.value = (f.drg as SegGroup) ?? '不限'
    }
  }
  watch(data, () => {
    if (!d.value.tasks.some(t => t.id === flowId.value)) flowId.value = d.value.currentTaskId
    loadScope()
  })

  // ---- queue ----
  const filterMatch = (t: A8Task, f: QueueFilter) =>
    f === '全部' || (f === '待我审批' ? t.step === 5 : isUrgent(t) && t.step < 10)
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
    loadScope()
  }

  // ---- scope ----
  /** the approved scope is fixed once the task left the approval node */
  const scopeLocked = computed(() => step.value !== 5 || !isConvener.value)
  const saved = computed(() => (step.value >= 6 ? cur.value.scope : undefined))
  const coverage = computed(() => {
    const s = saved.value
    if (s) return d.value.institutions.filter(h => s.institutions.includes(h.name))
    return d.value.institutions.filter(
      h =>
        sTiers.value.includes(h.tier) &&
        sDist.value.includes(h.district) &&
        (sBatch.value === '全部' || h.batch === sBatch.value) &&
        (sGrp.value === '不限' || (sGrp.value === '收治 BR25' ? h.br25 : h.gg19)),
    )
  })
  const covN = computed(() => saved.value?.coverage ?? coverage.value.length)
  const covNames = computed(
    () => coverage.value.slice(0, 8).map(h => h.name).join('、') + (covN.value > 8 ? ' 等 ' + covN.value + ' 家' : ''),
  )
  const missTiers = computed(() =>
    scopeLocked.value
      ? []
      : d.value.tiers
        .map((_, i) => i)
        .filter(i => !sTiers.value.includes(i) && d.value.institutions.some(h => h.tier === i && h.br25)),
  )
  const toggle = <T>(arr: Ref<T[]>, v: T) => {
    if (scopeLocked.value) return
    arr.value = arr.value.includes(v) ? arr.value.filter(x => x !== v) : [...arr.value, v]
  }
  const fixMiss = () => {
    if (!scopeLocked.value) sTiers.value = [...sTiers.value, ...missTiers.value]
  }

  // ---- publish / sign (real sign-offs from report_signoff) ----
  const posted = computed(() => step.value >= 6 && step.value < 99)
  const signedSet = computed(() => new Set(cur.value.signed ?? []))
  const sigN = computed(() => (posted.value ? coverage.value.filter(h => signedSet.value.has(h.name)).length : 0))
  const unsignedAll = computed(() => coverage.value.filter(h => !signedSet.value.has(h.name)))
  const unsigned = computed(() => unsignedAll.value.slice(0, 8))
  const urged = computed(() => Object.fromEntries((cur.value.urged ?? []).map(n => [n, true])) as Record<string, boolean>)
  const signDue = computed(() => {
    const a = cur.value.approvedAt
    if (!a) return d.value.signRemain
    const due = new Date(a + 'T00:00:00')
    due.setDate(due.getDate() + 7)
    return '签收期至 ' + String(due.getMonth() + 1).padStart(2, '0') + '-' + String(due.getDate()).padStart(2, '0')
  })

  /** run an A8 action: the UI changes only after the server accepted it; a refusal is shown and the page reloads */
  async function act(action: string, payload: Record<string, unknown>, ok: (data: unknown) => void): Promise<boolean> {
    if (busy.value) return false
    busy.value = true
    try {
      const r = await runAction<{ result?: unknown }>('A8', action, payload)
      if (!r.ok) {
        say(r.error || '操作未成功')
        await refresh()
        return false
      }
      ok(r.data?.result)
      await refresh()
      return true
    } finally {
      busy.value = false
    }
  }

  const urge = (name: string) => {
    if (urged.value[name] || !canWork.value) return
    void act('urgeSign', { taskId: cur.value.id, institution: name }, () => say('已向 ' + name + ' 发送催办 · 政务微信 + 短信'))
  }
  const urgeAll = () => {
    if (!canWork.value) return
    void act('urgeSignAll', { taskId: cur.value.id }, res => {
      const n = (res as { urged?: string[] } | undefined)?.urged?.length ?? 0
      say(n ? '已向 ' + n + ' 家未签收机构发送催办' : '未签收机构均已催办过')
    })
  }

  // ---- checklist ----
  const checks = computed<CheckView[]>(() => [
    ...d.value.checks.map(c => ({ text: c.text, ok: c.ok, warn: c.level === 'warn', tab: c.tab })),
    covN.value === 0
      ? { text: '覆盖 0 家 · 不能发布', ok: false, warn: false, tab: 'scope' as A8Tab }
      : {
          text: '覆盖 ' + covN.value + ' 家 · ' + (covN.value >= d.value.minCoverage ? '范围合理' : '低于常规覆盖,请确认'),
          ok: covN.value >= d.value.minCoverage,
          warn: covN.value < d.value.minCoverage,
          tab: 'scope' as A8Tab,
        },
  ])
  /** a failing blocking check stops 批准 (the server checks the same) */
  const blocked = computed(() => checks.value.some(c => !c.ok && !c.warn))

  // ---- approval ----
  const addPhrase = (p: string) => {
    apText.value = (apText.value ? apText.value + ';' : '') + p
  }
  const openConfirm = () => {
    if (blocked.value) {
      say(covN.value === 0 ? '定向范围覆盖 0 家机构,不能批准发布' : '发布前检查未通过,不能批准发布')
      return
    }
    confirmOpen.value = true
  }
  const approve = async () => {
    if (blocked.value) {
      confirmOpen.value = false
      say('发布前检查未通过,不能批准发布')
      return
    }
    const id = cur.value.id
    const n = covN.value
    const done = await act('approvePublish', {
      taskId: id,
      comment: apText.value.trim(),
      coverage: n,
      institutions: coverage.value.map(h => h.name),
      filters: { tiers: [...sTiers.value], districts: [...sDist.value], batch: sBatch.value, drg: sGrp.value },
    }, () => {
      apText.value = ''
      tab.value = 'sig'
      say('已批准 · 定向发布至 ' + n + ' 家机构')
    })
    confirmOpen.value = false
    return done
  }
  const reject = async () => {
    if (!apText.value.trim()) {
      say('驳回需填写审批意见')
      return
    }
    const to = rjTo.value
    await act('rejectPublish', { taskId: cur.value.id, toStep: to, comment: apText.value.trim() }, () => {
      apText.value = ''
      say('已驳回至第 ' + to + ' 步「' + stepName(to) + '」')
    })
  }
  const submit = async () => {
    await act('submitForApproval', { taskId: cur.value.id }, () => say('已提交召集人审批'))
  }

  // ---- 更正 / 撤回 ----
  const revisions = computed(() => d.value.tasks.filter(t => t.origin === cur.value.id))
  const openRevision = computed(() => revisions.value.find(t => t.step < 10 && t.status !== 'archived' && t.status !== 'withdrawn'))
  const canFix = computed(() => canWork.value && posted.value && cur.value.status !== 'withdrawn' && !openRevision.value)
  const fixReason = ref('')
  const fixAct = async (kind: 'correction' | 'withdraw') => {
    if (!posted.value) {
      say('仅已发布的任务可发起更正或撤回')
      return
    }
    await act(kind === 'correction' ? 'startCorrection' : 'startWithdraw', { taskId: cur.value.id, reason: fixReason.value.trim() }, res => {
      fixReason.value = ''
      const nid = (res as { taskId?: string } | undefined)?.taskId
      say('已发起' + (kind === 'correction' ? '更正' : '撤回') + (nid ? ' · ' + nid : '') + ',将重新经过专家组审核与召集人审批')
    })
  }

  // ---- log: seeded history of the demo task + everything stored for the selected task ----
  const logs = computed<A8LogEntry[]>(() => [
    ...(cur.value.id === d.value.currentTaskId ? d.value.logs : []),
    ...(cur.value.logs ?? []),
  ])
  const lastReject = computed(() => [...(cur.value.logs ?? [])].reverse().find(l => /驳回/.test(l.tag)))

  return reactive({
    d, role, isConvener, canWork, actorName, busy, flowId, logs, tab, q, qf, sTiers, sDist, sBatch, sGrp, apText, rjTo, pvi,
    urged, confirmOpen, cur, step, stepName, archived, queueGroups, queueCount, filterCounts, selectTask,
    scopeLocked, coverage, covN, covNames, missTiers, toggle: (k: 'sTiers' | 'sDist', v: number | string) =>
      k === 'sTiers' ? toggle(sTiers, v as number) : toggle(sDist, v as string),
    fixMiss, posted, sigN, unsigned, unsignedAll, signDue, urge, urgeAll, checks, blocked, addPhrase, openConfirm,
    approve, reject, submit, revisions, openRevision, canFix, fixReason, fixAct, lastReject, refresh,
  })
}

export type A8Store = ReturnType<typeof createA8Store>
const KEY: InjectionKey<A8Store> = Symbol('A8Store')
export const provideA8 = (s: A8Store) => provide(KEY, s)
export const useA8 = () => inject(KEY)!

/** soft chip: brand-soft when on */
export const softChip = (on: boolean) =>
  on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3'
