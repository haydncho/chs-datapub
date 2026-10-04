<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { pageDef, pagePath, workspaceOf } from '@/lib/nav'
import { useAuthStore } from '@/stores/auth'

/**
 * 工作区页签:同一业务任务的页面共用一条页签 / 步骤条。
 * 月度发布(numbered)按 01 → 05 编号,表示主线先后顺序;其余工作区为并列页签。只显示当前身份可见的成员。
 */
const route = useRoute()
const auth = useAuthStore()
const ws = computed(() => workspaceOf(String(route.meta.page)))
const items = computed(() =>
  (ws.value?.members ?? []).filter((m) => auth.user?.pages.includes(m)).map((m) => pageDef(m)!),
)
const labelOf = (key: string, label: string) => (key === 'W1' ? '总览向导' : label)
</script>

<template>
  <nav v-if="ws && items.length > 1" class="mb-4 flex flex-wrap items-center gap-1.5" :aria-label="`${ws.label}页签`" data-testid="ws-tabs">
    <span class="mr-2 text-[12px] font-semibold tracking-[.06em] text-ink-muted">{{ ws.label }}</span>
    <RouterLink
      v-for="(p, i) in items"
      :key="p.key"
      :to="pagePath(p)"
      class="flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-[13px] no-underline transition-colors hover:no-underline"
      :class="p.key === route.meta.page ? 'border-primary bg-primary-tint font-semibold text-primary' : 'border-line bg-surface text-ink-sub hover:bg-hover'"
      :data-ws-tab="p.key"
    >
      <span v-if="ws.numbered && p.key !== 'W1'" class="flex size-[18px] items-center justify-center rounded-full text-[11px]" :class="p.key === route.meta.page ? 'bg-primary-solid text-white' : 'bg-chip text-ink-muted'">{{ i }}</span>
      {{ labelOf(p.key, p.label) }}
    </RouterLink>
  </nav>
</template>
