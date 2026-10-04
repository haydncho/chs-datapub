<script setup lang="ts">
import { computed } from 'vue'
import type { FlowDetail } from '@/api/publish'
import { Button } from '@/components/ui/button'
import ApprovalActions from './ApprovalActions.vue'

/**
 * 审批与留痕：第 5 步「召集人审批」批准（→ 第 6 步定向发布）或驳回至「分析成稿」（驳回意见必填）；
 * 批准之后由经办推进:定向发布 → 签收查阅 → 意见申诉 → 答复整改 → 归档。
 * 批准 / 驳回只限召集人（无权限时按钮置灰；越权调用接口返回 403 并记审计）；第 5 步之前可提交至下一环节。
 */
const props = defineProps<{ detail: FlowDetail; busy?: boolean }>()
const emit = defineEmits<{ approve: [opinion: string]; reject: [opinion: string]; submit: []; advance: [] }>()


const f = computed(() => props.detail.flow)
const atGate = computed(() => !f.value.archived && f.value.step === f.value.gateIdx)
const before = computed(() => !f.value.archived && f.value.step < f.value.gateIdx)
const rejectTo = computed(() => props.detail.steps.find((s) => s.idx === f.value.rejectIdx)?.name ?? '分析成稿')
const count = computed(() => props.detail.coverage?.count)

</script>

<template>
  <div class="grid grid-cols-2 gap-6">
    <div>
      <div class="mb-2 text-[13px] font-semibold text-ink">审批意见</div>
      <template v-if="atGate">
        <div
          v-if="detail.openCheckOpinions && f.kind === '月告知'"
          class="mb-2.5 rounded-lg border border-ai-line bg-ai-soft px-2.5 py-2 text-[12px] text-ai-ink"
        >尚有 {{ detail.openCheckOpinions }} 条核对期异议未答复(意见管理),将影响本期发布包定稿。</div>
        <ApprovalActions
          :can-approve="detail.canApprove"
          :busy="busy"
          approve-text="批准发布"
          :reject-text="`驳回至「${rejectTo}」`"
          :ids="{ opinion: 'approval-opinion', approve: 'approve-btn', reject: 'reject-btn', required: 'opinion-required' }"
          :reset-key="`${detail.flow.id}-${detail.flow.step}`"
          :rows-height="110"
          role-hint="当前身份可提交与调整定向范围;批准与驳回由召集人办理,所有操作全程留痕。"
          @approve="emit('approve', $event)"
          @reject="emit('reject', $event)"
        >
          <div class="mt-2 text-[11px] leading-[1.6] text-ink-muted">
            批准即整包放行,数据由分析监测区进入发布区,按定向范围推送 <b class="text-ink-sub">{{ count ?? '—' }}</b> 家机构。
          </div>
        </ApprovalActions>
      </template>
      <template v-else>
        <div class="rounded-lg bg-subtle px-3 py-3 text-[12px] leading-[1.7] text-ink-body" data-testid="not-gate">
          当前环节:{{ f.stepLabel }}。<template v-if="before">仅第 {{ f.gateIdx }} 步可审批。</template><template v-else-if="f.archived">流程已归档,发布版本见「更正与撤回」。</template><template v-else>已批准发布,经办按环节推进:定向发布 → 签收查阅 → 意见申诉 → 答复整改 → 归档。</template>
        </div>
        <div v-if="detail.advance" class="mt-3 rounded-lg border border-primary-line bg-primary-tint/60 px-3 py-3" data-testid="advance-box">
          <div class="flex items-center gap-2 text-[12px]">
            <span class="font-semibold text-ink">{{ f.stepLabel }}</span>
            <span v-if="detail.advance.total != null" class="ml-auto tabular-nums text-ink-sub" data-testid="sign-stats">
              签收 {{ detail.advance.signed }}/{{ detail.advance.total }}
            </span>
          </div>
          <div v-if="detail.advance.hint" class="mt-1 text-[11px] leading-[1.7] text-ink-body" data-testid="advance-hint">{{ detail.advance.hint }}</div>
          <div v-if="detail.advance.blocked" class="mt-1.5 text-[11px] text-warning" data-testid="advance-blocked">{{ detail.advance.blocked }}</div>
          <Button size="sm" class="mt-2.5" :disabled="busy || !detail.advance.allowed" data-testid="advance-btn" @click="emit('advance')">
            {{ detail.advance.label }}
          </Button>
        </div>
        <Button v-if="before && f.nextStepName" size="sm" variant="outline" class="mt-2.5" :disabled="busy" data-testid="submit-btn" @click="emit('submit')">
          {{ f.step + 1 === f.gateIdx ? '提交召集人审批' : `提交至「${f.nextStepName}」` }}
        </Button>
      </template>
    </div>

    <div>
      <div class="mb-2 text-[13px] font-semibold text-ink">全程留痕</div>
      <ol class="flex flex-col gap-2.5 border-l-2 border-line pl-3.5" data-testid="approval-logs">
        <li v-for="(l, i) in detail.logs" :key="i" class="text-[12px]">
          <div><span class="font-mono text-ink-muted">{{ l.at }}</span> · <span class="font-medium text-ink">{{ l.who }}</span></div>
          <div class="text-ink-body">{{ l.what }}</div>
        </li>
      </ol>
    </div>
  </div>
</template>
