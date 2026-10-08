<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { A7Collaborator, A7Topic, A7TrackStep } from '@/mock/A7'

const props = defineProps<{
  topic: A7Topic
  collaborators: A7Collaborator[]
  track: A7TrackStep[]
  nOk: number
  total: number
  /** already submitted for 核对与审核 (once) */
  submitted?: boolean
  busy?: boolean
}>()
defineEmits<{ submit: [] }>()

const AVATAR: Record<A7Collaborator['tone'], string> = {
  brand: 'bg-brand-soft text-brand',
  violet: 'bg-violet-soft text-violet',
  warn: 'bg-warn-soft text-warn-ink',
}
const DOT = { done: 'bg-ok', cur: 'bg-brand', todo: 'bg-ink-6' } as const

/** ring circumference for r = 15 is ~94.2 */
const dash = computed(() => `${((props.nOk / props.total) * 94.2).toFixed(1)} 999`)
const ready = computed(() => props.nOk === props.total)
</script>

<template>
  <div class="sticky top-(--sticky-top) z-30 border-b border-line-1 bg-white px-8 pt-[18px] pb-3.5 max-xl:static max-xl:px-5">
    <div class="flex items-start gap-5 max-xl:flex-wrap">
      <div class="min-w-0 flex-1 max-xl:basis-full">
        <div class="flex flex-wrap items-center gap-1.5 text-[11px]">
          <Badge variant="ok" class="px-2 py-0.5 text-[11px] font-medium">{{ topic.recommendTag }}</Badge>
          <Badge variant="brand" class="px-2 py-0.5 text-[11px] font-normal">{{ topic.envTag }}</Badge>
          <span class="text-ink-5">{{ topic.meta }}</span>
        </div>
        <div class="mt-1.5 text-[22px] font-semibold text-pretty break-words max-sm:text-lg">
          <span class="yb-num text-brand">{{ topic.code }}</span> {{ topic.name }}
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-4 max-xl:flex-wrap max-xl:gap-y-3">
        <div class="flex">
          <span
            v-for="(c, i) in collaborators"
            :key="c.name"
            :title="`${c.name} · ${c.role}`"
            :class="cn(
              'flex size-[30px] items-center justify-center rounded-full border-2 border-white text-xs font-semibold',
              AVATAR[c.tone],
              i > 0 && '-ml-2',
            )"
          >{{ c.name[0] }}</span>
        </div>
        <div class="flex items-center gap-2.5">
          <div class="relative size-[38px]">
            <svg viewBox="0 0 38 38" class="absolute inset-0 -rotate-90">
              <circle cx="19" cy="19" r="15" fill="none" stroke="#EEF1F5" stroke-width="4" />
              <circle
                cx="19" cy="19" r="15" fill="none" stroke="var(--ok)" stroke-width="4" stroke-linecap="round"
                :stroke-dasharray="dash" class="transition-[stroke-dasharray] duration-300"
              />
            </svg>
          </div>
          <div class="leading-[1.3]">
            <div class="text-[11px] text-ink-4">人工审定</div>
            <div class="yb-num text-lg font-semibold">{{ nOk }}/{{ total }}</div>
          </div>
        </div>
        <Button
          v-if="submitted"
          disabled
          variant="soft"
          class="h-[38px] rounded-[10px] px-[18px] text-[13px] max-xl:h-11"
        >已提交 · 核对与审核中</Button>
        <Button
          v-else
          :disabled="busy"
          :class="cn('h-[38px] rounded-[10px] px-[18px] text-[13px] max-xl:h-11', !ready && 'bg-brand-mute')"
          @click="$emit('submit')"
        >提交核对与审核</Button>
      </div>
    </div>
    <div class="mt-3.5 flex items-center gap-2.5 max-xl:flex-wrap max-xl:gap-y-2 max-lg:grid max-lg:grid-cols-5 max-lg:items-start max-lg:gap-3 max-sm:grid-cols-2">
      <div v-for="(t, i) in track" :key="t.label" class="flex shrink-0 items-center gap-2.5 max-lg:min-w-0">
        <div class="flex items-center gap-2 max-lg:flex-wrap max-lg:gap-x-1.5 max-lg:gap-y-0.5">
          <span :class="cn('size-2 shrink-0 rounded-full', DOT[t.state])" />
          <span
            :class="cn(
              'text-xs whitespace-nowrap max-lg:min-w-0 max-lg:flex-1 max-lg:break-keep max-lg:whitespace-normal',
              t.state === 'todo' ? 'text-ink-5' : 'text-ink-1',
              t.state === 'cur' ? 'font-semibold' : 'font-medium',
            )"
          >{{ t.label }}</span>
          <span class="text-[11px] text-ink-5 max-lg:basis-full max-lg:pl-3.5">{{ t.progress ? `${nOk}/${total}` : t.sub }}</span>
        </div>
        <span v-if="i < track.length - 1" class="h-px w-9 bg-line-4 max-xl:w-6 max-lg:hidden" aria-hidden="true" />
      </div>
    </div>
  </div>
</template>
