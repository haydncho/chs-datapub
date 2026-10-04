<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { FlowDetail } from '@/api/publish'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

/**
 * 审批与留痕：第 5 步「召集人审批」批准（→ 第 6 步定向发布）或驳回至「分析成稿」（驳回意见必填）；
 * 批准之后由经办推进:定向发布 → 签收查阅 → 意见申诉 → 答复整改 → 归档。
 * 批准 / 驳回只限召集人（服务端校验，行政管理组调用返回 403 并记审计）；第 5 步之前可提交至下一环节。
 */
const props = defineProps<{ detail: FlowDetail; busy?: boolean }>()
const emit = defineEmits<{ approve: [opinion: string]; reject: [opinion: string]; submit: []; advance: [] }>()

const opinion = ref('')
const missing = ref(false)
watch(() => [props.detail.flow.id, props.detail.flow.step], () => {
  opinion.value = ''
  missing.value = false
})
watch(opinion, (v) => v.trim() && (missing.value = false))

const f = computed(() => props.detail.flow)
const atGate = computed(() => !f.value.archived && f.value.step === f.value.gateIdx)
const before = computed(() => !f.value.archived && f.value.step < f.value.gateIdx)
const rejectTo = computed(() => props.detail.steps.find((s) => s.idx === f.value.rejectIdx)?.name ?? '分析成稿')
const count = computed(() => props.detail.coverage?.count)

function reject() {
  if (!opinion.value.trim()) {
    missing.value = true
    return
  }
  emit('reject', opinion.value)
}
</script>

<template>
  <div class="grid grid-cols-2 gap-6">
    <div>
      <div class="mb-2 text-[13px] font-semibold text-ink">审批意见</div>
      <template v-if="atGate">
        <div
          v-if="detail.openCheckOpinions && f.kind === '月告知'"
          class="mb-2.5 rounded-lg border border-ai-line bg-ai-soft px-2.5 py-2 text-[12px] text-ai-ink"
        >尚有 {{ detail.openCheckOpinions }} 条核对期异议未答复(A10 意见与申诉),将影响本期发布包定稿。</div>
        <Textarea
          v-model="opinion"
          placeholder="填写审批意见(驳回时必填)"
          class="h-[110px] resize-none text-[13px]"
          :class="{ 'border-danger': missing }"
          :aria-invalid="missing"
          data-testid="approval-opinion"
        />
        <div v-if="missing" class="mt-1 text-[12px] text-danger" data-testid="opinion-required">驳回须填写意见,说明退回修改的原因</div>
        <div class="mt-2.5 flex gap-2">
          <Button class="px-[18px]" :disabled="busy" data-testid="approve-btn" @click="emit('approve', opinion)">批准发布</Button>
          <Button
            variant="outline"
            class="border-danger px-[18px] text-danger hover:border-danger hover:bg-danger-soft"
            :disabled="busy"
            data-testid="reject-btn"
            @click="reject"
          >驳回至「{{ rejectTo }}」</Button>
        </div>
        <div class="mt-2 text-[11px] leading-[1.6] text-ink-muted">
          批准即整包放行,数据由分析监测区进入发布区,按定向范围推送 <b class="text-ink-sub">{{ count ?? '—' }}</b> 家机构。
        </div>
        <div v-if="!detail.canApprove" class="mt-2 rounded-lg bg-subtle px-2.5 py-2 text-[11px] leading-[1.6] text-ink-muted" data-testid="role-hint">
          当前身份可提交与调整定向范围;批准与驳回由召集人办理,服务端校验身份并全程留痕。
        </div>
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
