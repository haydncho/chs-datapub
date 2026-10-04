<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { policyApi } from '@/api'
import type { Quadrant, TierRow } from '@/api/types'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'
import { toneSoft, toneVar } from '@/lib/tone'

/**
 * A13 展示策略配置：“谁在看 × 数据归谁”四象限策略；对标三档（匿名分位 / 匿名编号 / 具名对比与排行）。
 * 切换档位弹出审批申请，审批通过前保持原档位（行内显示「审批中:→ 新档位」）。
 */
const page = pageDef('A13')!
const quads = ref<Quadrant[]>([])
const qSel = ref(0)
const tierNames = ref<string[]>([])
const tiers = ref<TierRow[]>([])
const approver = ref('')
const appr = ref<{ indicator: string; from: number; to: number } | null>(null)
const reason = ref('')
const busy = ref(false)

onMounted(async () => {
  try {
    const [q, t] = await Promise.all([policyApi.quadrants(), policyApi.tiers()])
    quads.value = q
    tierNames.value = t.tierNames
    tiers.value = t.rows
    approver.value = t.approver
  } catch (e) {
    notifyError(e)
  }
})

const Q = computed(() => quads.value[qSel.value])
const params = computed(() =>
  Q.value
    ? [
        ['受众范围', Q.value.audience],
        ['粒度上限', Q.value.granularity],
        ['具名规则', Q.value.naming],
        ['抑制阈值', Q.value.threshold],
      ]
    : [],
)

function ask(r: TierRow, to: number) {
  if (to === r.tier || r.pending) return
  appr.value = { indicator: r.indicator, from: r.tier, to }
  reason.value = ''
}

async function submit() {
  if (!appr.value) return
  busy.value = true
  try {
    const row = await policyApi.requestChange(appr.value.indicator, appr.value.to, reason.value)
    tiers.value = tiers.value.map((x) => (x.indicator === row.indicator ? row : x))
    appr.value = null
    notify('已提交召集人审批,审批前保持原档位')
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div class="mt-5 grid grid-cols-2 items-start gap-3.5">
      <Panel title="展示策略四象限">
        <div class="grid grid-cols-[36px_minmax(0,1fr)] gap-2">
          <span />
          <div class="grid grid-cols-2 gap-2 text-center text-[12px] text-ink-muted"><span>数据归属:区域内</span><span>数据归属:区域外</span></div>
          <div class="grid grid-rows-2 gap-2 text-[12px] text-ink-muted">
            <div class="flex items-center justify-center"><span class="whitespace-nowrap [writing-mode:vertical-rl] tracking-[.1em]">查看者 · 区域内</span></div>
            <div class="flex items-center justify-center"><span class="whitespace-nowrap [writing-mode:vertical-rl] tracking-[.1em]">查看者 · 区域外</span></div>
          </div>
          <div class="grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-2" data-testid="quadrants">
            <button
              v-for="(q, i) in quads"
              :key="q.id"
              type="button"
              class="flex cursor-pointer flex-col gap-1.5 rounded-[10px] border-2 p-3.5 text-left"
              :style="{ background: toneSoft[q.tone], borderColor: i === qSel ? toneVar[q.tone] : 'transparent' }"
              @click="qSel = i"
            >
              <div class="text-[13px] font-semibold" :style="{ color: toneVar[q.tone] }">{{ q.title }}</div>
              <div class="text-[12px] text-ink-sub">{{ q.summary }}</div>
            </button>
          </div>
        </div>
        <div class="mt-2 pl-9 text-[11px] text-ink-faint">无权查看的数据不渲染</div>
      </Panel>

      <div class="flex flex-col gap-3.5">
        <Panel v-if="Q" :title="`策略编辑 · ${Q.title}`">
          <div v-for="p in params" :key="p[0]" class="grid grid-cols-[80px_1fr] gap-2.5 border-b border-divider py-2 text-[12px]">
            <span class="text-ink-muted">{{ p[0] }}</span><span class="text-ink">{{ p[1] }}</span>
          </div>
          <div v-if="Q.fixedNone" class="mt-2.5 rounded-lg bg-chip px-2.5 py-2 text-[12px] text-ink-muted">固定策略:不提供,不可编辑。</div>
        </Panel>

        <Panel title="对标三档" data-testid="tiers">
          <template #head><span class="text-[11px] text-ink-muted">切换需召集人审批</span></template>
          <div v-for="r in tiers" :key="r.indicator" class="flex items-center gap-2.5 border-b border-divider py-2 text-[12px]" :data-tier="r.indicator">
            <span class="flex-1 font-medium text-ink">{{ r.indicator }}</span>
            <span v-if="r.pending" class="text-[11px] text-warning">审批中:→ {{ tierNames[r.pending.toTier] }}</span>
            <div class="flex overflow-hidden rounded-lg border border-line" role="radiogroup" :aria-label="`${r.indicator} 对标档位`">
              <button
                v-for="(tn, j) in tierNames"
                :key="tn"
                type="button"
                role="radio"
                :aria-checked="j === r.tier"
                class="cursor-pointer px-2.5 py-[3px] text-[11px] disabled:cursor-not-allowed"
                :class="j === r.tier ? 'bg-primary-solid text-white' : 'bg-surface text-ink-sub hover:bg-hover'"
                :disabled="!!r.pending && j !== r.tier"
                @click="ask(r, j)"
              >{{ tn }}</button>
            </div>
          </div>
        </Panel>
      </div>
    </div>

    <Dialog :open="!!appr" @update:open="(v: boolean) => !v && (appr = null)">
      <DialogContent class="w-[460px] max-w-[460px] gap-0 rounded-[14px] border-line bg-surface p-0 sm:max-w-[460px]" data-testid="tier-dialog">
        <div class="border-b border-divider px-5 py-4"><DialogTitle class="text-[15px] font-semibold text-ink">对标档位切换审批申请</DialogTitle></div>
        <div v-if="appr" class="grid grid-cols-[64px_1fr] items-start gap-3 px-5 py-4 text-[12px]">
          <span class="text-ink-muted">指标</span><span class="font-semibold text-ink">{{ appr.indicator }}</span>
          <span class="text-ink-muted">变更</span><span>{{ tierNames[appr.from] }} → <b class="text-primary">{{ tierNames[appr.to] }}</b></span>
          <span class="pt-2 text-ink-muted">理由</span><Textarea v-model="reason" placeholder="必填:说明调整档位的依据" class="h-16 resize-none text-[12px]" data-testid="tier-reason" />
          <span class="text-ink-muted">审批人</span><span>{{ approver }}</span>
        </div>
        <div v-if="appr?.to === 2" class="mx-5 rounded-lg border border-warning-line bg-warning-soft px-2.5 py-2 text-[12px] text-warning-ink">
          具名对比与排行将向同级机构显示机构名称与名次,请确认已完成机构核对。
        </div>
        <div class="flex justify-end gap-2 px-5 py-4">
          <Button variant="outline" size="sm" @click="appr = null">取消</Button>
          <Button size="sm" :disabled="busy || !reason.trim()" data-testid="tier-submit" @click="submit">提交审批</Button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
