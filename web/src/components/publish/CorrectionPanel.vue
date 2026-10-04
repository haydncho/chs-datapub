<script setup lang="ts">
import { ref } from 'vue'
import type { FlowDetail, Release } from '@/api/publish'
import type { Tone } from '@/api/types'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'

/**
 * 更正与撤回：原版本保留只读（灰卡「已更正 · 原版保留」）与现行版本并列 + 更正说明。
 * 发起更正 / 撤回会新建「更正与撤回」流程，重新经过专家组审核与召集人审批。
 */
defineProps<{ detail: FlowDetail; busy?: boolean }>()
const emit = defineEmits<{ initiate: [action: '更正' | '撤回', reason: string] }>()

const ST: Record<Release['status'], [string, Tone]> = {
  CURRENT: ['现行版本', 'success'],
  SUPERSEDED: ['已更正 · 原版保留', 'muted'],
  WITHDRAWN: ['已撤回 · 原版保留', 'danger'],
}

const dlg = ref<'更正' | '撤回' | null>(null)
const reason = ref('')
function open(a: '更正' | '撤回') {
  dlg.value = a
  reason.value = ''
}
function submit() {
  if (!dlg.value || !reason.value.trim()) return
  emit('initiate', dlg.value, reason.value.trim())
  dlg.value = null
}
</script>

<template>
  <div data-testid="corrections">
    <div v-if="detail.corrections.releases.length" class="grid grid-cols-2 gap-3">
      <div
        v-for="r in detail.corrections.releases"
        :key="r.version"
        class="rounded-[10px] border px-3.5 py-3.5"
        :class="r.status === 'CURRENT' ? 'border-primary bg-surface' : 'border-line bg-subtle'"
        :data-release="r.version"
      >
        <div class="flex items-start justify-between gap-2">
          <span class="text-[13px] font-semibold" :class="r.status === 'CURRENT' ? 'text-ink' : 'text-ink-muted'">{{ r.title }}</span>
          <Tag :tone="ST[r.status][1]">{{ ST[r.status][0] }}</Tag>
        </div>
        <div class="mt-1.5 text-[12px]" :class="r.status === 'CURRENT' ? 'text-ink-sub' : 'text-ink-muted'">
          {{ r.version > 1 ? '更正发布于' : '发布于' }} {{ r.publishedOn }} · 签收 {{ r.signed }}/{{ r.total }} · {{ r.status === 'CURRENT' ? r.note : '原版本只读可查' }}
        </div>
      </div>
    </div>
    <div v-else class="rounded-lg bg-subtle px-3 py-3 text-[12px] leading-[1.7] text-ink-muted">
      「{{ detail.corrections.subject }}」尚未发布。批准发布后可在此发起更正或撤回;原版本始终保留只读。
    </div>

    <div
      v-if="detail.corrections.explanation"
      class="mt-3 rounded-lg border border-warning-line bg-warning-soft px-3.5 py-3 text-[12px] leading-[1.7] text-ink-body"
      data-testid="correction-note"
    ><b>{{ detail.corrections.action === '撤回' ? '撤回理由' : '更正说明' }}:</b>{{ detail.corrections.explanation }}</div>

    <div class="mt-3 flex items-center gap-2">
      <Button variant="outline" size="sm" :disabled="busy || !detail.corrections.canInitiate" data-testid="init-correct" @click="open('更正')">发起更正</Button>
      <Button
        variant="outline"
        size="sm"
        class="text-danger hover:text-danger"
        :disabled="busy || !detail.corrections.canInitiate"
        data-testid="init-withdraw"
        @click="open('撤回')"
      >发起撤回</Button>
      <span v-if="!detail.corrections.canInitiate && detail.corrections.releases.length" class="text-[11px] text-ink-muted">已有进行中的更正 / 撤回流程,或无现行版本</span>
    </div>

    <Dialog :open="!!dlg" @update:open="(v: boolean) => !v && (dlg = null)">
      <DialogContent class="w-[460px] max-w-[460px] gap-0 rounded-[14px] border-line bg-surface p-0 sm:max-w-[460px]" data-testid="correction-dialog">
        <div class="border-b border-divider px-5 py-4"><DialogTitle class="text-[15px] font-semibold text-ink">发起{{ dlg }}</DialogTitle></div>
        <div class="grid grid-cols-[64px_1fr] items-start gap-3 px-5 py-4 text-[12px]">
          <span class="text-ink-muted">发布物</span><span class="font-semibold text-ink">{{ detail.corrections.subject }}</span>
          <span class="pt-2 text-ink-muted">{{ dlg === '撤回' ? '撤回理由' : '更正说明' }}</span>
          <Textarea v-model="reason" :placeholder="dlg === '撤回' ? '说明撤回原因与受影响机构' : '说明更正内容、原因与受影响机构'" class="h-20 resize-none text-[12px]" data-testid="correction-reason" />
        </div>
        <div class="mx-5 rounded-lg border border-warning-line bg-warning-soft px-2.5 py-2 text-[12px] text-warning-ink">
          将新建「更正与撤回」流程,重新经过专家组审核与召集人审批;原版本保留只读,机构端可查。
        </div>
        <div class="flex justify-end gap-2 px-5 py-4">
          <Button variant="outline" size="sm" @click="dlg = null">取消</Button>
          <Button size="sm" :disabled="!reason.trim()" data-testid="correction-submit" @click="submit">提交</Button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>
