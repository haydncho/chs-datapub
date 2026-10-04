<script setup lang="ts">
import { ref, watch } from 'vue'
import type { IndicatorRequest } from '@/api/publish'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

/**
 * 指标上线审批(A4 提交):召集人批准后指标上线并登记对标档位;驳回须填写意见,退回 A4 由提交人修改后重新提交。
 */
const props = defineProps<{ req: IndicatorRequest; canApprove: boolean; busy?: boolean }>()
const emit = defineEmits<{ approve: [opinion: string]; reject: [opinion: string] }>()

const opinion = ref('')
const missing = ref(false)
watch(() => props.req.id, () => {
  opinion.value = ''
  missing.value = false
})
watch(opinion, (v) => v.trim() && (missing.value = false))

function reject() {
  if (!opinion.value.trim()) {
    missing.value = true
    return
  }
  emit('reject', opinion.value)
}
</script>

<template>
  <Panel title="指标上线审批" sub="A4 指标可视化配置提交 · 批准后上线" data-testid="indicator-approval">
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
      <div>
        <Textarea
          v-model="opinion"
          placeholder="填写审批意见(驳回时必填)"
          class="h-[92px] resize-none text-[13px]"
          :class="{ 'border-danger': missing }"
          data-testid="ind-opinion"
        />
        <div v-if="missing" class="mt-1 text-[12px] text-danger">驳回须填写意见</div>
        <div class="mt-2.5 flex gap-2">
          <Button class="px-[18px]" :disabled="busy" data-testid="ind-approve" @click="emit('approve', opinion)">批准上线</Button>
          <Button
            variant="outline"
            class="border-danger px-[18px] text-danger hover:border-danger hover:bg-danger-soft"
            :disabled="busy"
            data-testid="ind-reject"
            @click="reject"
          >驳回申请</Button>
        </div>
        <div class="mt-2 text-[11px] leading-[1.6] text-ink-muted">
          <template v-if="req.internal">批准后「{{ req.name }}」仅在分析监测区可见。</template>
          <template v-else>批准后「{{ req.name }}」出现在全息图“{{ req.grp }}”图层、机构门户核心指标区,并登记到 A13 对标档位。</template>
          驳回则退回 A4,提交人修改后可重新提交。
        </div>
        <div v-if="!canApprove" class="mt-2 rounded-lg bg-subtle px-2.5 py-2 text-[11px] text-ink-muted">指标上线由召集人审批,服务端校验身份。</div>
      </div>
    </div>
  </Panel>
</template>
