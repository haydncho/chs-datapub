<script setup lang="ts">
import { computed } from 'vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { A4Indicator, A4Tier } from '@/mock/A4'
import { GROUP_COLOR, SOURCE_RAW, STATUS, TIER_LABEL } from './meta'

const props = defineProps<{
  ind: A4Indicator
  audiences: string[]
  confirm: A4Tier | null
  pending: A4Tier | null
  busy?: boolean
  packageName: string
}>()
const emit = defineEmits<{ pickTier: [t: A4Tier]; confirmOk: []; confirmNo: [] }>()

const intl = computed(() => props.ind.source === 'internal')
const TIERS: A4Tier[] = ['pct', 'anon', 'named']

/** what each audience sees, by the indicator's current 对标档位 */
const HOSPITAL_VIEW: Record<A4Tier, string> = { pct: '本院+分位', anon: '本院+匿名编号', named: '具名排行', none: '—' }
const vis = computed(() =>
  props.audiences.map((a, i) => {
    // 异地就医 is not split by 二级 / 一级 (no cases there)
    const ok = !intl.value && !(props.ind.name === '异地就医基金支出占比' && i < 4 && i > 1)
    const t = intl.value
      ? '不可见'
      : i === 5 ? '全量'
      : i === 6 ? '汇总'
      : i === 4 ? '本县具名'
      : ok ? HOSPITAL_VIEW[props.ind.tier] : '—'
    return { a, t, ok }
  }),
)
/** own 版本记录, newest first: an open request, the current version, then 上一版 … */
const history = computed(() => {
  const list = props.ind.history ?? []
  const cur = list.findIndex(h => !h.pending)
  return list.map((h, i) => ({ ...h, tag: h.pending ? '审批中 ' : i === cur ? '' : '上一版 ' }))
})
</script>

<template>
  <!-- 桌面右栏为单列;Pad 上作为整宽卡片时,公式 / 档位在左,受众可见性 / 版本在右 -->
  <aside aria-label="指标详情" class="@container border-l border-line-1 bg-white px-[22px] pt-[22px] pb-10 max-xl:mx-5 max-xl:mb-8 max-xl:self-start max-xl:rounded-[var(--radius-card)] max-xl:border max-xl:p-5">
   <div class="grid grid-cols-1 gap-[18px] @xl:grid-cols-2 @xl:gap-x-8">
    <div class="@xl:col-span-full">
      <div class="flex items-center gap-1.5 text-[11px] max-xl:text-xs">
        <span class="flex items-center gap-1 text-ink-3">
          <span class="size-[7px] rounded-full" :style="{ background: GROUP_COLOR[ind.group] }" />{{ ind.group }} · {{ ind.domain }}
        </span>
        <Badge :variant="STATUS[ind.status].variant" class="py-px font-normal">{{ STATUS[ind.status].label }}</Badge>
      </div>
      <div class="mt-1.5 text-xl font-semibold">
        {{ ind.name }} <span class="font-mono text-xs font-normal text-brand">{{ ind.version }}</span>
      </div>
      <div class="text-xs text-ink-4">{{ SOURCE_RAW[ind.source] }} · {{ ind.freq }}更新 · 被 {{ ind.refs }} 份报告引用</div>
      <div v-if="ind.approvalNo" class="mt-1 text-xs text-brand">上线审批 {{ ind.approvalNo }} · 待召集人审批</div>
      <div v-if="ind.inPackage" class="mt-1 text-xs text-ok-ink">✓ 已加入 {{ packageName }}</div>
    </div>

    <div v-if="intl" class="rounded-[10px] bg-line-3 px-3 py-2.5 text-xs text-ink-3 @xl:self-start">
      仅内部指标:病例级 / 个人级数据,不可加入任何发布包,也不可配置对标档位。
    </div>

    <div v-else class="flex min-w-0 flex-col gap-[18px]">
      <div>
        <div class="mb-2 text-xs text-ink-4">公式</div>
        <div class="flex flex-col items-center gap-2 rounded-xl bg-surface-1 p-4">
          <span class="rounded-lg border border-line-1 bg-white px-3 py-1.5 text-center text-[13px]">{{ ind.numerator }}</span>
          <span class="h-[1.5px] w-4/5 bg-ink-1" />
          <span class="rounded-lg border border-line-1 bg-white px-3 py-1.5 text-[13px]">{{ ind.denominator }}</span>
        </div>
      </div>
      <div>
        <div class="mb-2 flex justify-between text-xs">
          <span class="text-ink-4">对标档位</span><span class="text-ink-5">切换需召集人审批</span>
        </div>
        <div class="flex overflow-hidden rounded-lg border border-line-1">
          <button type="button"
            v-for="t in TIERS"
            :key="t"
            :class="cn(
              'flex-1 cursor-pointer px-1 py-[7px] text-center text-xs font-medium max-xl:min-h-10',
              ind.tier === t ? 'bg-ink-1 text-white' : 'bg-white text-ink-3',
            )"
            @click="emit('pickTier', t)"
          >{{ TIER_LABEL[t] }}</button>
        </div>
        <div v-if="confirm" class="mt-2.5 rounded-[10px] border border-[#F6DFB8] bg-[#FFFBF4] p-3 text-xs">
          <div class="text-ink-2">{{ TIER_LABEL[ind.tier] }} → <b>{{ TIER_LABEL[confirm] }}</b>:提交后由召集人审批,通过前按原档位发布。</div>
          <div class="mt-2.5 flex gap-2">
            <Button class="h-[30px] px-3 text-xs font-normal max-xl:h-10" :disabled="busy" @click="emit('confirmOk')">提交审批</Button>
            <Button variant="outline" class="h-[30px] px-3 text-xs font-normal max-xl:h-10" @click="emit('confirmNo')">取消</Button>
          </div>
        </div>
        <div v-if="pending" class="mt-2 text-xs text-warn-ink">审批中:→ {{ TIER_LABEL[pending] }}</div>
      </div>
    </div>

   <div class="flex min-w-0 flex-col gap-[18px]">
    <div>
      <div class="mb-2 text-xs text-ink-4">受众可见性</div>
      <div class="grid grid-cols-2 gap-1.5">
        <div
          v-for="x in vis"
          :key="x.a"
          :class="cn('flex justify-between rounded-lg px-2.5 py-1.5 text-xs', x.ok ? 'bg-ok-soft' : 'bg-surface-2')"
        >
          <span class="text-ink-3">{{ x.a }}</span>
          <span :class="cn('font-medium', x.ok ? 'text-ok-ink' : 'text-ink-5')">{{ x.t }}</span>
        </div>
      </div>
    </div>

    <div>
      <div class="mb-2 text-xs text-ink-4">版本</div>
      <div class="flex flex-col gap-2.5 border-l-2 border-line-2 pl-3 text-xs">
        <div v-for="(h, i) in history" :key="i">
          <div :class="cn('font-medium', h.pending && 'text-warn-ink')">{{ h.tag }}{{ h.version }} · {{ h.date }} · {{ h.author }}</div>
          <div class="text-ink-4">{{ h.note }}</div>
        </div>
        <div v-if="history.length === 0" class="text-ink-5">暂无版本记录</div>
      </div>
    </div>
   </div>
   </div>
  </aside>
</template>
