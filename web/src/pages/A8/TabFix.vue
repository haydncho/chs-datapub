<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { useA8 } from './store'

const s = useA8()
</script>

<template>
  <div class="grid grid-cols-2 gap-3">
    <div class="rounded-xl border border-line-1 bg-surface-1 p-3.5">
      <div class="flex justify-between gap-2">
        <span class="font-semibold whitespace-nowrap text-ink-4">{{ s.d.correction.oldTitle }}</span>
        <span class="rounded-full bg-line-3 px-2 py-px text-[11px] whitespace-nowrap text-ink-4">{{ s.d.correction.oldTag }}</span>
      </div>
      <div class="mt-1.5 text-xs text-ink-5">{{ s.d.correction.oldMeta }}</div>
    </div>
    <div class="rounded-xl border border-brand-line p-3.5">
      <div class="flex justify-between gap-2">
        <span class="font-semibold whitespace-nowrap">{{ s.d.correction.newTitle }}</span>
        <span class="rounded-full bg-ok-soft px-2 py-px text-[11px] whitespace-nowrap text-ok-ink">{{ s.d.correction.newTag }}</span>
      </div>
      <div class="mt-1.5 text-xs text-ink-3">{{ s.d.correction.newMeta }}</div>
    </div>
  </div>
  <div class="mt-3 rounded-[10px] border border-[#F6DFB8] bg-[#FFFBF4] px-3.5 py-3 text-xs leading-[1.7] text-ink-3">
    <b>更正说明</b> · {{ s.d.correction.note }}
  </div>
  <div class="mt-3 flex gap-2">
    <Button variant="outline" class="h-[34px] px-4 font-normal" @click="s.fixAct('correction')">发起更正</Button>
    <Button variant="outline" class="h-[34px] border-[#F3C5C0] px-4 font-normal text-bad" @click="s.fixAct('withdraw')">发起撤回</Button>
  </div>
</template>
