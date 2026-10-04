<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

/**
 * 审批动作区(三类审批共用):审批意见 + 批准 / 驳回。
 * 驳回必须填写意见;无审批权限时按钮置灰并显示 roleHint;切换审批对象(resetKey 变化)时清空意见。
 * 批准前的二次确认由调用方负责(见 lib/confirm)。
 */
const props = defineProps<{
  canApprove: boolean
  busy?: boolean
  approveText: string
  rejectText: string
  /** 测试钩子:意见框 / 批准 / 驳回 / 缺意见提示 */
  ids: { opinion: string; approve: string; reject: string; required?: string }
  resetKey?: string | number
  rowsHeight?: number
  roleHint?: string
}>()
const emit = defineEmits<{ approve: [opinion: string]; reject: [opinion: string] }>()

const opinion = ref('')
const missing = ref(false)
watch(() => props.resetKey, () => {
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
  <div>
    <Textarea
      v-model="opinion"
      placeholder="填写审批意见(驳回时必填)"
      class="resize-none text-[13px]"
      :style="{ height: `${rowsHeight ?? 92}px` }"
      :class="{ 'border-danger': missing }"
      :aria-invalid="missing"
      :data-testid="ids.opinion"
    />
    <div v-if="missing" class="mt-1 text-[12px] text-danger" :data-testid="ids.required">驳回须填写意见,说明退回修改的原因</div>
    <div class="mt-2.5 flex gap-2">
      <Button class="px-[18px]" :disabled="busy || !canApprove" :data-testid="ids.approve" @click="emit('approve', opinion)">{{ approveText }}</Button>
      <Button
        variant="outline"
        class="border-danger px-[18px] text-danger hover:border-danger hover:bg-danger-soft"
        :disabled="busy || !canApprove"
        :data-testid="ids.reject"
        @click="reject"
      >{{ rejectText }}</Button>
    </div>
    <slot />
    <div v-if="!canApprove && roleHint" class="mt-2 rounded-lg bg-subtle px-2.5 py-2 text-[11px] leading-[1.6] text-ink-muted" data-testid="role-hint">{{ roleHint }}</div>
  </div>
</template>
