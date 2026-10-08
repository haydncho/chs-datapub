<script setup lang="ts">
import { KpiValue } from '@/components/yb'
import { computed } from 'vue'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useA8 } from './store'

const s = useA8()

const kpis = computed(() => [
  { k: '覆盖机构', v: s.covN + ' 家' },
  { k: '签收期', v: s.d.signPeriod },
  { k: '发布包', v: s.d.packageSize },
])
</script>

<template>
  <Dialog v-model:open="s.confirmOpen">
    <DialogContent
      overlay-class="z-[96] bg-[rgba(11,21,38,.4)]"
      :show-close="false"
      class="z-[96] flex max-h-[calc(100dvh-32px)] w-[min(520px,calc(100%-32px))] flex-col overflow-y-auto gap-4 rounded-2xl border-0 bg-white p-6 shadow-[0_24px_64px_rgba(11,21,38,.3)]"
    >
      <div class="flex items-center gap-3">
        <span class="flex size-10 items-center justify-center rounded-xl bg-brand-soft">
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="var(--brand)" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" /></svg>
        </span>
        <div>
          <DialogTitle class="text-lg font-semibold">确认定向发布</DialogTitle>
          <DialogDescription class="text-xs text-ink-4">发布后机构即可查看,撤回需重新审批</DialogDescription>
        </div>
      </div>
      <div class="grid grid-cols-3 gap-2.5">
        <div v-for="k in kpis" :key="k.k" class="rounded-[10px] bg-surface-1 p-3">
          <div class="text-[11px] text-ink-4">{{ k.k }}</div>
          <KpiValue :value="k.v" size="md" class="block" />
        </div>
      </div>
      <div class="rounded-[10px] border border-[#F6DFB8] bg-[#FFFBF4] px-3 py-2.5 text-xs leading-[1.7] text-ink-3">{{ s.covNames }}</div>
      <div class="flex justify-end gap-2">
        <Button variant="outline" class="h-[38px] px-4 font-normal max-xl:h-11" @click="s.confirmOpen = false">返回检查</Button>
        <Button class="h-[38px] px-5 max-xl:h-11" :disabled="s.busy || s.blocked" @click="s.approve()">{{ s.busy ? '提交中…' : '确认发布' }}</Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
