<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useCockpit } from './store'

/** `screen2` renders the larger variant used on the dual-screen secondary display. */
const props = withDefaults(defineProps<{ screen2?: boolean }>(), { screen2: false })
const { peer } = useCockpit()

const dotStyle = (d: { me: boolean; x: string; tr: string }) => ({
  left: d.x,
  top: d.me ? '6px' : '9px',
  marginTop: props.screen2 ? '3px' : undefined,
  width: d.me ? '18px' : '12px',
  height: d.me ? '18px' : '12px',
  marginLeft: d.me ? '-9px' : '-6px',
  background: d.me ? 'rgb(var(--ck-acc))' : 'rgba(159,178,209,.45)',
  boxShadow: d.me ? '0 0 0 3px rgb(var(--ck-acc)/.25), 0 0 16px rgb(var(--ck-acc))' : 'none',
  zIndex: d.me ? 5 : 1,
  transition: props.screen2 ? undefined : d.tr,
})
</script>

<template>
  <div :class="cn('flex flex-col', props.screen2 ? 'min-h-0 flex-1 justify-around' : 'h-full justify-between py-0.5')">
    <div
      v-if="!props.screen2"
      class="grid grid-cols-[180px_minmax(0,1fr)_150px] gap-6 text-[13px] text-[#6F84A6]"
    >
      <span>指标</span>
      <span class="flex justify-between"><span>← 较差</span><span>同级 市三级 6 家 · 匿名 · 浅带为 P25–P75</span><span>较好 →</span></span>
      <span class="text-right">本院 · 同级分位</span>
    </div>
    <div
      v-for="r in peer"
      :key="r.n"
      :class="cn(
        'grid items-center',
        props.screen2 ? 'grid-cols-[200px_minmax(0,1fr)_170px] gap-7' : 'grid-cols-[180px_minmax(0,1fr)_150px] gap-6',
      )"
    >
      <span :class="cn('whitespace-nowrap', props.screen2 ? 'text-[19px]' : 'text-base')">{{ r.n }}</span>
      <div :class="cn('relative', props.screen2 ? 'h-9' : 'h-[30px]')">
        <div :class="cn('absolute inset-x-0 h-0.5 bg-[rgba(255,255,255,.08)]', props.screen2 ? 'top-[17px]' : 'top-3.5')" />
        <div
          :class="cn(
            'absolute left-1/4 w-1/2 rounded-[3px] bg-[rgb(var(--ck-acc)/.12)]',
            props.screen2 ? 'top-[11px] h-3.5' : 'top-[9px] h-3',
          )"
        />
        <span v-for="(d, i) in r.dots" :key="i" class="absolute rounded-full" :style="dotStyle(d)" />
      </div>
      <div class="text-right whitespace-nowrap">
        <span :class="cn('yb-num font-semibold text-[rgb(var(--ck-accl))]', props.screen2 ? 'text-[26px]' : 'text-[22px]')">{{ r.v }}</span>
        <span
          :class="cn('yb-num font-semibold', props.screen2 ? 'ml-3 text-[17px]' : 'ml-2.5 text-sm')"
          :style="{ color: r.pc }"
        >{{ r.p }}</span>
      </div>
    </div>
  </div>
</template>
