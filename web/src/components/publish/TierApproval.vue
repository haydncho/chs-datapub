<script setup lang="ts">
import type { TierRequest } from '@/api/publish'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import ApprovalActions from './ApprovalActions.vue'

/**
 * 对标档位切换审批（A13 发起）：召集人批准后更新指标档位，驳回须填写意见；审批通过前按原档位发布。
 */
defineProps<{ req: TierRequest; canApprove: boolean; busy?: boolean }>()
const emit = defineEmits<{ approve: [opinion: string]; reject: [opinion: string] }>()

const TIERS = ['匿名分位', '匿名编号', '具名对比与排行']
const DESC = ['机构仅见本院在同级中的分位', '如“三级医院A”,不具名横向对比', '显示机构名称与排名']
</script>

<template>
  <Panel title="对标档位切换审批" sub="展示策略配置发起 · 审批通过前按原档位发布" data-testid="tier-approval">
    <template #head><Tag tone="warning">待召集人审批</Tag></template>
    <div class="grid grid-cols-3 gap-2.5">
      <div
        v-for="(t, i) in TIERS"
        :key="t"
        class="rounded-[10px] border-[1.5px] px-3 py-2.5"
        :class="i === req.toTier ? 'border-dashed border-primary bg-primary-tint' : i === req.currentTier ? 'border-line-strong bg-subtle' : 'border-line'"
      >
        <div class="flex items-center justify-between text-[13px] font-semibold" :class="i === req.toTier ? 'text-primary' : 'text-ink'">
          {{ t }}
          <span v-if="i === req.currentTier" class="text-[11px] font-normal text-ink-muted">当前</span>
          <span v-else-if="i === req.toTier" class="text-[11px] font-normal text-primary">申请切换为</span>
        </div>
        <div class="mt-0.5 text-[11px] text-ink-muted">{{ DESC[i] }}</div>
      </div>
    </div>

    <div class="mt-4 grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-6">
      <div class="grid grid-cols-[64px_1fr] content-start gap-x-2.5 gap-y-2 text-[12px]">
        <span class="text-ink-muted">指标</span><span class="font-semibold text-ink" data-testid="tier-indicator">{{ req.indicator }}</span>
        <span class="text-ink-muted">变更</span><span>{{ req.fromName }} → <b class="text-primary">{{ req.toName }}</b></span>
        <span class="text-ink-muted">理由</span><span class="leading-[1.7] text-ink-body">{{ req.reason }}</span>
        <span class="text-ink-muted">申请人</span><span>{{ req.requestedBy }} · 行政管理组 · {{ req.requestedAt }}</span>
        <span class="text-ink-muted">审批人</span><span>{{ req.approver }}</span>
        <div v-if="req.named" class="col-span-2 rounded-lg border border-warning-line bg-warning-soft px-2.5 py-2 text-warning-ink">
          具名对比与排行将向同级机构显示机构名称与名次,请确认已完成机构核对。
        </div>
      </div>
      <ApprovalActions
        :can-approve="canApprove"
        :busy="busy"
        approve-text="批准切换"
        reject-text="驳回申请"
        :ids="{ opinion: 'tier-opinion', approve: 'tier-approve', reject: 'tier-reject' }"
        :reset-key="req.id"
        role-hint="档位切换由召集人审批。"
        @approve="emit('approve', $event)"
        @reject="emit('reject', $event)"
      >
        <div class="mt-2 text-[11px] leading-[1.6] text-ink-muted">
          批准后「{{ req.indicator }}」在展示策略配置与机构端按“{{ req.toName }}”展示;驳回则保持“{{ req.fromName }}”。
        </div>
      </ApprovalActions>
    </div>
  </Panel>
</template>
