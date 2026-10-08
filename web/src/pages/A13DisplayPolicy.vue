<script setup lang="ts">
import { computed, reactive } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Switch } from '@/components/ui/switch'
import { usePageData, runAction } from '@/api/client'
import { say } from '@/app/shell'
import { A13_SEED, type A13NumberRule, type A13SwitchRule } from '@/mock/A13'

/** rules as saved on the server (GET /pages/A13 carries the saved values) */
const data = usePageData('A13', A13_SEED)

/** values the server has accepted in this visit, keyed by rule key (on top of the loaded data) */
const edits = reactive<Record<string, number | boolean>>({})
/** rule keys waiting for the server's answer */
const pending = reactive<Record<string, boolean>>({})
const val = (key: string) => edits[key] ?? data.value.rules.find(r => r.key === key)?.value

/** Save one rule; the page only shows the new value once the server has validated and stored it. */
async function save(r: A13NumberRule | A13SwitchRule, value: number | boolean, label: string) {
  if (pending[r.key]) return
  pending[r.key] = true
  const res = await runAction('A13', 'setPolicyRule', { key: r.key, value })
  pending[r.key] = false
  if (!res.ok) {
    say(`未保存:${res.error}`)
    return
  }
  edits[r.key] = value
  say(`已保存 · ${r.name} ${label} · 即时生效并记入审计`)
}

function num(r: A13NumberRule) { return val(r.key) as number }
function bump(r: A13NumberRule, dir: 1 | -1) {
  const next = Math.min(r.max, Math.max(r.min, num(r) + dir * r.step))
  if (next === num(r)) return
  void save(r, next, `${next} ${r.unit}`)
}
function flip(r: A13SwitchRule, v: boolean) {
  void save(r, v, v ? '开启' : '关闭')
}

const minOrg = computed(() => val('minOrg') as number)
const minCase = computed(() => val('minCase') as number)
const pv = computed(() => data.value.preview)
const suppressed = computed(() => pv.value.peerCount < minOrg.value)
</script>

<template>
  <PageSection label="A13 展示策略" class="grid! grid-cols-1 items-start lg:grid-cols-[minmax(0,1fr)_320px] xl:grid-cols-[minmax(0,1fr)_400px]">
    <div class="flex flex-col gap-4">
      <PageHeader title="展示策略" subtitle="全局规则 · 对所有发布物生效 · 召集人与行政管理组可修改,保存即生效并记入审计" />
      <div v-for="r in data.rules" :key="r.key" class="yb-card flex items-center gap-5 px-card-x py-card-y-sm">
        <div class="min-w-0 flex-1">
          <div class="text-sm font-semibold">{{ r.name }}</div>
          <div class="text-xs text-ink-4">{{ r.desc }}</div>
        </div>
        <div v-if="r.kind === 'number'" class="flex items-center gap-2">
          <button
            type="button"
            class="flex size-7 max-xl:size-10 cursor-pointer items-center justify-center rounded-md border border-line-1 bg-white hover:bg-surface-1 disabled:cursor-not-allowed disabled:opacity-40"
            :aria-label="'减少' + r.name"
            :disabled="pending[r.key] || num(r) <= r.min"
            @click="bump(r, -1)"
          >−</button>
          <span class="yb-num min-w-9 text-center text-xl font-semibold">{{ num(r) }}</span>
          <button
            type="button"
            class="flex size-7 max-xl:size-10 cursor-pointer items-center justify-center rounded-md border border-line-1 bg-white hover:bg-surface-1 disabled:cursor-not-allowed disabled:opacity-40"
            :aria-label="'增加' + r.name"
            :disabled="pending[r.key] || num(r) >= r.max"
            @click="bump(r, 1)"
          >+</button>
          <span class="w-6 text-xs text-ink-4">{{ r.unit }}</span>
        </div>
        <Switch
          v-else
          :model-value="val(r.key) as boolean"
          size="lg"
          :aria-label="r.name"
          :disabled="pending[r.key]"
          @update:model-value="(v: boolean) => flip(r, v)"
        />
      </div>
    </div>

    <div class="yb-card lg:sticky lg:top-(--sticky-panel) flex flex-col gap-3 p-card-x">
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
