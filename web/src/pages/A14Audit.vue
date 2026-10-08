<script setup lang="ts">
import { computed, ref } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import { usePageData } from '@/api/client'
import { say } from '@/app/shell'
import { fmt } from '@/lib/format'
import { cn } from '@/lib/utils'
import { A14_SEED, type A14EventType } from '@/mock/A14'
import AuditDetail from './A14/AuditDetail.vue'
import { LIVE_FILTERS, useAuditLog } from './A14/useAuditLog'

const data = usePageData('A14', A14_SEED)
const { live, logs, today, nextCursor, loading, chain, filters, error, rangeError, narrowed, load, verify, exportCsv } = useAuditLog(data)

const selId = ref<string | null>(null)

const KIND_CLASS: Record<A14EventType, string> = {
  查阅: 'bg-surface-3 text-ink-3',
  审批: 'bg-bad-soft text-bad-ink',
  配置: 'bg-brand-soft text-brand',
  导出: 'bg-warn-soft text-warn-ink',
  权限: 'bg-violet-soft text-violet',
  发布: 'bg-ok-soft text-ok-ink',
  登录: 'bg-surface-3 text-ink-3',
  删除: 'bg-bad-soft text-bad-ink',
  其他: 'bg-surface-3 text-ink-4',
}

const chips = computed(() => (live.value ? LIVE_FILTERS : data.value.filters))
const summary = computed(() =>
  live.value && today.value != null ? `全量留存 6 年 · 防篡改链式签名 · 今日 ${fmt(today.value)} 条` : data.value.summary)

// seed mode keeps the prototype behaviour (selection survives filtering); live mode follows the visible rows
const sel = computed(() => {
  if (live.value) return logs.value.find(l => l.id === selId.value) ?? logs.value[0] ?? null
  const id = selId.value ?? data.value.selectedId
  return data.value.logs.find(l => l.id === id) ?? data.value.logs[0] ?? null
})

const chainView = computed(() => {
  const c = chain.value
  switch (c.state) {
    case 'checking': return { text: '链式校验中…', cls: 'border-line-1 bg-white text-ink-4' }
    case 'ok': return { text: `✓ 链式校验通过 · ${fmt(c.checked)} 条`, cls: 'border-transparent bg-ok-soft text-ok-ink' }
    case 'broken': return { text: `✗ 链在 #${c.brokenAt} 处断开`, cls: 'border-bad-line bg-bad-soft text-bad-ink' }
    default: return { text: '链式校验 · 未连接审计服务', cls: 'border-line-1 bg-surface-1 text-ink-4' }
  }
})

const exporting = ref(false)
async function onExport() {
  if (rangeError.value) {
    say(rangeError.value)
    return
  }
  if (!live.value) {
    say('演示数据 · 连接审计服务后可导出 CSV')
    return
  }
  exporting.value = true
  try {
    const r = await exportCsv()
    if (r) say(`已导出 ${fmt(r.rows)} 条 · 水印编号 ${r.watermark} · 本次导出已记入审计日志`)
  } catch {
    say('导出失败,请稍后重试')
  } finally {
    exporting.value = false
  }
}

const GRID = 'grid grid-cols-[120px_76px_150px_minmax(0,1fr)_110px] items-center gap-3 px-[18px] py-2.5'
const DATE = 'h-8 max-xl:h-10 rounded-md border border-line-1 bg-white px-2 font-mono text-xs text-ink-2 outline-none focus:border-brand-line'
</script>

<template>
  <PageSection label="A14 审计日志">
    <PageHeader class="max-xl:flex-wrap" title="审计日志" :subtitle="summary">
      <div class="flex max-w-full min-w-0 flex-wrap gap-1.5 max-xl:gap-2" role="group" aria-label="按事件类型筛选">
        <button
          v-for="c in chips"
          :key="c"
          type="button"
          :class="cn(
            'cursor-pointer rounded-full border px-3 py-1.5 text-xs whitespace-nowrap max-xl:min-h-10 max-xl:px-4 max-sm:px-3',
            c === filters.type ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
          )"
          :aria-pressed="c === filters.type"
          @click="filters.type = c"
        >{{ c }}</button>
      </div>
    </PageHeader>

    <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
      <div class="yb-card overflow-clip">
        <div class="flex flex-wrap items-center gap-2.5 border-b border-line-3 px-[18px] py-2.5 text-xs">
          <Input v-model="filters.actor" placeholder="操作人" aria-label="按操作人筛选" class="h-8 w-[132px] text-xs max-xl:h-10 md:text-xs" />
          <template v-if="live">
            <input v-model="filters.from" type="date" aria-label="起始日期" :class="DATE">
            <span class="text-ink-5">—</span>
            <input v-model="filters.to" type="date" aria-label="截止日期" :class="DATE" :aria-invalid="!!rangeError" :aria-describedby="rangeError ? 'a14-range-error' : undefined">
            <span v-if="rangeError" id="a14-range-error" role="alert" class="text-bad-ink" data-testid="a14-range-error">{{ rangeError }}</span>
          </template>
          <label class="flex cursor-pointer items-center gap-1.5 text-ink-3 max-xl:min-h-10">
            <Switch v-model="filters.offHours" aria-label="仅非工作时间" />
            仅非工作时间 <span class="text-ink-5">22:00–06:00</span>
          </label>
          <div class="flex-1 max-xl:hidden" />
          <button
            type="button"
            :class="['cursor-pointer rounded-full border px-2.5 py-1 max-xl:min-h-10 font-mono text-[11px] whitespace-nowrap', chainView.cls]"
            title="重新校验整条哈希链"
            @click="verify()"
          >{{ chainView.text }}</button>
          <Button variant="outline" size="sm" class="h-8 text-xs max-xl:h-10" :disabled="exporting" @click="onExport">
            {{ exporting ? '导出中…' : '导出 CSV' }}
          </Button>
        </div>
        <div class="max-xl:max-h-[480px] max-xl:overflow-auto">
        <div class="max-xl:min-w-[640px]">
        <div :class="[GRID, 'max-xl:top-0 sticky top-(--sticky-top) z-[6] bg-surface-1 text-xs text-ink-4']">
          <span>时间</span><span>类型</span><span>操作人</span><span>对象</span><span>IP</span>
        </div>
        <div v-if="logs.length === 0" class="flex flex-col items-center gap-1.5 px-5 py-11 text-ink-4" data-testid="a14-empty">
          <template v-if="error">
            <div class="font-semibold text-bad-ink">{{ error }}</div>
            <div class="text-xs">请调整筛选条件后重试</div>
          </template>
          <template v-else>
            <div class="font-semibold text-ink-2">{{ loading ? '加载中…' : narrowed ? '没有符合筛选条件的事件' : '暂无该类事件' }}</div>
            <div class="text-xs">{{ narrowed ? '可调整操作人、日期范围或事件类型' : '可切换事件类型' }}</div>
          </template>
        </div>
        <div
          v-for="l in logs"
          :key="l.id"
          role="button"
          tabindex="0"
          :aria-pressed="l.id === sel?.id"
          :aria-label="`${l.time} ${l.type} ${l.who} ${l.object}`"
          :class="cn(
            GRID,
            'cursor-pointer yb-tr border-b border-line-3 hover:bg-surface-1 max-xl:min-h-11 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-brand',
            l.id === sel?.id ? 'bg-brand-tint shadow-[inset_3px_0_0_var(--brand)]'
            : !l.signatureOk ? 'bg-bad-soft/40 shadow-[inset_3px_0_0_var(--bad)]'
            : l.risk ? 'bg-[#FFFBF4] shadow-[inset_3px_0_0_var(--warn)]' : 'bg-white',
          )"
          @click="selId = l.id"
          @keydown.enter.prevent="selId = l.id"
          @keydown.space.prevent="selId = l.id"
        >
          <span class="yb-num text-xs text-ink-3">{{ l.time }}</span>
          <span :class="['justify-self-start rounded px-2 py-px text-[11px] font-semibold', KIND_CLASS[l.type]]">{{ l.type }}</span>
          <div class="min-w-0">
            <div class="truncate text-xs font-medium">{{ l.who }}</div>
            <div class="truncate text-[11px] text-ink-5">{{ l.role }}</div>
          </div>
          <span class="truncate text-xs">{{ l.object }}</span>
          <span class="truncate font-mono text-[11px] text-ink-4">{{ l.ip }}</span>
        </div>
        </div>
        </div>
        <div v-if="live && nextCursor != null" class="flex justify-center px-[18px] py-2.5">
          <Button variant="ghost" size="sm" class="h-8 text-xs text-brand" :disabled="loading" @click="load(true)">
            {{ loading ? '加载中…' : '加载更早的记录' }}
          </Button>
        </div>
      </div>

      <AuditDetail v-if="sel" :log="sel" :date="data.date" :kind-class="KIND_CLASS" />
    </div>
  </PageSection>
</template>
