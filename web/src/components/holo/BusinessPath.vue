<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { collectionApi, opinionApi } from '@/api'
import { recommendApi } from '@/api/indicator'
import { publishApi } from '@/api/publish'

/**
 * 业务路径看板(驾驶舱顶部):把「数据归集 → 指标与算法 → 发布管理 → 发布后响应」四个阶段的当前进展与待办汇成一行,
 * 每段可点击进入对应页面;数字全部取自各页面同源接口,失败的阶段显示「—」而不影响其它阶段。
 */
interface Stage { no: string; name: string; to: string; main: string; sub: string; tone: 'ok' | 'todo' | 'alert' | 'idle' }
const stages = ref<Stage[]>([
  { no: '01', name: '数据底座', to: '/a3', main: '—', sub: '数据归集', tone: 'idle' },
  { no: '02', name: '指标与算法', to: '/a6', main: '—', sub: '选题候选', tone: 'idle' },
  { no: '03', name: '发布管理', to: '/a8', main: '—', sub: '在办事项', tone: 'idle' },
  { no: '04', name: '发布后响应', to: '/a10', main: '—', sub: '待办理意见', tone: 'idle' },
])
const loaded = ref(false)

onMounted(async () => {
  const [col, top, todo, op] = await Promise.allSettled([collectionApi.overview(), recommendApi.topics(), publishApi.todos(), opinionApi.list('全部')])
  const s = stages.value
  if (col.status === 'fulfilled') {
    const ok = col.value.sources.filter((x) => x.status === 'OK').length
    s[0] = { ...s[0], main: `${ok}/${col.value.sources.length}`, sub: col.value.qcDone ? '数据源到齐 · 已校验' : col.value.allArrived ? '数据源到齐 · 待校验' : '数据源到数中', tone: col.value.qcDone ? 'ok' : 'todo' }
  }
  if (top.status === 'fulfilled') {
    const cand = top.value.rows.filter((r) => r.status === 'CAND').length
    s[1] = { ...s[1], main: String(cand), sub: '个选题待人工确认', tone: cand ? 'todo' : 'ok' }
  }
  if (todo.status === 'fulfilled') {
    const n = todo.value.flatMap((g) => g.items)
    const urgent = n.filter((i) => i.dueTone === 'danger' || i.dueTone === 'warning').length
    s[2] = { ...s[2], main: String(n.length), sub: urgent ? `项在办 · ${urgent} 项时限临近` : '项在办 · 时限正常', tone: urgent ? 'alert' : 'todo' }
  }
  if (op.status === 'fulfilled') {
    const open = op.value.rows.filter((r) => r.status !== 'DONE')
    const over = open.filter((r) => r.dueTone === 'danger').length
    s[3] = { ...s[3], main: String(open.length), sub: over ? `条待办理 · ${over} 条超期` : '条待办理 · 无超期', tone: over ? 'alert' : open.length ? 'todo' : 'ok' }
  }
  loaded.value = true
})
const dot = computed(() => ({ ok: 'bg-success-solid', todo: 'bg-[#E5A23C]', alert: 'bg-[#D2402A]', idle: 'bg-neutral' }))
</script>

<template>
  <nav class="path ck-card grid grid-cols-4" aria-label="业务路径" data-testid="biz-path">
    <RouterLink v-for="(s, i) in stages" :key="s.no" :to="s.to" class="path-step group relative flex items-center gap-3.5 px-5 py-3 no-underline hover:no-underline" :data-stage="s.no">
      <span class="font-display text-[26px] leading-none font-bold text-[#E0816F]/80 tabular-nums">{{ s.no }}</span>
      <span class="min-w-0 flex-1">
        <span class="flex items-center gap-2 text-[12px] tracking-[.14em] text-ink-muted"><i class="size-1.5 rotate-45" :class="dot[s.tone]" />{{ s.name }}</span>
        <span class="mt-0.5 flex items-baseline gap-1.5"><b class="font-display text-[22px] leading-none text-ink tabular-nums">{{ s.main }}</b><span class="truncate text-[12px] text-ink-sub">{{ s.sub }}</span></span>
      </span>
      <svg v-if="i < stages.length - 1" class="absolute top-1/2 -right-[7px] z-[1] -translate-y-1/2 text-ink-faint" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M9 5l7 7-7 7" /></svg>
    </RouterLink>
  </nav>
</template>

<style scoped>
.path-step + .path-step {
  border-left: 1px solid rgba(243, 239, 228, 0.14);
}
.path-step:hover {
  background: rgba(243, 239, 228, 0.05);
}
</style>
