import { computed, reactive, ref, watch, type Ref } from 'vue'
import { ApiError, authHeaders, getJson } from '@/api/client'
import type { A14Change, A14Data, A14EventType, A14Log } from '@/mock/A14'

/**
 * A14 live audit trail: GET /api/v1/audit (filters + cursor paging), /audit/verify (链式校验)
 * and /audit/export.csv. Until the API answers, the page keeps rendering the seed log and
 * filters it locally.
 */

const BASE = (import.meta.env.VITE_API_BASE as string | undefined) ?? '/api/v1'
const PAGE_SIZE = 50

/** every type the server can return (filter chips in live mode) */
export const LIVE_FILTERS = ['全部', '查阅', '审批', '配置', '导出', '权限', '发布', '登录', '删除', '其他']

interface ApiDiff { from: string; to: string; changes?: A14Change[] }
interface ApiItem {
  id: number
  at: string
  date: string
  time: string
  type: A14EventType
  actor: string
  who: string
  role: string
  page: string
  action: string
  label: string
  object: string
  ip: string
  terminal: string
  watermark: string
  chain: 'ok' | 'broken'
  offHours: boolean
  risk: boolean
  diff?: ApiDiff | null
  prevHash: string
  hash: string
}
interface ApiPage { items: ApiItem[]; nextCursor?: number | null; today: number }
interface ApiChain { valid: boolean; checked: number; brokenAt?: number | null }

export type ChainState =
  | { state: 'checking' }
  | { state: 'ok'; checked: number }
  | { state: 'broken'; checked: number; brokenAt: number }
  | { state: 'offline' }

export interface AuditFilters {
  type: string
  actor: string
  /** yyyy-mm-dd, inclusive */
  from: string
  to: string
  offHours: boolean
}

function toLog(e: ApiItem): A14Log {
  return {
    id: String(e.id),
    time: e.time,
    type: e.type,
    who: e.who,
    role: e.role,
    object: e.object,
    ip: e.ip,
    terminal: e.terminal,
    watermark: e.watermark,
    signatureOk: e.chain === 'ok',
    diff: e.diff ? { from: e.diff.from, to: e.diff.to } : null,
    risk: e.risk,
    date: e.date,
    offHours: e.offHours,
    riskNote: e.risk ? `异常:非工作时间${e.type}(22:00–06:00)· 请安全员核查` : undefined,
    page: e.page,
    action: e.action,
    hash: e.hash,
    prevHash: e.prevHash,
    changes: e.diff?.changes,
  }
}

/** hour of a seed time string ("02:14:36", "昨天 17:40") */
function seedHour(time: string): number {
  const m = /(\d{1,2}):\d{2}/.exec(time)
  return m ? Number(m[1]) : 12
}

export function isOffHoursTime(time: string): boolean {
  const h = seedHour(time)
  return h >= 22 || h < 6
}

function query(f: AuditFilters, extra: Record<string, string> = {}): string {
  const p = new URLSearchParams()
  if (f.type !== '全部') p.set('type', f.type)
  if (f.actor.trim()) p.set('actor', f.actor.trim())
  if (f.from) p.set('from', f.from)
  if (f.to) p.set('to', f.to)
  if (f.offHours) p.set('offHours', 'true')
  for (const [k, v] of Object.entries(extra)) p.set(k, v)
  const s = p.toString()
  return s ? `?${s}` : ''
}

export function useAuditLog(data: Ref<A14Data>) {
  const live = ref(false)
  const liveLogs = ref<A14Log[]>([])
  const today = ref<number | null>(null)
  const nextCursor = ref<number | null>(null)
  const loading = ref(false)
  const chain = ref<ChainState>({ state: 'checking' })
  const filters = reactive<AuditFilters>({ type: '全部', actor: '', from: '', to: '', offHours: false })
  /** why the live list could not be loaded for the current filters (null = fine) */
  const error = ref<string | null>(null)
  /** 起始晚于截止:不发请求,直接提示 */
  const rangeError = computed(() => (filters.from && filters.to && filters.from > filters.to ? '起始日期不能晚于截止日期' : null))
  /** any filter besides the event type narrows the list */
  const narrowed = computed(() => !!(filters.actor.trim() || filters.from || filters.to || filters.offHours))

  let seq = 0
  async function load(more = false) {
    const mine = ++seq
    if (live.value && rangeError.value) {
      error.value = rangeError.value
      liveLogs.value = []
      nextCursor.value = null
      loading.value = false
      return
    }
    loading.value = true
    try {
      const extra: Record<string, string> = { limit: String(PAGE_SIZE) }
      if (more && nextCursor.value != null) extra.cursor = String(nextCursor.value)
      const page = await getJson<ApiPage>(`/audit${query(filters, extra)}`)
      if (mine !== seq || !page || !Array.isArray(page.items)) return
      const rows = page.items.map(toLog)
      liveLogs.value = more ? [...liveLogs.value, ...rows] : rows
      nextCursor.value = page.nextCursor ?? null
      today.value = page.today
      live.value = true
      error.value = null
    } catch (e) {
      // live list + a refused query (e.g. a bad filter): say so and show nothing stale;
      // API not reachable (or not permitted) before the first answer — keep the seed
      if (mine === seq && live.value) {
        error.value = e instanceof ApiError && e.api ? `查询失败:${e.message}` : '审计服务暂不可用,请稍后重试'
        liveLogs.value = []
        nextCursor.value = null
      }
    } finally {
      if (mine === seq) loading.value = false
    }
  }

  async function verify() {
    chain.value = { state: 'checking' }
    try {
      const r = await getJson<ApiChain>('/audit/verify')
      chain.value = r.valid
        ? { state: 'ok', checked: r.checked }
        : { state: 'broken', checked: r.checked, brokenAt: r.brokenAt ?? 0 }
    } catch {
      chain.value = { state: 'offline' }
    }
  }

  /** downloads the CSV for the current filters; resolves to the export's watermark id */
  async function exportCsv(): Promise<{ rows: number; watermark: string } | null> {
    const res = await fetch(`${BASE}/audit/export.csv${query(filters)}`, { headers: authHeaders({ Accept: 'text/csv' }) })
    if (!res.ok) throw new Error(`${res.status}`)
    const blob = await res.blob()
    const cd = res.headers.get('Content-Disposition') ?? ''
    const m = /filename\*=UTF-8''([^;]+)/.exec(cd)
    const name = m ? decodeURIComponent(m[1]!) : 'audit-export.csv'
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = name
    document.body.appendChild(a)
    a.click()
    setTimeout(() => { a.remove(); URL.revokeObjectURL(url) }, 1000)
    const text = await blob.text()
    const rows = Math.max(0, text.split('\r\n').filter(Boolean).length - 1)
    // the export itself is now the newest event (and one more link in the chain)
    void load()
    void verify()
    return { rows, watermark: res.headers.get('X-YB-Watermark') ?? '' }
  }

  // re-query the server when filters change (actor is typed: debounce it)
  let timer: ReturnType<typeof setTimeout> | undefined
  watch(() => [filters.type, filters.from, filters.to, filters.offHours], () => { if (live.value) void load() })
  watch(() => filters.actor, () => {
    if (!live.value) return
    clearTimeout(timer)
    timer = setTimeout(() => void load(), 300)
  })

  /** rows shown: live rows (already filtered server-side) or the seed filtered locally */
  const logs = computed<A14Log[]>(() => {
    if (live.value) return liveLogs.value
    const who = filters.actor.trim()
    return data.value.logs.filter(l =>
      (filters.type === '全部' || l.type === filters.type)
      && (!who || l.who.includes(who))
      && (!filters.offHours || isOffHoursTime(l.time)))
  })

  void load()
  void verify()

  return { live, logs, today, nextCursor, loading, chain, filters, error, rangeError, narrowed, load, verify, exportCsv }
}
