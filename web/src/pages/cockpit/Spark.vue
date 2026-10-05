<script setup lang="ts">
import { computed } from 'vue'

/** 迷你走势折线:渐变面积 + 描线动画 + 末点脉冲。replay 变化时重播。 */
const props = withDefaults(defineProps<{ trend: number[]; color: string; w?: number; h?: number; replay?: number }>(), { w: 120, h: 44, replay: 0 })

const geo = computed(() => {
  const t = props.trend
  const pad = 5
  const mn = Math.min(...t)
  const mx = Math.max(...t)
  const span = mx - mn || 1
  const pts = t.map((v, i) => [
    pad + (i / Math.max(1, t.length - 1)) * (props.w - pad * 2),
    props.h - pad - ((v - mn) / span) * (props.h - pad * 2),
  ] as const)
  const line = pts.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ')
  const last = pts[pts.length - 1]!
  return { line, area: `${line} L${last[0].toFixed(1)} ${props.h} L${pts[0]![0].toFixed(1)} ${props.h} Z`, lx: last[0], ly: last[1] }
})
const gid = computed(() => 'sp' + Math.abs(hash(props.color + props.trend.join())))
function hash(s: string) { let x = 0; for (const c of s) x = (x * 31 + c.charCodeAt(0)) | 0; return x }
</script>

<template>
  <svg :width="w" :height="h" :viewBox="`0 0 ${w} ${h}`" class="shrink-0 overflow-visible" aria-hidden="true">
    <defs>
      <linearGradient :id="gid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0" :stop-color="color" stop-opacity=".32" />
        <stop offset="1" :stop-color="color" stop-opacity="0" />
      </linearGradient>
    </defs>
    <g :key="replay">
      <path :d="geo.area" :fill="`url(#${gid})`" class="spark-area" />
      <path :d="geo.line" fill="none" :stroke="color" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" pathLength="1" class="spark-line" />
      <circle :cx="geo.lx" :cy="geo.ly" r="3.2" :fill="color" />
      <circle :cx="geo.lx" :cy="geo.ly" r="3.2" fill="none" :stroke="color" stroke-width="1.5" class="spark-ring" />
    </g>
  </svg>
</template>

<style>
.spark-line { stroke-dasharray: 1; stroke-dashoffset: 1; animation: cockDraw 1.1s cubic-bezier(.2, .8, .2, 1) .15s forwards; }
.spark-area { opacity: 0; animation: cockFade .6s ease .8s forwards; }
.spark-ring { transform-box: fill-box; transform-origin: center; animation: cockPulse 2.2s ease-out 1.2s infinite; }
html[data-motion="0"] .spark-line { animation: none; stroke-dashoffset: 0; }
html[data-motion="0"] .spark-area { animation: none; opacity: 1; }
html[data-motion="0"] .spark-ring { animation: none; opacity: 0; }
@keyframes cockDraw { to { stroke-dashoffset: 0 } }
@keyframes cockFade { to { opacity: 1 } }
</style>
