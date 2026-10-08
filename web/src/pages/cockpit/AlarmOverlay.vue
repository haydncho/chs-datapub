<script setup lang="ts">
import { useCockpit } from './store'

defineProps<{ busy?: boolean }>()
const emit = defineEmits<{ ack: [] }>()
const { alarm } = useCockpit()
</script>

<template>
  <div
    class="pointer-events-none absolute inset-0 z-[150] border-[6px] border-[#FF3B30] shadow-[inset_0_0_80px_rgba(255,59,48,.45)] [animation:ybAlarm_1s_ease-in-out_infinite]"
  />
  <div
    class="absolute top-24 left-1/2 z-[160] flex w-[1000px] -translate-x-1/2 items-center gap-[22px] rounded-[14px] border-[1.5px] border-[#FF5A4E] bg-[rgba(36,6,8,.95)] px-[26px] py-[18px] shadow-[0_0_48px_rgba(255,59,48,.55)]"
  >
    <div class="relative size-[52px] shrink-0">
      <span class="absolute inset-0 rounded-full border-2 border-[#FF5A4E] [animation:ybRing_1.4s_ease-out_infinite]" />
      <span class="absolute inset-0 flex items-center justify-center rounded-full bg-[#FF3B30] text-[28px] font-bold text-white">!</span>
    </div>
    <div class="min-w-0 flex-1">
      <div class="text-[15px] font-semibold tracking-[2px] text-[#FFB2AA]">{{ alarm.lv }} · {{ alarm.ty }}</div>
      <div class="truncate text-[26px] font-semibold text-white">{{ alarm.t }}</div>
      <div class="text-[15px] text-[#E6A9A3]">{{ alarm.sub }}</div>
    </div>
    <div class="shrink-0 text-right">
      <div class="yb-num text-2xl text-white">{{ alarm.time }}</div>
      <button
        type="button"
        class="mt-2 h-10 cursor-pointer rounded-lg border-0 bg-[#FF3B30] px-[22px] text-base font-semibold text-white disabled:cursor-wait disabled:opacity-70"
        :disabled="busy"
        data-testid="alarm-ack"
        @click="emit('ack')"
      >{{ busy ? '提交中…' : '确认处置' }}</button>
    </div>
  </div>
</template>
