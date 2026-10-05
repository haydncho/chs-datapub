<script setup lang="ts">
import { ref } from 'vue'
import { cn } from '@/lib/utils'

/** 分段单选控件(radiogroup 语义,方向键切换,选中项深色)。 */
defineProps<{ options: string[]; label: string }>()
const model = defineModel<number>({ required: true })
const root = ref<HTMLElement>()

function move(e: KeyboardEvent, n: number) {
  const d = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0
  if (!d) return
  e.preventDefault()
  const i = (model.value + d + n) % n
  model.value = i
  root.value?.querySelectorAll<HTMLElement>('[role=radio]')[i]?.focus()
}
</script>

<template>
  <div ref="root" role="radiogroup" :aria-label="label" class="flex w-max max-w-full overflow-x-auto rounded-lg border border-line-1">
    <button
      v-for="(l, i) in options"
      :key="l"
      type="button"
      role="radio"
      :aria-checked="model === i"
      :tabindex="model === i ? 0 : -1"
      :class="cn(
        'cursor-pointer px-3.5 py-1.5 max-xl:min-h-10 max-xl:px-4 text-xs whitespace-nowrap focus-visible:outline-offset-[-2px]',
        model === i ? 'bg-ink-1 text-white' : 'bg-white text-ink-3 hover:bg-surface-1',
      )"
      @click="model = i"
      @keydown="move($event, options.length)"
    >{{ l }}</button>
  </div>
</template>
