<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { A9Version } from '@/mock/A9'

defineProps<{ checks: { ok: boolean; text: string }[]; versions: A9Version[] }>()
</script>

<template>
  <div class="flex flex-col gap-2 rounded-[var(--radius-card)] border border-line-1 bg-white px-[18px] py-4">
    <div class="mb-0.5 text-[13px] font-semibold">流程校验</div>
    <div v-for="c in checks" :key="c.text" class="flex items-center gap-2 text-xs">
      <span :class="cn('flex size-4 shrink-0 items-center justify-center rounded-full text-[10px] text-white', c.ok ? 'bg-ok' : 'bg-bad')">{{ c.ok ? '✓' : '!' }}</span>
      <span :class="c.ok ? 'text-ink-2' : 'text-bad-ink'">{{ c.text }}</span>
    </div>
    <div class="mt-1.5 flex flex-col gap-1.5 border-t border-line-2 pt-2.5 text-xs">
      <template v-for="(v, i) in versions" :key="v.label">
        <div :class="cn('flex justify-between', i > 0 && 'mt-1')">
          <b v-if="v.current" class="font-semibold">{{ v.label }}</b>
          <span v-else class="font-medium">{{ v.label }}</span>
          <span class="text-ink-5">{{ v.date }}</span>
        </div>
        <div class="text-ink-4">{{ v.note }}</div>
      </template>
    </div>
  </div>
</template>
