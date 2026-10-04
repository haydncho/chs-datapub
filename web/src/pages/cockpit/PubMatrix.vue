<script setup lang="ts">
import { cn } from '@/lib/utils'
import { useCockpit } from './store'

/** `screen2` renders the larger variant used on the dual-screen secondary display. */
const props = withDefaults(defineProps<{ screen2?: boolean }>(), { screen2: false })
const { matrix, data } = useCockpit()
</script>

<template>
  <div v-if="!props.screen2" class="mb-3 flex gap-2.5">
    <span class="rounded-md bg-[rgba(255,90,78,.14)] px-3.5 py-1.5 text-[15px] text-[#FF8A7E]"><b class="yb-num text-xl">{{ data.matrixSummary.unpublished }}</b> 应公开未公开</span>
    <span class="rounded-md bg-[rgba(245,183,78,.14)] px-3.5 py-1.5 text-[15px] text-[#F5B74E]"><b class="yb-num text-xl">{{ data.matrixSummary.unread }}</b> 发了没人看</span>
    <span class="rounded-md bg-[rgba(160,130,255,.16)] px-3.5 py-1.5 text-[15px] text-[#B9A2FF]"><b class="yb-num text-xl">{{ data.matrixSummary.unanswered }}</b> 意见集中未答复</span>
  </div>
  <div :class="cn('grid grid-cols-[200px_repeat(7,minmax(0,1fr))]', props.screen2 ? 'gap-2' : 'gap-1.5')">
    <span :class="cn('px-1 text-[#6F84A6]', props.screen2 ? 'py-2 text-sm' : 'py-1.5 text-[13px]')">指标 \ 受众</span>
    <span
      v-for="a in data.audiences"
      :key="a"
      :class="cn('px-1 text-center text-[15px] font-semibold text-[#AEBBD0]', props.screen2 ? 'py-2 whitespace-nowrap' : 'py-1.5')"
    >{{ a }}</span>
  </div>
  <div
    v-for="r in matrix"
    :key="r.nm"
    :class="cn('grid grid-cols-[200px_repeat(7,minmax(0,1fr))]', props.screen2 ? 'mt-2.5 gap-2' : 'mt-1.5 gap-1.5')"
    :style="{ opacity: r.op }"
  >
    <span :class="cn('px-1', props.screen2 ? 'py-4 text-[17px] whitespace-nowrap' : 'py-2.5 text-[15px]')">{{ r.nm }}</span>
    <span
      v-for="(x, i) in r.cells"
      :key="i"
      :class="cn(
        'px-1 text-center font-semibold whitespace-nowrap',
        props.screen2 ? 'rounded-md py-4 text-[15px]' : 'rounded py-2.5 text-sm',
      )"
      :style="{ background: x.bg, color: x.fg }"
    >{{ x.txt }}</span>
  </div>
</template>
