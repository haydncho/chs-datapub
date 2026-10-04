<script setup lang="ts">
import type { AudienceVersion } from '@/api/publish'
import { toneText } from '@/lib/tone'

/** 分受众版本预览：同一发布包按「谁在看 × 数据归谁」生成不同版本，三列并排；无权内容不渲染。 */
defineProps<{ versions: AudienceVersion[] }>()

const lineTone = { default: 'text-ink', danger: 'text-danger', success: 'text-success', muted: 'text-ink-muted' } as const
</script>

<template>
  <div>
    <div class="mb-2.5 text-[12px] text-ink-sub">同一发布包按“谁在看 × 数据归谁”生成不同版本;无权查看的内容不渲染。</div>
    <div class="grid grid-cols-3 gap-3" data-testid="audience-versions">
      <div v-for="v in versions" :key="v.id" class="overflow-hidden rounded-[10px] border border-line" :data-version="v.title">
        <div class="border-b border-line bg-subtle px-3 py-2.5">
          <div class="text-[13px] font-semibold text-ink">{{ v.title }}</div>
          <div class="text-[11px] text-ink-muted">{{ v.subtitle }}</div>
        </div>
        <div class="flex flex-col gap-2 px-3 py-2.5 text-[12px]">
          <div v-for="l in v.lines" :key="l.label" class="flex justify-between gap-3" :class="l.tone === 'muted' ? 'text-ink-muted' : 'text-ink-body'">
            <span>{{ l.label }}</span>
            <span class="text-right tabular-nums" :class="lineTone[l.tone]">{{ l.value }}</span>
          </div>
          <div v-if="v.note" class="text-[11px]" :class="v.noteTone ? toneText[v.noteTone] : 'text-ink-muted'">{{ v.note }}</div>
        </div>
      </div>
    </div>
  </div>
</template>
