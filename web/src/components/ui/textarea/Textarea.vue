<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { useVModel } from "@vueuse/core"
import { cn } from "@/lib/utils"

const props = defineProps<{
  class?: HTMLAttributes["class"]
  defaultValue?: string | number
  modelValue?: string | number
}>()

const emits = defineEmits<{
  (e: "update:modelValue", payload: string | number): void
}>()

const modelValue = useVModel(props, "modelValue", emits, {
  passive: true,
  defaultValue: props.defaultValue,
})
</script>

<template>
  <textarea
    v-model="modelValue"
    data-slot="textarea"
    :class="cn('flex w-full rounded-lg border border-line bg-surface px-3 py-[9px] text-[13px] leading-[1.6] text-ink outline-none transition-colors placeholder:text-ink-faint focus:border-ring disabled:cursor-not-allowed disabled:opacity-50', props.class)"
  />
</template>
