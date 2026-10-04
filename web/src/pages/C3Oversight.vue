<script setup lang="ts">
import { PageHeader, PageSection, StatCard } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { C3_SEED, type C3CalendarEvent, type C3Icon } from '@/mock/C3'

const data = usePageData('C3', C3_SEED)

const ICON: Record<C3Icon, string> = {
  coin: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9 10h6M9 14h6M12 10v7',
  shield: 'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3zM8.5 12l2.5 2.5 4.5-5',
  doc: 'M6 3h9l5 5v13H6zM14 3v6h6M9 13h7M9 17h5',
  check: 'M20 6L9 17l-5-5',
}

const cellClass = (e: C3CalendarEvent) =>
  !e.done ? 'bg-line-4 text-ink-4' : e.kind === 'monthly' ? 'bg-brand text-white' : 'bg-violet text-white'

function ask() {
  sendAction('C3', 'submitSuggestion', {})
  say('建议已提交 · 将在 15 个工作日内答复')
}
</script>

<template>
  <PageSection label="C3 外部监督">
    <PageHeader title="外部监督视图" subtitle="人大代表 · 政协委员 · 社会监督员 · 仅汇总口径,不含任何机构与个人数据">
      <Badge variant="ok" class="px-3 py-1 text-xs">公开层</Badge>
    </PageHeader>

    <div class="grid grid-cols-4 gap-3">
      <StatCard
        v-for="k in data.kpis"
        :key="k.label"
        :label="k.label"
        :value="k.value"
        :sub="k.sub"
        :tone="k.tone"
        size="lg"
        :icon="ICON[k.icon]"
      />
    </div>

    <div class="grid grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)] gap-4">
      <div class="yb-card flex flex-col px-card-x py-card-y">
        <div class="mb-3.5 text-[15px] font-semibold">数据公开日历 · {{ data.year }}</div>
        <div class="grid grid-cols-12 gap-1.5">
          <div v-for="m in data.calendar" :key="m.month" class="flex flex-col items-center gap-1">
            <span class="yb-num text-[11px] text-ink-5">{{ m.month }}</span>
            <span
              v-for="(e, i) in m.events"
              :key="i"
              :class="['flex h-[30px] w-full items-center justify-center rounded-[5px] text-[10px]', cellClass(e)]"
            >{{ e.label }}</span>
          </div>
        </div>
        <div class="flex-1" />
        <div class="mt-4 flex gap-3.5 text-[11px] text-ink-4">
          <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-[2px] bg-brand" />月告知</span>
          <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-[2px] bg-violet" />季度/专题</span>
          <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-[2px] bg-line-4" />计划中</span>
        </div>
      </div>

      <div class="yb-card px-card-x py-card-y">
        <div class="mb-3 text-[15px] font-semibold">反馈闭环</div>
        <div v-for="r in data.feedback" :key="r.label" class="border-t border-line-3 py-2.5">
          <div class="flex justify-between text-[13px]">
            <span>{{ r.label }}</span>
            <span class="yb-num font-semibold">{{ r.value }}</span>
          </div>
          <div class="mt-1.5 h-1.5 rounded-[3px] bg-line-2">
            <div class="h-1.5 rounded-[3px] bg-ok" :style="{ width: r.pct + '%' }" />
          </div>
        </div>
        <Button variant="outline" class="mt-3.5 w-full font-normal" @click="ask">提交监督建议</Button>
      </div>
    </div>
  </PageSection>
</template>
