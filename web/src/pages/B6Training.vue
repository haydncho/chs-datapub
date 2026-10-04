<script setup lang="ts">
import { computed, reactive } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { B6_SEED, type B6Course, type B6DocKind, type B6Icon, type B6Tone } from '@/mock/B6'

const data = usePageData('B6', B6_SEED)

const ICON: Record<B6Icon, string> = {
  book: 'M4 4h7a3 3 0 0 1 3 3v13a2 2 0 0 0-2-2H4zM20 4h-7a3 3 0 0 0-3 3v13a2 2 0 0 1 2-2h8z',
  chart: 'M4 18h16M7 14v4M12 9v9M17 5v13',
  shield: 'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3zM8.5 12l2.5 2.5 4.5-5',
  chat: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
}
const TONE: Record<B6Tone, { bg: string; fg: string }> = {
  brand: { bg: 'var(--brand-soft)', fg: 'var(--brand)' },
  warn: { bg: 'var(--warn-soft)', fg: 'var(--warn-ink)' },
  ok: { bg: 'var(--ok-soft)', fg: 'var(--ok-ink)' },
}
const DOC_CLASS: Record<B6DocKind, string> = {
  国家: 'bg-bad-soft text-bad-ink',
  本市: 'bg-brand-soft text-brand',
  口径: 'bg-surface-3 text-ink-3',
}

/** courses completed in this session */
const finished = reactive<Record<string, boolean>>({})
const isDone = (c: B6Course) => c.done || !!finished[c.id]
const doneCount = computed(() => data.value.courses.filter(isDone).length)
const total = computed(() => data.value.courses.length)

function start(c: B6Course) {
  finished[c.id] = true
  sendAction('B6', 'completeCourse', { id: c.id })
  say('已完成“' + c.name + '”')
}
</script>

<template>
  <PageSection label="B6 政策培训">
    <PageHeader title="政策与培训" :subtitle="data.subtitle">
      <div class="w-[220px]">
        <div class="flex justify-between text-xs">
          <span class="text-ink-4">我的必修进度</span>
          <span class="yb-num font-semibold">{{ doneCount }}/{{ total }}</span>
        </div>
        <div class="mt-1.5 h-1.5 rounded-[3px] bg-line-1">
          <div class="h-1.5 rounded-[3px] bg-ok transition-[width]" :style="{ width: (doneCount / total) * 100 + '%' }" />
        </div>
      </div>
    </PageHeader>

    <div class="grid grid-cols-4 gap-3.5">
      <div v-for="c in data.courses" :key="c.id" class="yb-card flex flex-col overflow-hidden">
        <div
          class="relative flex h-[110px] flex-col justify-between overflow-hidden p-3.5"
          :style="{ background: `linear-gradient(135deg, ${TONE[c.tone].bg} 0%, #FFFFFF 120%)` }"
        >
          <svg viewBox="0 0 24 24" width="88" height="88" fill="none" :stroke="TONE[c.tone].fg" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round" class="absolute -right-2.5 -bottom-3.5 opacity-[.14]"><path :d="ICON[c.icon]" /></svg>
          <span class="relative flex items-center gap-1.5 text-[11px] font-semibold" :style="{ color: TONE[c.tone].fg }">
            <span class="flex size-[22px] items-center justify-center rounded-md bg-white">
              <svg viewBox="0 0 24 24" width="13" height="13" fill="none" :stroke="TONE[c.tone].fg" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path :d="ICON[c.icon]" /></svg>
            </span>{{ c.tag }}
          </span>
          <span class="yb-num relative text-[30px] font-semibold" :style="{ color: TONE[c.tone].fg }">{{ c.minutes }}<span class="text-[13px]"> 分钟</span></span>
        </div>
        <div class="flex flex-1 flex-col gap-2 p-3.5">
          <div class="font-semibold">{{ c.name }}</div>
          <div class="flex-1 text-xs text-ink-4">{{ c.desc }}</div>
          <span v-if="isDone(c)" class="text-xs font-medium text-ok-ink">✓ 已完成 · 测验 {{ c.score }} 分</span>
          <Button v-else variant="soft" class="h-8 font-medium" @click="start(c)">开始学习</Button>
        </div>
      </div>
    </div>

    <div class="yb-card overflow-hidden">
      <div class="border-b border-line-2 px-card-x py-3.5 text-[15px] font-semibold">政策文件</div>
      <div
        v-for="d in data.docs"
        :key="d.name"
        class="grid grid-cols-[90px_minmax(0,1fr)_140px_90px] items-center gap-3.5 border-b border-line-3 px-5 py-3"
      >
        <span :class="['justify-self-start rounded px-2 py-px text-[11px] font-semibold', DOC_CLASS[d.kind]]">{{ d.kind }}</span>
        <span class="font-medium">{{ d.name }}</span>
        <span class="text-xs text-ink-4">{{ d.number }}</span>
        <span class="yb-num text-right text-xs text-ink-5">{{ d.date }}</span>
      </div>
    </div>
  </PageSection>
</template>
