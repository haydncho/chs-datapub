<script setup lang="ts">
import { ref, watch } from 'vue'
import { exportApi } from '@/api'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { notifyError } from '@/lib/notify'
import Chip from './Chip.vue'

/**
 * 导出审批弹窗（安全规则）：用途 / 有效期 / 下载次数；提交后返回水印编号并写入审计日志。
 * 只能点 × 或「取消」关闭（遮罩点击不关闭）。
 */
const props = defineProps<{ scope: string }>()
const open = defineModel<boolean>('open', { required: true })

const opts = ref<{ content: string; purposes: string[]; validity: string[]; times: string[] } | null>(null)
const purpose = ref('')
const validity = ref('')
const times = ref('')
const wm = ref<string | null>(null)
const busy = ref(false)

watch(open, async (v) => {
  if (!v) return
  wm.value = null
  try {
    opts.value = await exportApi.options(props.scope)
    purpose.value = opts.value.purposes[0]
    validity.value = opts.value.validity[1] ?? opts.value.validity[0]
    times.value = opts.value.times[0]
  } catch (e) {
    notifyError(e)
    open.value = false
  }
})

async function submit() {
  busy.value = true
  try {
    const r = await exportApi.request({ scope: props.scope, purpose: purpose.value, validity: validity.value, times: times.value })
    wm.value = r.watermarkNo
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      class="w-[480px] max-w-[480px] gap-0 rounded-[14px] border-line bg-surface p-0 sm:max-w-[480px]"
      @pointer-down-outside.prevent
      @interact-outside.prevent
    >
      <div class="border-b border-divider px-5 py-4">
        <DialogTitle class="text-[15px] font-semibold text-ink">导出审批申请</DialogTitle>
      </div>
      <template v-if="!wm && opts">
        <div class="grid grid-cols-[64px_1fr] items-center gap-x-2.5 gap-y-3.5 px-5 py-4 text-[12px]">
          <span class="text-ink-muted">导出内容</span><span class="text-ink" data-testid="export-content">{{ opts.content }}</span>
          <span class="text-ink-muted">用途</span>
          <div class="flex flex-wrap gap-1.5"><Chip v-for="p in opts.purposes" :key="p" :on="p === purpose" @click="purpose = p">{{ p }}</Chip></div>
          <span class="text-ink-muted">有效期</span>
          <div class="flex gap-1.5"><Chip v-for="p in opts.validity" :key="p" :on="p === validity" @click="validity = p">{{ p }}</Chip></div>
          <span class="text-ink-muted">下载次数</span>
          <div class="flex gap-1.5"><Chip v-for="p in opts.times" :key="p" :on="p === times" @click="times = p">{{ p }}</Chip></div>
        </div>
        <div class="mx-5 rounded-lg border border-warning-line bg-warning-soft px-3 py-2.5 text-[12px] text-warning-ink">
          导出文件嵌入实名水印与编号,审批通过后在“我的导出”下载。仅提供专网内下载。
        </div>
        <div class="flex justify-end gap-2 px-5 py-4">
          <Button variant="outline" size="sm" @click="open = false">取消</Button>
          <Button size="sm" :disabled="busy" data-testid="export-submit" @click="submit">提交审批</Button>
        </div>
      </template>
      <template v-else-if="wm">
        <div class="px-5 pt-7 pb-5 text-center">
          <div class="text-[15px] font-semibold text-success">✓ 已提交导出审批</div>
          <div class="mt-1.5 text-[12px] text-ink-muted">水印编号 <span class="font-mono text-ink" data-testid="export-wm">{{ wm }}</span> · 审计日志已记录</div>
        </div>
        <div class="flex justify-end px-5 pb-4"><Button variant="outline" size="sm" @click="open = false">关闭</Button></div>
      </template>
      <div v-else class="px-5 py-10 text-center text-[12px] text-ink-faint">加载中…</div>
    </DialogContent>
  </Dialog>
</template>
