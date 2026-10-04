<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { B5_SEED } from '@/mock/B5'

const data = usePageData('B5', B5_SEED)

type Result = 'ok' | 'diff'
const ck = ref<Record<number, Result>>({})
const own = ref<Record<number, string>>({})
const reason = ref<Record<number, string>>({})
const sent = ref(false)

const total = computed(() => data.value.items.length)
const n = computed(() => Object.keys(ck.value).length)
const nd = computed(() => Object.values(ck.value).filter(x => x === 'diff').length)
const done = computed(() => n.value === total.value)

function mark(i: number, v: Result) {
  ck.value = { ...ck.value, [i]: v }
}

function submit() {
  if (!done.value) {
    say('还有 ' + (total.value - n.value) + ' 项未确认')
    return
  }
  sent.value = true
  sendAction('B5', 'submitVerification', {
    items: data.value.items.map((it, i) => ({
      id: it.id,
      result: ck.value[i],
      ...(ck.value[i] === 'diff' ? { ownValue: own.value[i] ?? '', reason: reason.value[i] ?? '' } : {}),
    })),
  })
  say(nd.value ? '核对意见已提交 · 将进入意见与申诉受理' : '已确认全部数据一致')
}

const btn = 'h-[34px] px-4 font-medium text-ink-3 border-line-4 hover:brightness-[.92]'
</script>

<template>
  <section data-screen-label="B5 意见核对" class="mx-auto flex w-full max-w-[1100px] flex-col gap-4 px-8 pt-6 pb-14">
    <!-- header -->
    <div class="yb-card flex items-center gap-6 px-6 py-5">
      <div class="min-w-0 flex-1">
        <div class="text-xs font-medium text-violet">{{ data.draft }}</div>
        <div class="mt-1 text-[22px] font-semibold">{{ data.title }}</div>
        <div class="text-[13px] text-ink-4">{{ data.subtitle }}</div>
      </div>
      <div class="border-l border-line-2 px-5 text-center">
        <div class="text-xs text-ink-4">剩余</div>
        <div class="yb-num text-[30px] font-semibold text-warn-ink">{{ data.remainingDays }} 天</div>
      </div>
      <div class="w-[180px]">
        <div class="flex justify-between text-xs">
          <span class="text-ink-4">已确认</span>
          <span class="yb-num font-semibold">{{ n }}/{{ total }}</span>
        </div>
        <div class="mt-1.5 h-1.5 rounded-[3px] bg-line-2">
          <div class="h-1.5 rounded-[3px] bg-brand transition-[width]" :style="{ width: (n / total) * 100 + '%' }" />
        </div>
      </div>
    </div>

    <!-- items -->
    <div class="yb-card overflow-hidden">
      <div v-for="(r, i) in data.items" :key="r.id" class="flex flex-col gap-2.5 border-b border-line-3 px-6 py-4">
        <div class="flex items-center gap-4">
          <div class="min-w-0 flex-1">
            <div class="text-xs text-ink-4">{{ r.label }}</div>
            <div class="yb-num text-2xl font-semibold">{{ r.value }}</div>
            <div v-if="r.reference" class="text-[11px] text-ok-ink">✓ {{ r.reference }}</div>
          </div>
          <div class="flex gap-1.5">
            <Button
              variant="outline"
              :class="cn(btn, ck[i] === 'ok' && 'border-ok bg-ok text-white')"
              @click="mark(i, 'ok')"
            >一致</Button>
            <Button
              variant="outline"
              :class="cn(btn, ck[i] === 'diff' && 'border-bad bg-bad text-white')"
              @click="mark(i, 'diff')"
            >有差异</Button>
          </div>
        </div>
        <template v-if="ck[i] === 'diff'">
          <div class="grid grid-cols-[160px_1fr] gap-2.5">
            <Input
              v-model="own[i]"
              placeholder="本院数值"
              class="h-9 rounded-lg border-line-4 px-3 text-[13px] shadow-none md:text-[13px]"
            />
            <Input
              v-model="reason[i]"
              placeholder="差异原因,例如:12 例特例单议未剔除"
              class="h-9 rounded-lg border-line-4 px-3 text-[13px] shadow-none md:text-[13px]"
            />
          </div>
          <span class="w-fit cursor-pointer text-xs text-brand">+ 上传佐证材料</span>
        </template>
      </div>
      <div class="flex items-center gap-3 bg-surface-1 px-6 py-4">
        <span class="flex-1 text-xs text-ink-4">{{ data.footnote }}</span>
        <Button
          v-if="!sent"
          :class="cn('h-[38px] px-5', !done && 'bg-brand-mute')"
          @click="submit"
        >提交核对结果</Button>
        <span v-else class="text-[13px] font-medium text-ok-ink">✓ 已提交 · 可在报告中心查看处理进度</span>
      </div>
    </div>
  </section>
</template>
