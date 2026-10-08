<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import { isUrgent, useA8 } from './store'

const s = useA8()

const meta = computed(() => {
  const st = s.step
  return [
    { k: '当前步骤', v: st >= 10 ? '已归档' : st + ' / 10 · ' + s.stepName(st), warn: st === 5 },
    { k: '承办人', v: st >= 10 ? '—' : (s.d.owners[st - 1] ?? '—'), warn: false },
    { k: '本步停留', v: st === 5 ? s.d.gateDwell : '—', warn: false },
  ]
})

const SG: [string, number[]][] = [['准备', [1, 2, 3]], ['审核', [4, 5]], ['发布', [6, 7]], ['反馈', [8, 9, 10]]]
const stages = computed(() => {
  const step = s.step
  return SG.map(([n, ids]) => {
    const done = step >= 10 || ids.every(i => i < step)
    const act = ids.includes(step) && step < 10
    const gate = step === 5
    return {
      n: n + ' · ' + ids.length + ' 步',
      st: done ? '已完成' : act ? (gate ? '待召集人审批' : '进行中') : '未开始',
      sc: done ? 'text-ok-ink' : act ? (gate ? 'text-warn-ink' : 'text-brand') : 'text-ink-5',
      tc: act ? 'text-ink-1' : 'text-ink-3',
      box: act ? (gate ? 'border-[#F6DFB8] bg-[#FFFBF4]' : 'border-brand-line bg-brand-tint') : 'border-line-1 bg-white',
      steps: ids.map(i => {
        const d = i < step || step >= 10
        const on = i === step && step < 10
        return {
          n: i,
          l: s.stepName(i),
          bar: d ? 'bg-ok' : on ? (i === 5 ? 'bg-warn' : 'bg-brand') : 'bg-line-1',
          nc: d ? 'text-ok-ink' : on ? 'text-brand' : 'text-ink-6',
          lc: on ? 'font-semibold text-ink-1' : d ? 'text-ink-3' : 'text-ink-5',
          gate: i === 5,
        }
      }),
    }
  })
})
</script>

<template>
  <div class="flex items-end gap-5 max-xl:flex-wrap">
    <div class="min-w-0 flex-1">
      <div class="flex flex-wrap items-center gap-2 text-xs">
        <span class="rounded-md bg-surface-3 px-2 py-0.5 whitespace-nowrap text-ink-3">{{ s.cur.group }}</span>
        <span class="rounded-md bg-brand-soft px-2 py-0.5 whitespace-nowrap text-brand">{{ s.d.packageVersion }}</span>
        <span
          :class="cn('rounded-md px-2 py-0.5 font-medium whitespace-nowrap', isUrgent(s.cur) ? 'bg-bad-soft text-bad-ink' : 'bg-surface-3 text-ink-3')"
        >{{ s.cur.due }}</span>
      </div>
      <div class="mt-2 text-lg font-semibold tracking-[-0.2px] text-pretty">{{ s.cur.name }}</div>
    </div>
    <div class="flex rounded-xl border border-line-1 bg-white max-xl:w-full xl:w-auto">
      <div v-for="m in meta" :key="m.k" class="border-l border-line-2 px-[18px] py-2 whitespace-nowrap first:border-l-0 max-xl:min-w-0 max-xl:flex-1 max-xl:px-4 max-sm:px-3 max-sm:whitespace-normal">
        <div class="text-[11px] text-ink-4">{{ m.k }}</div>
        <div :class="cn('font-semibold', m.warn ? 'text-warn-ink' : 'text-ink-1')">{{ m.v }}</div>
      </div>
    </div>
  </div>

  <div class="grid grid-cols-[minmax(0,3fr)_minmax(0,2fr)_minmax(0,2fr)_minmax(0,3fr)] gap-2.5 max-xl:grid-cols-2 max-sm:grid-cols-1">
    <div v-for="g in stages" :key="g.n" :class="cn('flex flex-col gap-2.5 rounded-xl border px-3.5 py-3', g.box)">
      <div class="flex items-center justify-between gap-2">
        <span :class="cn('text-xs font-semibold whitespace-nowrap', g.tc)">{{ g.n }}</span>
        <span :class="cn('min-w-0 truncate text-[11px]', g.sc)" :title="g.st">{{ g.st }}</span>
      </div>
      <div class="flex gap-1.5">
        <div v-for="st in g.steps" :key="st.n" class="flex min-w-0 flex-1 flex-col gap-[5px]">
          <div :class="cn('h-1 rounded-[2px]', st.bar)" />
          <div class="flex min-w-0 items-center gap-1 whitespace-nowrap">
            <span :class="cn('yb-num text-[11px] font-semibold', st.nc)">{{ st.n }}</span>
            <span :class="cn('overflow-hidden text-xs text-ellipsis', st.lc)" :title="st.l">{{ st.l }}</span>
          </div>
          <span
            v-if="st.gate"
            class="self-start rounded-full bg-bad-soft px-1.5 py-px text-[10px] font-semibold whitespace-nowrap text-bad"
          >未批准不外发</span>
        </div>
      </div>
    </div>
  </div>
</template>
