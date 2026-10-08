<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { useA8 } from './store'

const s = useA8()

/** the seeded July correction is the history of task c7 only */
const showSeedCorrection = computed(() => s.cur.id === 'c7')
const origin = computed(() => (s.cur.origin ? s.d.tasks.find(t => t.id === s.cur.origin) : undefined))
const hint = computed(() =>
  !s.posted
    ? '仅已发布(召集人批准后)的任务可发起更正或撤回;本任务当前处于第 ' + s.step + ' 步「' + s.stepName(s.step) + '」。'
    : s.openRevision
      ? '已有进行中的' + (s.openRevision.id.startsWith('CH-') ? '撤回' : '更正') + '任务 ' + s.openRevision.id + ',完成后才能再次发起。'
      : !s.canWork
        ? '当前身份只能查看,更正与撤回由召集人或行政管理组发起。'
        : '发起后生成更正任务,重新经过专家组审核与召集人审批;原版本保留可查。',
)
</script>

<template>
  <template v-if="showSeedCorrection">
    <div class="grid grid-cols-2 gap-3 max-md:grid-cols-1">
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
  </template>

  <div v-if="origin" class="rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs leading-[1.7] text-ink-3">
    本任务是对「{{ origin.name }}」的{{ s.cur.id.startsWith('CH-') ? '撤回' : '更正' }},审批通过后生效。
  </div>

  <div v-if="s.revisions.length" class="mt-3 flex flex-col overflow-hidden rounded-[10px] border border-line-2">
    <div class="bg-surface-1 px-3.5 py-2 text-xs text-ink-4">已发起的更正 / 撤回</div>
    <button
      v-for="r in s.revisions"
      :key="r.id"
      type="button"
      class="flex cursor-pointer items-center justify-between gap-3 border-t border-line-3 px-3.5 py-2.5 text-left text-[13px] hover:bg-surface-1 max-xl:min-h-11"
      @click="s.selectTask(r.id)"
    >
      <span class="min-w-0 truncate">{{ r.name }}</span>
      <span class="shrink-0 text-xs text-ink-4">{{ r.step >= 10 ? '已归档' : r.step + ' · ' + s.stepName(r.step) }} ›</span>
    </button>
  </div>

  <div class="mt-3 rounded-[10px] bg-surface-1 px-3.5 py-2.5 text-xs leading-[1.7] text-ink-3">{{ hint }}</div>
  <template v-if="s.canFix">
    <textarea
      v-model="s.fixReason"
      placeholder="更正 / 撤回原因(选填,写入操作日志)"
      class="mt-3 h-[64px] w-full resize-none rounded-[10px] border border-line-1 bg-surface-1 px-3 py-2.5 text-[13px] outline-none placeholder:text-ink-5 focus:border-brand-line"
    />
    <div class="mt-3 flex gap-2">
      <Button variant="outline" class="h-[34px] px-4 font-normal max-xl:h-11" :disabled="s.busy" @click="s.fixAct('correction')">发起更正</Button>
      <Button variant="outline" class="h-[34px] max-xl:h-11 border-[#F3C5C0] px-4 font-normal text-bad" :disabled="s.busy" @click="s.fixAct('withdraw')">发起撤回</Button>
    </div>
  </template>
</template>
