<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Check, TriangleAlert } from '@lucide/vue'
import { Input } from '@/components/ui/input'
import { THEME_COLORS, MIN_CONTRAST, contrastWithWhite, normalizeHex } from '@/app/appearance'

/**
 * 主题色选择:5 个预设色(aria-pressed)+ 自定义色(取色器 + 色值输入 + 对比度提示)。
 * `c` 预设序号,`custom` 自定义色('' = 未使用);`invalid` 向外报告"当前输入不可保存"。
 */
const props = defineProps<{ names: string[] }>()
const c = defineModel<number>('c', { required: true })
const custom = defineModel<string>('custom', { required: true })
const invalid = defineModel<boolean>('invalid', { default: false })

const text = ref(custom.value)
watch(custom, v => { if (normalizeHex(text.value) !== v) text.value = v })

const parsed = computed(() => normalizeHex(text.value))
const ratio = computed(() => (parsed.value ? contrastWithWhite(parsed.value) : 0))
const lowContrast = computed(() => !!parsed.value && ratio.value < MIN_CONTRAST)
const badFormat = computed(() => !!text.value.trim() && !parsed.value)
watch([lowContrast, badFormat], () => { invalid.value = lowContrast.value || badFormat.value }, { immediate: true })

function onText(v: string | number) {
  text.value = String(v)
  const h = normalizeHex(text.value)
  if (h) custom.value = h
  else if (!text.value.trim()) custom.value = ''
}
function onPicker(e: Event) {
  const h = normalizeHex((e.target as HTMLInputElement).value)
  text.value = h
  custom.value = h
}
function pick(i: number) {
  c.value = i
  custom.value = ''
  text.value = ''
}
const usingCustom = computed(() => !!custom.value)
</script>

<template>
  <div class="flex flex-col gap-3">
    <div class="flex flex-wrap items-start gap-2.5" role="group" aria-label="预设主题色">
      <button
        v-for="(col, i) in THEME_COLORS"
        :key="col"
        type="button"
        class="flex cursor-pointer flex-col items-center gap-1 rounded-lg p-0.5"
        :aria-pressed="!usingCustom && c === i"
        :aria-label="`主题色 ${props.names[i]}`"
        @click="pick(i)"
      >
        <span
          class="flex size-[34px] items-center justify-center rounded-[10px] text-white"
          :style="{ background: col, boxShadow: !usingCustom && i === c ? `0 0 0 2px #fff, 0 0 0 4px ${col}` : 'none' }"
        ><Check v-if="!usingCustom && i === c" class="size-4" :stroke-width="3" /></span>
        <span class="text-[11px] whitespace-nowrap text-ink-4">{{ props.names[i] }}</span>
      </button>
    </div>
    <div class="flex flex-wrap items-center gap-2.5 rounded-lg border border-line-1 bg-surface-1 px-3 py-2.5">
      <label
        class="relative flex size-[34px] shrink-0 cursor-pointer items-center justify-center overflow-hidden rounded-[10px] focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-ring"
        :style="{
          background: parsed || 'conic-gradient(#E5484D, #F5A623, #46A758, #3E63DD, #8E4EC6, #E5484D)',
          boxShadow: usingCustom ? `0 0 0 2px #fff, 0 0 0 4px ${custom}` : 'none',
        }"
      >
        <input
          type="color"
          :value="parsed || THEME_COLORS[c]"
          class="absolute inset-0 size-full cursor-pointer opacity-0"
          aria-label="自定义主题色 · 取色器"
          @input="onPicker"
        />
        <Check v-if="usingCustom" class="pointer-events-none size-4 text-white" :stroke-width="3" />
      </label>
      <div class="flex flex-col gap-0.5">
        <span class="text-xs font-medium">自定义主题色</span>
        <span class="text-[11px] text-ink-4">取色器或输入色值</span>
      </div>
      <Input
        :model-value="text"
        placeholder="#1E5BD8"
        maxlength="7"
        spellcheck="false"
        aria-label="自定义主题色 · 色值"
        :aria-invalid="lowContrast || badFormat"
        class="yb-num ml-auto h-8 w-[108px] rounded-lg border-line-4 bg-white px-2.5 text-[13px] uppercase shadow-none md:text-[13px]"
        @update:model-value="onText"
      />
    </div>
    <p v-if="badFormat" class="flex items-center gap-1.5 text-xs text-bad-ink" role="alert">
      <TriangleAlert class="size-3.5 shrink-0" />色值格式应为 6 位十六进制,如 #1E5BD8
    </p>
    <p v-else-if="lowContrast" class="flex items-start gap-1.5 text-xs text-bad-ink" role="alert">
      <TriangleAlert class="mt-px size-3.5 shrink-0" />
      <span>主色与白色文字的对比度仅 <b class="yb-num">{{ ratio.toFixed(2) }}:1</b>,低于 {{ MIN_CONTRAST }}:1,按钮文字将难以辨认,无法保存。请选择更深的颜色。</span>
    </p>
    <p v-else-if="parsed" class="flex items-center gap-1.5 text-xs text-ok-ink">
      <Check class="size-3.5 shrink-0" />对比度 <b class="yb-num">{{ ratio.toFixed(2) }}:1</b>,符合无障碍要求(≥ {{ MIN_CONTRAST }}:1)
    </p>
  </div>
</template>
