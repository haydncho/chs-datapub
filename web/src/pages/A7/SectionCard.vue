<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { pad } from '@/lib/format'

/**
 * One manuscript section: header (number, title, status, 审定 / 重新生成),
 * the section's chart (default slot) and the LLM draft text whose numbers are
 * highlighted until the section is approved.
 */
defineProps<{ index: number; name: string; draft: string[]; ok: boolean; active: boolean; locked?: boolean }>()
defineEmits<{ focus: []; approve: []; regen: [] }>()
</script>

<template>
  <div
    :class="cn(
      'mx-[-22px] flex flex-col gap-3.5 rounded-xl border px-[22px] py-5 transition-colors',
      active ? 'border-brand-line' : 'border-line-2',
    )"
    @click="$emit('focus')"
  >
    <div class="flex flex-wrap items-center gap-2.5 max-xl:gap-y-2">
      <span class="yb-num text-[13px] font-semibold text-ink-5">{{ pad(index + 1) }}</span>
      <span class="min-w-0 flex-1 text-lg font-semibold">{{ name }}</span>
      <Badge v-if="ok" variant="ok" class="px-2 py-0.5 text-[11px] font-medium">✓ 已人工审定</Badge>
      <template v-else>
        <Badge variant="warn" class="px-2 py-0.5 text-[11px] font-medium">大模型初稿 · 待审定</Badge>
        <Button variant="dark" class="h-7 max-xl:h-10 rounded-lg border-0 bg-ink-1 px-3 text-xs font-normal text-white hover:brightness-[.92]" :disabled="locked" @click.stop="$emit('approve')">审定本段</Button>
      </template>
      <Button variant="outline" class="h-7 max-xl:h-10 rounded-lg px-2.5 text-xs font-normal text-ink-3" :disabled="locked" :title="ok ? '重新生成后需重新审定' : undefined" @click.stop="$emit('regen')">重新生成</Button>
    </div>
    <slot />
    <div class="text-sm leading-[1.9] text-pretty text-ink-2">
      <span
        v-for="(t, j) in draft"
        :key="j"
        :class="cn(
          'rounded-[3px] px-px',
          j % 2 === 1 ? (ok ? 'font-semibold text-ink-1' : 'bg-brand-soft font-semibold text-brand') : 'font-normal',
        )"
      >{{ t }}</span>
    </div>
  </div>
</template>
