<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { softChip, useA8 } from './store'
import { vPress } from '@/lib/a11y'

const s = useA8()

const passN = computed(() => s.checks.filter(c => c.ok).length)
const allOk = computed(() => s.checks.every(c => c.ok))
const gateTxt = computed(() =>
  s.cur.status === 'withdrawn'
    ? '本发布已撤回。'
    : s.step < 5
      ? (s.cur.status === 'rejected' ? '已被召集人驳回至「' + s.stepName(s.step) + '」,按意见修改后可重新提交审批。' : '当前处于「' + s.stepName(s.step) + '」,完成前序步骤后提交召集人审批。')
      : s.step >= 10
        ? '本发布已归档。'
        : '已于本期批准并定向发布至 ' + s.covN + ' 家机构,签收追踪进行中。',
)
const logs = computed(() =>
  s.logs
    .map((l, i, a) => ({
      ...l,
      c: /驳回/.test(l.tag) ? 'bg-bad' : /批准|发布/.test(l.tag) ? 'bg-ok' : 'bg-brand',
      line: i < a.length - 1,
    }))
    .reverse(),
)
</script>

<template>
  <aside class="flex flex-col gap-[18px] border-l border-line-1 bg-white p-5 max-xl:border-t max-xl:border-l-0 max-xl:px-4">
    <div>
      <div class="mb-2.5 flex items-baseline justify-between">
        <span class="text-sm font-semibold">发布前检查</span>
        <span :class="cn('text-xs font-semibold', allOk ? 'text-ok-ink' : 'text-warn-ink')">{{ passN }} / {{ s.checks.length }} 通过</span>
      </div>
      <div class="flex flex-col gap-1 text-xs">
        <div v-press
          v-for="c in s.checks"
          :key="c.text"
          class="flex cursor-pointer items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-surface-2 max-xl:min-h-11"
          @click="s.tab = c.tab"
        >
          <span
            :class="cn(
              'flex size-[18px] shrink-0 items-center justify-center rounded-full text-[11px]',
              c.ok ? 'bg-ok-soft text-ok' : c.warn ? 'bg-warn-soft text-warn-ink' : 'bg-bad-soft text-bad',
            )"
          >{{ c.ok ? '✓' : c.warn ? '!' : '×' }}</span>
          <span class="min-w-0 flex-1">{{ c.text }}</span>
          <span class="text-ink-6">›</span>
        </div>
      </div>
    </div>

    <div class="border-t border-line-2 pt-[18px]">
      <div class="mb-2.5 text-sm font-semibold">召集人审批</div>
      <template v-if="s.step === 5 && s.isConvener">
        <div class="mb-2 flex flex-wrap gap-1.5">
          <button type="button"
            v-for="p in s.d.phrases"
            :key="p"
            class="cursor-pointer rounded-full bg-surface-3 px-[9px] py-[3px] text-[11px] max-xl:min-h-10 max-xl:px-3.5 max-xl:text-xs whitespace-nowrap text-ink-3 hover:bg-brand-soft hover:text-brand"
            @click="s.addPhrase(p)"
          >{{ p }}</button>
        </div>
        <textarea
          v-model="s.apText"
          placeholder="审批意见(驳回时必填)"
          class="h-[84px] w-full resize-none rounded-[10px] border border-line-1 bg-surface-1 px-3 py-2.5 text-[13px] outline-none placeholder:text-ink-5 focus:border-brand-line"
        />
        <Button class="mt-2.5 h-10 w-full rounded-[10px] text-sm max-xl:h-12" :disabled="s.busy || s.blocked" @click="s.openConfirm()">批准发布 · 推送 {{ s.covN }} 家</Button>
        <div v-if="s.blocked" class="mt-1.5 text-[11px] text-bad-ink">发布前检查未通过,修正后才能批准</div>
        <div class="mt-3 mb-1.5 text-xs text-ink-4">驳回至</div>
        <div class="grid grid-cols-2 gap-1.5">
          <button type="button"
            v-for="i in [1, 2, 3, 4]"
            :key="i"
            :class="cn('cursor-pointer rounded-md border px-2 py-[3px] text-center text-[11px] whitespace-nowrap max-xl:min-h-10 max-xl:text-xs', softChip(i === s.rjTo))"
            @click="s.rjTo = i"
          >{{ i }} {{ s.stepName(i) }}</button>
        </div>
        <Button
          variant="outline"
          class="mt-2 h-9 w-full rounded-[10px] border-[#F3C5C0] max-xl:h-11 font-normal text-bad"
          :disabled="s.busy"
          @click="s.reject()"
        >驳回至「{{ s.stepName(s.rjTo) }}」</Button>
      </template>
      <template v-else-if="s.step === 5">
        <div class="rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs leading-[1.7] text-ink-3">
          等待召集人审批。当前身份({{ s.actorName || '未登录' }})只能查看,审批意见与操作由召集人完成。
        </div>
      </template>
      <template v-else>
        <div class="rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs leading-[1.7] text-ink-3">{{ gateTxt }}</div>
        <div v-if="s.lastReject && s.step < 5" class="mt-2 rounded-[10px] border border-[#F3C5C0] bg-bad-soft px-3.5 py-2.5 text-xs leading-[1.7] text-bad-ink">
          驳回意见 · {{ s.lastReject.who }} {{ s.lastReject.time }}:{{ s.lastReject.what }}
        </div>
        <Button
          v-if="s.step < 5 && !s.archived && s.canWork"
          class="mt-2.5 h-10 w-full rounded-[10px] text-sm max-xl:h-12"
          :disabled="s.busy"
          @click="s.submit()"
        >{{ s.cur.status === 'rejected' ? '重新提交审批' : '提交召集人审批' }}</Button>
      </template>
    </div>

    <div class="min-h-0 flex-1 border-t border-line-2 pt-[18px]">
      <div class="mb-2.5 text-sm font-semibold">操作日志</div>
      <div v-for="(l, i) in logs" :key="i + l.time + l.what" class="flex gap-2.5">
        <div class="flex w-2.5 flex-col items-center">
          <span :class="cn('mt-[5px] size-2 rounded-full', l.c)" />
          <span v-if="l.line" class="w-px flex-1 bg-line-1" />
        </div>
        <div class="min-w-0 pb-3 text-xs">
          <div class="flex flex-wrap items-baseline gap-1.5">
            <b class="font-semibold">{{ l.who }}</b>
            <span class="text-ink-4">{{ l.tag }}</span>
            <span class="yb-num text-ink-5">{{ l.time }}</span>
          </div>
          <div class="text-ink-3">{{ l.what }}</div>
        </div>
      </div>
    </div>
  </aside>
</template>
