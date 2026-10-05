<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { appearance } from '@/app/appearance'

/**
 * 数字滚动:值变化(或 replay 计数变化)时,从 0 缓动到目标值。
 * 保留原字符串里的前缀 / 后缀 / 小数位 / 千分位("+1,860"、"97.6%"、"9/10")。外观里动效设为「关闭」时直接显示终值。
 */
const props = defineProps<{ value: string; replay?: number; duration?: number }>()
const shown = ref(props.value)
let raf = 0

const NUM = /\d[\d,]*(?:\.\d+)?/
function parse(v: string) {
  const m = NUM.exec(v)
  if (!m) return null
  const raw = m[0]
  return {
    pre: v.slice(0, m.index),
    post: v.slice(m.index + raw.length),
    n: parseFloat(raw.replace(/,/g, '')),
    dec: (raw.split('.')[1] ?? '').length,
    comma: raw.includes(','),
  }
}
function render(p: NonNullable<ReturnType<typeof parse>>, n: number) {
  let t = n.toFixed(p.dec)
  if (p.comma) {
    const [i, d] = t.split('.')
    t = Number(i).toLocaleString('en-US') + (d ? '.' + d : '')
  }
  return p.pre + t + p.post
}
function run() {
  cancelAnimationFrame(raf)
  const p = parse(props.value)
  if (!p || appearance.mot === 0) { shown.value = props.value; return }
  const t0 = performance.now()
  const d = props.duration ?? 900
  const step = (now: number) => {
    const k = Math.min(1, (now - t0) / d)
    shown.value = render(p, p.n * (1 - Math.pow(1 - k, 3)))
    if (k < 1) raf = requestAnimationFrame(step)
    else shown.value = props.value
  }
  raf = requestAnimationFrame(step)
}
onMounted(run)
watch(() => [props.value, props.replay], run)
onBeforeUnmount(() => cancelAnimationFrame(raf))
</script>

<template><span>{{ shown }}</span></template>
