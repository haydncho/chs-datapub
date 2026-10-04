<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Coverage, FlowDetail, Scope, ScopeDim } from '@/api/publish'
import Chip from '@/components/shared/Chip.vue'

/**
 * 发布包与定向范围：包内容（「仅内部」项自动排除）+ 五维定向范围选择器。
 * 每次调整即保存并由引擎重算覆盖机构数与名单（实时）。批准发布后范围锁定。
 */
const props = defineProps<{ detail: FlowDetail; coverage?: Coverage; busy?: boolean }>()
const emit = defineEmits<{ change: [scope: Scope] }>()

const DIMS: { key: ScopeDim; label: string }[] = [
  { key: 'tiers', label: '等级' },
  { key: 'districts', label: '县区' },
  { key: 'batch', label: '付费批次' },
  { key: 'alliance', label: '医共体' },
  { key: 'group', label: '收治病组' },
]

const locked = computed(() => props.detail.flow.scopeLocked)
const showAll = ref(false)

function on(dim: ScopeDim, v: string) {
  const cur = props.detail.scope[dim]
  return Array.isArray(cur) ? cur.includes(v) : cur === v
}

function toggle(dim: ScopeDim, v: string) {
  if (locked.value) return
  const s: Scope = { ...props.detail.scope }
  if (dim === 'tiers' || dim === 'districts') {
    const arr = s[dim]
    s[dim] = arr.includes(v) ? arr.filter((x) => x !== v) : [...arr, v]
  } else {
    if (s[dim] === v) return
    s[dim] = v
  }
  emit('change', s)
}
</script>

<template>
  <div class="grid grid-cols-[minmax(0,1fr)_minmax(0,1.45fr)] gap-6">
    <div>
      <div class="mb-2 text-[13px] font-semibold text-ink">发布包内容</div>
      <div class="flex flex-col gap-1.5 text-[12px]" data-testid="pkg-items">
        <div v-for="it in detail.pkg.items" :key="it.name" class="flex justify-between gap-3 rounded-lg border border-line px-2.5 py-2">
          <span class="text-ink"><span class="mr-1 text-success">✓</span>{{ it.name }}</span>
          <span class="text-right text-ink-sub">{{ it.detail }}</span>
        </div>
        <div v-if="detail.pkg.excluded.length" class="rounded-lg bg-chip px-2.5 py-2 text-ink-sub" data-testid="pkg-excluded">
          已自动排除 {{ detail.pkg.excluded.length }} 项“仅内部”指标:{{ detail.pkg.excluded.join('、') }}
        </div>
      </div>
      <div class="mt-2.5 text-[11px] leading-[1.6] text-ink-faint">个人级、病例级指标标记“仅内部”,不进入发布区;整包审批,不单项放行。</div>
    </div>

    <div>
      <div class="mb-2 flex items-baseline justify-between">
        <span class="text-[13px] font-semibold text-ink">定向范围</span>
        <span class="text-[12px] text-ink-sub">
          覆盖 <b class="text-[20px] text-primary tabular-nums" data-testid="coverage-count">{{ coverage ? coverage.count : '—' }}</b> 家机构
        </span>
      </div>
      <div class="grid grid-cols-[72px_1fr] items-center gap-x-2 gap-y-2.5 text-[12px]" :class="{ 'opacity-70': locked }">
        <template v-for="d in DIMS" :key="d.key">
          <span class="text-ink-muted">{{ d.label }}</span>
          <div class="flex flex-wrap gap-1.5" :data-dim="d.key">
            <Chip
              v-for="o in detail.options[d.key]"
              :key="o.value"
              :on="on(d.key, o.value)"
              :disabled="locked || busy"
              :data-opt="o.label"
              @click="toggle(d.key, o.value)"
            >{{ o.label }}</Chip>
          </div>
        </template>
      </div>
      <div v-if="locked" class="mt-2 text-[11px] text-ink-muted">已批准发布,定向范围已锁定;如需调整请发起更正。</div>

      <div class="mt-3 rounded-lg bg-subtle px-3 py-2.5 text-[12px] leading-[1.7] text-ink-body" data-testid="coverage-names">
        <template v-if="!coverage">覆盖机构暂无法计算,请稍后刷新。</template>
        <template v-else-if="!coverage.count"><span class="text-danger">当前条件未覆盖任何机构,请放宽定向范围。</span></template>
        <template v-else-if="!showAll">{{ coverage.preview }}</template>
        <div v-else class="flex flex-wrap gap-x-3 gap-y-1">
          <span v-for="n in coverage.names" :key="n">{{ n }}</span>
        </div>
      </div>
      <div v-if="coverage" class="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-ink-muted">
        <span v-for="t in coverage.byTier" :key="t.tier" :class="{ 'text-ink-ghost': !t.count }">
          {{ t.tier }} <b class="font-semibold tabular-nums" :class="t.count ? 'text-ink-sub' : ''">{{ t.count }}</b>/{{ t.total }}
        </span>
        <button
          v-if="coverage.count > 5"
          type="button"
          class="ml-auto cursor-pointer text-primary"
          data-testid="toggle-names"
          @click="showAll = !showAll"
        >{{ showAll ? '收起名单' : `展开全部 ${coverage.count} 家` }}</button>
      </div>
    </div>
  </div>
</template>
