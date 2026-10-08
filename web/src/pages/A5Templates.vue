<script setup lang="ts">
import { computed, ref } from 'vue'
import { getJson, runAction, usePageData } from '@/api/client'
import { say } from '@/app/shell'
import { Button } from '@/components/ui/button'
import { pad } from '@/lib/format'
import { cn } from '@/lib/utils'
import { A5_SEED, type A5Chart, type A5Data } from '@/mock/A5'
import ChartThumb from './A5/ChartThumb.vue'
import { vPress } from '@/lib/a11y'

const data = usePageData('A5', A5_SEED)

/** re-read the read model: the template's charts / version / dirty flag live on the server */
async function refresh() {
  try {
    const remote = await getJson<A5Data>('/pages/A5')
    if (remote && typeof remote === 'object') data.value = { ...A5_SEED, ...remote }
  } catch { /* keep the current view */ }
}

const tplIdx = ref(0)
const secIdx = ref(0)
const busy = ref(false)

const tpl = computed(() => data.value.templates[tplIdx.value] ?? data.value.templates[0]!)
const secName = computed(() => tpl.value.sections[secIdx.value]?.name ?? '')

function pickTpl(i: number) {
  tplIdx.value = i
  secIdx.value = 0
}

async function addChart(c: A5Chart) {
  const sec = tpl.value.sections[secIdx.value]
  if (!sec || busy.value) return
  if (sec.charts.includes(c.name)) {
    say('“' + c.name + '”已在 ' + sec.name + ' 中')
    return
  }
  busy.value = true
  const r = await runAction('A5', 'addChart', { template: tplIdx.value, section: secIdx.value, chart: c.name })
  busy.value = false
  if (!r.ok) return say(r.error)
  await refresh()
  say('已将“' + c.name + '”加入 ' + sec.name)
}

async function removeChart(si: number, chart: string) {
  const sec = tpl.value.sections[si]
  if (!sec || busy.value) return
  busy.value = true
  const r = await runAction('A5', 'removeChart', { template: tplIdx.value, section: si, chart })
  busy.value = false
  if (!r.ok) return say(r.error)
  await refresh()
  say('已从 ' + sec.name + ' 移除“' + chart + '”')
}

async function save() {
  if (busy.value) return
  busy.value = true
  const r = await runAction<{ result?: { version: number } }>('A5', 'saveTemplateVersion', { template: tplIdx.value })
  busy.value = false
  if (!r.ok) return say(r.error)
  await refresh()
  say('已保存为 v' + (r.data?.result?.version ?? tpl.value.version) + ' · 下期起生效')
}
</script>

<template>
  <!--
    ≥1280:模板列表 | 章节 | 组件库 三栏。
    <1280(Pad):模板列表改为顶部一行卡片;章节与组件库左右并排,组件库吸顶,选中章节后无需滚到页底即可加图。
  -->
  <section data-screen-label="A5 图表与报告模板" class="grid min-h-[calc(var(--app-vh)-132px)] grid-cols-[minmax(0,1fr)_272px] grid-rows-[auto_1fr] max-md:grid-cols-1 lg:grid-cols-[minmax(0,1fr)_312px] xl:grid-cols-[260px_minmax(0,1fr)_340px] xl:grid-rows-none">
    <aside aria-label="报告模板" class="flex flex-col gap-1.5 border-r border-line-1 bg-surface-1 px-3.5 py-5 max-xl:col-span-full max-xl:grid max-xl:grid-cols-2 max-xl:gap-2 max-xl:border-r-0 max-xl:border-b max-xl:px-5 max-xl:py-4 lg:max-xl:grid-cols-4">
      <div class="px-2 pb-2 text-lg font-semibold max-xl:col-span-full max-xl:px-0 max-xl:pb-0">报告模板</div>
      <div v-press
        v-for="(t, i) in data.templates"
        :key="t.name"
        :class="cn(
          'cursor-pointer rounded-[10px] border p-3 hover:bg-white max-xl:min-h-11',
          i === tplIdx ? 'border-brand-line bg-white' : 'border-transparent bg-transparent max-xl:border-line-1 max-xl:bg-white/60',
        )"
        @click="pickTpl(i)"
      >
        <div class="font-semibold">{{ t.name }}<span v-if="t.dirty" class="ml-1.5 align-[1px] text-[11px] font-normal whitespace-nowrap text-warn-ink max-xl:text-xs">· 未保存</span></div>
        <div class="text-[11px] text-ink-4 max-xl:text-xs">{{ t.desc }} · v{{ t.version }}</div>
      </div>
    </aside>

    <main class="flex min-w-0 flex-col gap-3.5 px-7 pt-6 pb-12 max-xl:px-5 max-xl:pb-8">
      <div class="flex flex-wrap items-end justify-between gap-x-5 gap-y-2">
        <div class="min-w-0 flex-1">
          <div class="text-[22px] font-semibold">{{ tpl.name }}</div>
          <div class="text-xs text-ink-4">
            {{ tpl.desc }} · 被引用 {{ tpl.refs }} 次 · v{{ tpl.version }}
            <span v-if="tpl.dirty" class="text-warn-ink"> · 有未保存的修改,保存后生成 v{{ tpl.version + 1 }}</span>
          </div>
        </div>
        <Button class="h-9 max-xl:h-10" :disabled="busy || !tpl.dirty" :title="tpl.dirty ? undefined : '当前模板没有未保存的修改'" @click="save">保存为新版本</Button>
      </div>
      <div v-press
        v-for="(s, i) in tpl.sections"
        :key="tpl.name + s.name"
        :class="cn(
          'grid cursor-pointer grid-cols-[28px_minmax(0,1fr)_minmax(120px,300px)] items-center max-xl:grid-cols-[28px_minmax(0,1fr)] max-xl:min-h-11 gap-3.5 max-xl:gap-y-2.5 rounded-xl border-[1.5px] bg-white px-4 py-3.5',
          i === secIdx ? 'border-brand' : 'border-line-1',
        )"
        @click="secIdx = i"
      >
        <span class="yb-num font-semibold text-ink-5">{{ pad(i + 1) }}</span>
        <div class="min-w-0">
          <div class="font-semibold">{{ s.name }}</div>
          <div class="text-xs text-ink-4">{{ s.indicators === '—' ? '自由内容' : '绑定指标:' + s.indicators }}</div>
        </div>
        <div class="flex flex-wrap justify-end gap-1.5 max-xl:col-start-2 max-xl:justify-start max-xl:gap-2">
          <span v-for="c in s.charts" :key="c" class="flex items-center gap-0.5 rounded-lg bg-surface-3 py-0.5 pr-0.5 pl-2.5 text-xs whitespace-nowrap text-ink-2">
            {{ c }}
            <button
              type="button"
              class="relative flex size-6 cursor-pointer items-center justify-center rounded-md text-ink-4 hover:bg-line-2 hover:text-ink-1 max-xl:size-8 max-xl:after:absolute max-xl:after:-inset-1"
              :aria-label="`从 ${s.name} 移除 ${c}`"
              :title="`移除 ${c}`"
              @click.stop="removeChart(i, c)"
            >×</button>
          </span>
          <span v-if="s.charts.length === 0" class="text-xs text-ink-5">暂无图表</span>
        </div>
      </div>
    </main>

    <aside aria-label="图表组件库" class="border-l border-line-1 bg-white px-5 py-[22px] max-xl:px-4 max-md:border-t max-md:border-l-0">
     <div class="flex flex-col gap-3 max-xl:sticky max-xl:top-[calc(var(--sticky-top)+16px)] max-md:static">
      <div class="text-xs font-semibold text-ink-4">图表组件库 · 点击加入“{{ secName }}”</div>
      <div class="grid grid-cols-2 gap-2 max-md:grid-cols-3">
        <div v-press
          v-for="c in data.library"
          :key="c.name"
          :aria-disabled="tpl.sections[secIdx]?.charts.includes(c.name) || undefined"
          :class="cn(
            'flex cursor-pointer flex-col gap-2 rounded-[10px] border border-line-1 p-2.5 hover:border-brand-line',
            tpl.sections[secIdx]?.charts.includes(c.name) && 'opacity-55',
          )"
          @click="addChart(c)"
        >
          <ChartThumb :thumb="c.thumb" />
          <div class="text-xs font-medium">{{ c.name }}<span v-if="tpl.sections[secIdx]?.charts.includes(c.name)" class="ml-1 font-normal text-ink-5">· 已加入</span></div>
          <div class="-mt-1.5 text-[11px] text-ink-5 max-xl:text-xs">{{ c.desc }}</div>
        </div>
      </div>
     </div>
    </aside>
  </section>
</template>
