<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import { computed, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * D1 移动端满屏实名水印：2 列 × 90px 行、透明度 0.1（高于 PC 端），文字「姓名 机构简称 MM-DD」，每分钟刷新日期。
 * 覆盖 390 宽内屏，固定定位、不拦截点击。
 */
const auth = useAuthStore()
const now = ref(new Date())
useIntervalFn(() => (now.value = new Date()), 60_000)
const pad = (n: number) => String(n).padStart(2, '0')
const text = computed(() => {
  const u = auth.user
  if (!u) return ''
  const d = now.value
  return `${u.name} ${u.org.replace(/^示例市/, '')} ${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})
const CELLS = 40
</script>

<template>
  <div
    v-if="text"
    class="pointer-events-none fixed inset-y-0 left-1/2 z-[90] grid w-full max-w-[390px] -translate-x-1/2 grid-cols-2 overflow-hidden text-ink"
    style="grid-auto-rows: 90px; opacity: 0.1"
    aria-hidden="true"
    data-testid="mobile-watermark"
    :data-text="text"
  >
    <div v-for="n in CELLS" :key="n" class="flex items-center justify-center">
      <span class="rotate-[-28deg] text-[12px] whitespace-nowrap">{{ text }}</span>
    </div>
  </div>
</template>
