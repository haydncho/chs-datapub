<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { FormulaCheck } from '@/api/indicator'

/**
 * 公式编辑器：函数按钮插入 + 带行号代码区（语法高亮层 + 透明文本框）+ 底部校验结果（引擎逐行报错）。
 */
const props = defineProps<{ functions: string[]; check: FormulaCheck | null; checking: boolean; disabled?: boolean }>()
const emit = defineEmits<{ generate: [] }>()
const model = defineModel<string>({ required: true })
const ta = ref<HTMLTextAreaElement | null>(null)
const hl = ref<HTMLElement | null>(null)

const FN = /\b(SUM|COUNT|AVG|PCTL|RATIO)\b/g
const KW = /\b(WHERE|AND|OR|GROUP BY|IN|NOT)\b/g
const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')

const errLines = computed(() => new Set((props.check && !props.check.ok ? props.check.errors : []).map((e) => e.line)))
const lines = computed(() => model.value.split('\n'))
/** 高亮：首行指标名称（主色）、函数与关键字（紫）、字符串（绿）。 */
const html = computed(() =>
  lines.value
    .map((raw, i) => {
      let s = esc(raw)
      if (i === 0) s = s.replace(/^(\s*)([^=\s][^=]*?)(\s*=)/, '$1<b class="t-name">$2</b>$3')
      s = s.replace(/'[^']*'/g, (m) => `<span class="t-str">${m}</span>`)
      s = s.replace(FN, '<span class="t-fn">$1</span>').replace(KW, '<span class="t-fn">$1</span>')
      return `<div class="${errLines.value.has(i + 1) ? 't-err' : ''}">${s || '&#8203;'}</div>`
    })
    .join(''),
)

function sync() {
  if (ta.value && hl.value) {
    hl.value.scrollTop = ta.value.scrollTop
    hl.value.scrollLeft = ta.value.scrollLeft
  }
}

async function insert(fn: string) {
  const el = ta.value
  if (!el || props.disabled) return
  const snippet = fn === 'WHERE' ? '\nWHERE ' : `${fn}()`
  const a = el.selectionStart ?? model.value.length
  const b = el.selectionEnd ?? a
  model.value = model.value.slice(0, a) + snippet + model.value.slice(b)
  await nextTick()
  el.focus()
  const caret = fn === 'WHERE' ? a + snippet.length : a + fn.length + 1
  el.setSelectionRange(caret, caret)
}
</script>

<template>
  <div class="overflow-hidden rounded-lg border border-line" :class="disabled && 'opacity-70'">
    <div class="flex items-center gap-1.5 border-b border-line bg-th px-2 py-1.5 font-mono text-[11px]">
      <button
        v-for="f in functions"
        :key="f"
        type="button"
        class="cursor-pointer rounded-[3px] border border-line bg-surface px-1.5 py-px text-ink-sub hover:border-primary hover:text-primary disabled:cursor-not-allowed"
        :disabled="disabled"
        :data-fn="f"
        @click="insert(f)"
      >{{ f }}</button>
      <button type="button" class="ml-auto cursor-pointer font-sans text-[11px] text-primary hover:underline disabled:cursor-not-allowed disabled:opacity-50" :disabled="disabled" data-testid="gen-formula" @click="emit('generate')">按左侧配置生成</button>
    </div>
    <div class="relative flex min-h-[170px] bg-surface font-mono text-[12px] leading-[1.9]">
      <div class="select-none border-r border-divider px-2 py-3 text-right text-ink-faint" aria-hidden="true">
        <div v-for="(_, i) in lines" :key="i" :class="errLines.has(i + 1) && 'text-danger'">{{ i + 1 }}</div>
      </div>
      <div class="relative min-w-0 flex-1">
        <!-- eslint-disable-next-line vue/no-v-html -- 已转义，仅包裹高亮标签 -->
        <div ref="hl" class="code-layer pointer-events-none absolute inset-0 overflow-hidden text-ink" aria-hidden="true" v-html="html" />
        <textarea
          ref="ta"
          v-model="model"
          spellcheck="false"
          :readonly="disabled"
          class="code-layer relative block h-full min-h-[170px] w-full resize-none bg-transparent text-transparent caret-[var(--c-text)] outline-none"
          aria-label="公式"
          data-testid="formula-input"
          @scroll="sync"
        />
      </div>
    </div>
    <div
      class="border-t border-line px-2.5 py-1.5 text-[11px]"
      :class="checking || !check ? 'bg-th text-ink-muted' : check.ok ? 'bg-success-soft text-success-ink' : 'bg-danger-soft text-danger-ink'"
      data-testid="formula-status"
      :data-ok="check?.ok"
    >
      <template v-if="checking || !check">校验中…</template>
      <template v-else>
        {{ check.message }}
        <div v-for="e in check.errors.slice(1)" :key="e.line + e.message">✗ 第 {{ e.line }} 行:{{ e.message }}</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.code-layer {
  padding: 12px;
  white-space: pre;
  font: inherit;
  line-height: inherit;
  tab-size: 2;
  overflow-wrap: normal;
}
textarea.code-layer {
  overflow: auto;
}
textarea.code-layer::selection {
  background: var(--c-primary-soft);
  color: transparent;
}
.code-layer :deep(.t-name) { color: var(--c-primary); font-weight: 400; }
.code-layer :deep(.t-fn) { color: var(--c-ai); }
.code-layer :deep(.t-str) { color: var(--c-success); }
.code-layer :deep(.t-err) { background: var(--c-danger-soft); }
</style>
