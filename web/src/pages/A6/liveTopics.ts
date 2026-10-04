import { ref, type Ref } from 'vue'
import { getJson } from '@/api/client'
import type { A6Status, A6Topic } from '@/mock/A6'

const STATUSES: A6Status[] = ['adopted', 'open', 'skip']

function isTopic(t: unknown): t is A6Topic {
  const x = t as Partial<A6Topic> | null
  return !!x && typeof x.id === 'string' && typeof x.title === 'string' && typeof x.score === 'number'
    && Array.isArray(x.dims) && x.dims.length === 4 && Array.isArray(x.facts) && Array.isArray(x.audiences)
}

/**
 * Merge live 病种专题 scores into the page's topic pool.
 *
 * - live topics replace the page's 病种专题 (other kinds — 机构 / 质量 / 区域 — are kept);
 * - a page 病种专题 that was already 采纳 stays in the pool even if it dropped out of the live top N;
 * - a live topic keeps the page's 采纳 / 本期不做 status when the page has it under the same id;
 * - ids that clash with a kept topic of another kind get a `-drg` suffix; result sorted by score.
 */
export function mergeTopics(page: A6Topic[], live: A6Topic[]): A6Topic[] {
  const prev = new Map(page.map(t => [t.id, t]))
  const liveIds = new Set(live.map(t => t.id))
  const kept = page.filter(t => t.kind !== '病种专题' || (t.status === 'adopted' && !liveIds.has(t.id)))
  const keptIds = new Set(kept.map(t => t.id))
  const merged = live.map(t => {
    const p = prev.get(t.id)
    const status = p && p.kind === t.kind ? p.status : STATUSES.includes(t.status) ? t.status : 'open'
    return { ...t, id: keptIds.has(t.id) ? `${t.id}-drg` : t.id, status }
  })
  return [...kept, ...merged].sort((a, b) => b.score - a.score)
}

/** Live 病种专题 candidates from the analytics service; stays `null` when it is unreachable. */
export function useLiveTopics(top = 4): Ref<A6Topic[] | null> {
  const live = ref<A6Topic[] | null>(null)
  getJson<unknown>(`/analytics/topics/recommend?top=${top}`)
    .then(r => {
      if (Array.isArray(r) && r.length > 0 && r.every(isTopic)) live.value = r
    })
    .catch(() => { /* analytics not running — keep the seed */ })
  return live
}
