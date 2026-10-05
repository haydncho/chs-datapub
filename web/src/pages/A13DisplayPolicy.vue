<script setup lang="ts">
import { computed, reactive } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Switch } from '@/components/ui/switch'
import { usePageData, sendAction } from '@/api/client'
import { A13_SEED, type A13NumberRule, type A13SwitchRule } from '@/mock/A13'

const data = usePageData('A13', A13_SEED)

/** local rule values, keyed by rule key (seeded from the data, overridden by edits) */
const edits = reactive<Record<string, number | boolean>>({})
const val = (key: string) => edits[key] ?? data.value.rules.find(r => r.key === key)?.value

function num(r: A13NumberRule) { return val(r.key) as number }
function bump(r: A13NumberRule, dir: 1 | -1) {
  const next = Math.min(r.max, Math.max(r.min, num(r) + dir * r.step))
  if (next === num(r)) return
  edits[r.key] = next
  sendAction('A13', 'setPolicyRule', { key: r.key, value: next })
}
function flip(r: A13SwitchRule, v: boolean) {
  edits[r.key] = v
  sendAction('A13', 'setPolicyRule', { key: r.key, value: v })
}

const minOrg = computed(() => val('minOrg') as number)
const minCase = computed(() => val('minCase') as number)
const pv = computed(() => data.value.preview)
const suppressed = computed(() => pv.value.peerCount < minOrg.value)
</script>

<template>
  <PageSection label="A13 展示策略" class="grid! grid-cols-1 items-start xl:grid-cols-[minmax(0,1fr)_400px]">
    <div class="flex flex-col gap-4">
      <PageHeader title="展示策略" subtitle="全局规则 · 对所有发布物生效 · 修改需召集人审批" />
      <div v-for="r in data.rules" :key="r.key" class="yb-card flex items-center gap-5 px-card-x py-card-y-sm">
        <div class="min-w-0 flex-1">
          <div class="text-sm font-semibold">{{ r.name }}</div>
          <div class="text-xs text-ink-4">{{ r.desc }}</div>
        </div>
        <div v-if="r.kind === 'number'" class="flex items-center gap-2">
          <button
            type="button"
            class="flex size-7 max-xl:size-10 cursor-pointer items-center justify-center rounded-md border border-line-1 bg-white hover:bg-surface-1"
            :aria-label="'减少' + r.name"
            @click="bump(r, -1)"
          >−</button>
          <span class="yb-num min-w-9 text-center text-xl font-semibold">{{ num(r) }}</span>
          <button
            type="button"
            class="flex size-7 max-xl:size-10 cursor-pointer items-center justify-center rounded-md border border-line-1 bg-white hover:bg-surface-1"
            :aria-label="'增加' + r.name"
            @click="bump(r, 1)"
          >+</button>
          <span class="w-6 text-xs text-ink-4">{{ r.unit }}</span>
        </div>
        <Switch
          v-else
          :model-value="val(r.key) as boolean"
          size="lg"
          :aria-label="r.name"
          @update:model-value="(v: boolean) => flip(r, v)"
        />
      </div>
    </div>

    <div class="yb-card xl:sticky xl:top-(--sticky-panel) flex flex-col gap-3 p-card-x">
      <div class="text-xs font-semibold text-ink-4">{{ pv.title }}</div>
      <div class="relative overflow-hidden rounded-[10px] border border-line-2 p-3.5">
        <div class="text-xs text-ink-4">{{ pv.metric }}</div>
        <div class="yb-num text-[28px] font-semibold text-bad">{{ pv.value }}</div>
        <div v-if="suppressed" class="mt-2 rounded-lg bg-warn-soft px-2.5 py-2 text-xs text-warn-ink">
          同级 {{ pv.peerCount }} 家 &lt; {{ minOrg }} 家 · 分位不输出,仅显示全市均值 {{ pv.cityMean }}
        </div>
        <div v-else class="mt-2 text-xs text-ink-3">同级分位 {{ pv.percentile }}</div>
        <div v-if="val('wm')" class="pointer-events-none absolute inset-0 flex items-center justify-center">
          <span class="-rotate-20 text-xs whitespace-nowrap text-ink-1 opacity-[.12]">{{ pv.watermark }}</span>
        </div>
      </div>
      <div class="text-xs leading-[1.8] text-ink-3">
        病例数 &lt; {{ minCase }} 例的病组并入“其他”<br>导出:{{ val('exp') ? '允许 · 带水印与追溯编码' : '禁止' }}
      </div>
    </div>
  </PageSection>
</template>
