<script setup lang="ts">
import { computed, ref } from 'vue'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { Button } from '@/components/ui/button'
import { pad } from '@/lib/format'
import { cn } from '@/lib/utils'
import { A5_SEED, type A5Chart } from '@/mock/A5'
import ChartThumb from './A5/ChartThumb.vue'
import { vPress } from '@/lib/a11y'

const data = usePageData('A5', A5_SEED)

const tplIdx = ref(0)
const secIdx = ref(0)

const tpl = computed(() => data.value.templates[tplIdx.value] ?? data.value.templates[0]!)
const secName = computed(() => tpl.value.sections[secIdx.value]?.name ?? '')

function pickTpl(i: number) {
  tplIdx.value = i
  secIdx.value = 0
}

function addChart(c: A5Chart) {
  const sec = tpl.value.sections[secIdx.value]
  if (!sec) return
  if (!sec.charts.includes(c.name)) sec.charts.push(c.name)
  sendAction('A5', 'addChart', { template: tplIdx.value, section: secIdx.value, chart: c.name })
  say('已将“' + c.name + '”加入 ' + sec.name)
}

function save() {
  const v = tpl.value.version + 1
  sendAction('A5', 'saveTemplateVersion', { template: tplIdx.value, version: v })
  say('已保存为 v' + v + ' · 下期起生效')
}
</script>

<template>
  <section data-screen-label="A5 图表与报告模板" class="grid min-h-[calc(100vh-132px)] grid-cols-1 xl:grid-cols-[260px_minmax(0,1fr)_340px]">
    <aside class="flex flex-col gap-1.5 border-r border-line-1 bg-surface-1 px-3.5 py-5 max-xl:grid max-xl:grid-cols-2 max-xl:gap-2 max-xl:border-r-0 max-xl:border-b max-xl:px-5 md:max-xl:grid-cols-3">
      <div class="px-2 pb-2 text-lg font-semibold max-xl:col-span-full max-xl:px-0 max-xl:pb-0">报告模板</div>
      <div v-press
        v-for="(t, i) in data.templates"
        :key="t.name"
        :class="cn(
          'cursor-pointer rounded-[10px] border p-3 hover:bg-white max-xl:min-h-11',
          i === tplIdx ? 'border-brand-line bg-white' : 'border-transparent bg-transparent',
        )"
        @click="pickTpl(i)"
      >
        <div class="font-semibold">{{ t.name }}</div>
        <div class="text-[11px] text-ink-4">{{ t.desc }}</div>
      </div>
    </aside>

    <main class="flex min-w-0 flex-col gap-3.5 px-7 pt-6 pb-12 max-xl:px-5 max-xl:pb-8">
      <div class="flex items-end justify-between gap-5">
        <div class="min-w-0 flex-1">
          <div class="text-[22px] font-semibold">{{ tpl.name }}</div>
          <div class="text-xs text-ink-4">{{ tpl.desc }} · 被引用 {{ tpl.refs }} 次 · v{{ tpl.version }}</div>
        </div>
        <Button class="h-9 max-xl:h-10" @click="save">保存为新版本</Button>
      </div>
      <div v-press
        v-for="(s, i) in tpl.sections"
        :key="tpl.name + s.name"
        :class="cn(
          'grid cursor-pointer grid-cols-[28px_minmax(0,1fr)_300px] items-center max-xl:grid-cols-[28px_minmax(0,1fr)] max-xl:min-h-11 gap-3.5 rounded-xl border-[1.5px] bg-white px-4 py-3.5',
          i === secIdx ? 'border-brand' : 'border-line-1',
        )"
        @click="secIdx = i"
      >
        <span class="yb-num font-semibold text-ink-5">{{ pad(i + 1) }}</span>
        <div class="min-w-0">
          <div class="font-semibold">{{ s.name }}</div>
          <div class="text-xs text-ink-4">{{ s.indicators === '—' ? '自由内容' : '绑定指标:' + s.indicators }}</div>
        </div>
        <div class="flex justify-end gap-1.5 max-xl:col-start-2 max-xl:flex-wrap max-xl:justify-start">
          <span v-for="c in s.charts" :key="c" class="rounded-lg bg-surface-3 px-2.5 py-1 text-xs text-ink-2">{{ c }}</span>
        </div>
      </div>
    </main>

    <aside class="flex flex-col gap-3 border-l border-line-1 bg-white px-5 py-[22px] max-xl:border-t max-xl:border-l-0">
      <div class="text-xs font-semibold text-ink-4">图表组件库 · 点击加入“{{ secName }}”</div>
      <div class="grid grid-cols-2 gap-2 max-xl:grid-cols-3 lg:max-xl:grid-cols-4">
        <div v-press
          v-for="c in data.library"
          :key="c.name"
          class="flex cursor-pointer flex-col gap-2 rounded-[10px] border border-line-1 p-2.5 hover:border-brand-line"
          @click="addChart(c)"
        >
          <ChartThumb :thumb="c.thumb" />
          <div class="text-xs font-medium">{{ c.name }}</div>
          <div class="-mt-1.5 text-[11px] text-ink-5">{{ c.desc }}</div>
        </div>
      </div>
    </aside>
  </section>
</template>
