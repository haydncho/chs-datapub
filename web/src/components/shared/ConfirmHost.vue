<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { confirmState, settleConfirm } from '@/lib/confirm'
</script>

<template>
  <Dialog :open="confirmState.open" @update:open="(v) => !v && settleConfirm(false)">
    <DialogContent class="w-[420px] max-w-[420px] gap-0 rounded-[14px] border-line bg-surface p-0 sm:max-w-[420px]" data-testid="confirm-dialog">
      <div class="px-5 pt-5 pb-3">
        <DialogTitle class="text-[15px] font-semibold text-ink">{{ confirmState.opts.title }}</DialogTitle>
        <DialogDescription class="mt-2 text-[13px] leading-[1.7] text-ink-sub">{{ confirmState.opts.body }}</DialogDescription>
      </div>
      <div class="flex justify-end gap-2.5 border-t border-divider px-5 py-3">
        <Button variant="outline" data-testid="confirm-cancel" @click="settleConfirm(false)">取消</Button>
        <Button
          :class="confirmState.opts.danger ? 'bg-danger-solid text-white hover:bg-danger-solid-hover' : ''"
          data-testid="confirm-ok"
          @click="settleConfirm(true)"
        >{{ confirmState.opts.okText ?? '确认' }}</Button>
      </div>
    </DialogContent>
  </Dialog>
</template>
