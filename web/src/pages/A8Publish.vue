<script setup lang="ts">
import { computed } from 'vue'
import { usePageData } from '@/api/client'
import { cn } from '@/lib/utils'
import { A8_SEED, type A8Tab } from '@/mock/A8'
import { createA8Store, provideA8 } from './A8/store'
import TaskQueue from './A8/TaskQueue.vue'
import FlowHeader from './A8/FlowHeader.vue'
import TabPackage from './A8/TabPackage.vue'
import TabScope from './A8/TabScope.vue'
import TabPreview from './A8/TabPreview.vue'
import TabSign from './A8/TabSign.vue'
import TabFix from './A8/TabFix.vue'
import ApprovalPanel from './A8/ApprovalPanel.vue'
import ConfirmDialog from './A8/ConfirmDialog.vue'
import { vPress } from '@/lib/a11y'

const data = usePageData('A8', A8_SEED)
const s = createA8Store(data)
provideA8(s)

const tabs = computed(() =>
  ([
    ['pkg', '发布包', ''],
    ['scope', '定向范围', String(s.covN)],
    ['ver', '分受众预览', ''],
    ['sig', '签收追踪', s.posted ? Math.round((s.sigN / Math.max(1, s.covN)) * 100) + '%' : ''],
    ['fix', '更正与撤回', ''],
  ] as [A8Tab, string, string][]).map(([id, l, b]) => ({ id, l, b })),
)
</script>

<template>
  <section data-screen-label="04 发布工作流" class="grid min-h-[calc(var(--app-vh)-132px)] grid-cols-1 lg:max-xl:grid-cols-[minmax(0,1fr)_300px] xl:max-[1439px]:grid-cols-[248px_minmax(0,1fr)_304px] min-[1440px]:grid-cols-[280px_minmax(0,1fr)_340px] max-xl:[&_.text-\[10px\]]:text-[11px] max-xl:[&_.text-\[11px\]]:text-xs">
    <TaskQueue class="lg:max-xl:col-span-2" />
    <main class="flex min-w-0 flex-col gap-4 px-7 pt-[22px] pb-12 max-[1439px]:px-6 max-xl:px-4 max-xl:pt-5 max-xl:pb-6 lg:max-xl:px-5">
      <FlowHeader />
      <div class="yb-card">
        <div class="flex gap-[22px] overflow-x-auto border-b border-line-2 px-5 max-xl:gap-5 max-xl:px-4" role="tablist">
          <div v-press
            v-for="t in tabs"
            :key="t.id"
            role="tab"
            :aria-selected="t.id === s.tab"
            :class="cn(
              'flex shrink-0 cursor-pointer items-center gap-1.5 border-b-2 py-[13px] whitespace-nowrap max-xl:min-h-12',
              t.id === s.tab ? 'border-ink-1 font-semibold text-ink-1' : 'border-transparent text-ink-4',
            )"
            @click="s.tab = t.id"
          >
            {{ t.l }}
            <span
              v-if="t.b"
              :class="cn(
                'rounded-full px-1.5 text-[10px] font-semibold',
                t.id === 'sig' ? 'bg-ok-soft text-ok-ink' : 'bg-brand-soft text-brand',
              )"
            >{{ t.b }}</span>
          </div>
        </div>
        <div class="p-5 max-xl:p-4">
          <TabPackage v-if="s.tab === 'pkg'" />
          <TabScope v-else-if="s.tab === 'scope'" />
          <TabPreview v-else-if="s.tab === 'ver'" />
          <TabSign v-else-if="s.tab === 'sig'" />
          <TabFix v-else />
        </div>
      </div>
    </main>
    <ApprovalPanel />
    <ConfirmDialog />
  </section>
</template>
