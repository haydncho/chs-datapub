<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import { computed, ref } from 'vue'
import { useTheme } from '@/composables/useTheme'
import { useAuthStore } from '@/stores/auth'

/**
 * 实名动态水印：整页斜铺「姓名 机构 YYYY-MM-DD HH:mm」，每分钟刷新时间；固定定位、不拦截点击，打印时保留。
 */
const props = withDefaults(defineProps<{ light?: boolean }>(), { light: false })
const auth = useAuthStore()
const { resolved } = useTheme()
const now = ref(new Date())
useIntervalFn(() => (now.value = new Date()), 60_000)

const pad = (n: number) => String(n).padStart(2, '0')
const text = computed(() => {
  const u = auth.user
  if (!u) return ''
  const d = now.value
  return `${u.name} ${u.org} ${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
})

const esc = (s: string) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

const bg = computed(() => {
  const a = props.light ? 0.05 : 0.085
  const fill = resolved.value === 'dark' ? `rgba(255,255,255,${a * 0.7})` : `rgba(29,38,51,${a})`
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="300" height="150">` +
    `<text x="150" y="75" text-anchor="middle" dominant-baseline="middle" transform="rotate(-24 150 75)" ` +
    `font-family="Inter, PingFang SC, Microsoft YaHei, sans-serif" font-size="13" fill="${fill}">${esc(text.value)}</text></svg>`
  return `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`
})
</script>

<template>
  <div
    v-if="text"
    class="pointer-events-none fixed inset-0 z-[90] print:block"
    :style="{ backgroundImage: bg, backgroundRepeat: 'repeat' }"
    aria-hidden="true"
    data-testid="page-watermark"
    :data-text="text"
  />
</template>
