<script setup lang="ts">
import type { IndicatorRequest } from '@/api/publish'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import ApprovalActions from './ApprovalActions.vue'

/**
 * 指标上线审批(A4 提交):召集人批准后指标上线并登记对标档位;驳回须填写意见,退回 A4 由提交人修改后重新提交。
 */
defineProps<{ req: IndicatorRequest; canApprove: boolean; busy?: boolean }>()
const emit = defineEmits<{ approve: [opinion: string]; reject: [opinion: string] }>()

</script>

<template>
  <Panel title="指标上线审批" sub="指标配置提交 · 批准后上线" data-testid="indicator-approval">
    <template #head><Tag tone="warning">待召集人审批</Tag></template>
    <div class="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-6">
      <div class="grid grid-cols-[72px_1fr] content-start gap-x-2.5 gap-y-2 text-[12px]">
        <span class="text-ink-muted">审批单号</span><span class="font-mono text-ink" data-testid="ind-approval-no">{{ req.approvalNo }}</span>
        <span class="text-ink-muted">指标</span><span class="font-semibold text-ink" data-testid="ind-name">{{ req.name }}<span class="ml-1.5 font-mono text-[11px] font-normal text-ink-muted">{{ req.code }}</span></span>
        <span class="text-ink-muted">分组 / 主题域</span><span>{{ req.grp }} · {{ req.domain }}</span>
        <span class="text-ink-muted">可见范围</span><span>{{ req.scope }}</span>
        <span class="text-ink-muted">对标档位</span>
        <span v-if="req.internal">仅内部指标 · 不进入发布包,不设对标档位</span>
        <span v-else>{{ req.tierName }}<span v-if="req.tier !== 0" class="ml-1.5 text-warning">非默认档位,随本审批单一并报批</span></span>
        <span class="text-ink-muted">呈现</span><span>{{ req.template }} · {{ req.unit }}</span>
        <span class="text-ink-muted">申请人</span><span>{{ req.requestedBy }} · 行政管理组 · {{ req.requestedAt }}</span>
        <span class="text-ink-muted">公式</span>
        <pre class="m-0 overflow-x-auto rounded-lg bg-subtle px-2.5 py-2 font-mono text-[11px] leading-[1.7] whitespace-pre-wrap text-ink-body">{{ req.formula }}</pre>
      </div>
      <ApprovalActions
        :can-approve="canApprove"
        :busy="busy"
        approve-text="批准上线"
        reject-text="驳回申请"
        :ids="{ opinion: 'ind-opinion', approve: 'ind-approve', reject: 'ind-reject' }"
        :reset-key="req.id"
        role-hint="指标上线由召集人审批。"
        @approve="emit('approve', $event)"
        @reject="emit('reject', $event)"
      >
        <div class="mt-2 text-[11px] leading-[1.6] text-ink-muted">
          <template v-if="req.internal">批准后「{{ req.name }}」仅在分析监测区可见。</template>
          <template v-else>批准后「{{ req.name }}」正式上线(归入“{{ req.grp }}”),并登记到展示策略配置的对标档位。</template>
          驳回则退回指标配置,提交人修改后可重新提交。
        </div>
      </ApprovalActions>
    </div>
  </Panel>
</template>
