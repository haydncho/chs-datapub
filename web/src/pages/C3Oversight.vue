<script setup lang="ts">
import { computed, ref } from 'vue'
import { PageHeader, PageSection, StatCard } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import { getJson, usePageData, runAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { C3_SEED, type C3CalendarEvent, type C3Data, type C3Icon } from '@/mock/C3'
import { FBS } from './A10/meta'

const data = usePageData('C3', C3_SEED)

async function refresh() {
  try {
    const r = await getJson<C3Data>('/pages/C3')
    if (r && typeof r === 'object') data.value = { ...C3_SEED, ...r }
  } catch { /* keep the current view */ }
}

const MIN_TEXT = 10
const MAX_TEXT = 500
const open = ref(false)
const topic = ref('')
const text = ref('')
const tried = ref(false)
const busy = ref(false)
const topics = computed(() => data.value.suggestionTopics ?? C3_SEED.suggestionTopics ?? [])
const mine = computed(() => data.value.mySuggestions ?? [])
const len = computed(() => text.value.trim().length)
const topicErr = computed(() => (tried.value && !topic.value ? '请选择建议类别' : ''))
const textErr = computed(() =>
  !tried.value ? '' : len.value < MIN_TEXT ? `建议内容至少 ${MIN_TEXT} 字` : len.value > MAX_TEXT ? `建议内容不能超过 ${MAX_TEXT} 字` : '',
)

async function submit() {
  if (busy.value) return
  tried.value = true
  if (topicErr.value || textErr.value) return
  busy.value = true
  const r = await runAction<{ result?: { id: string } }>('C3', 'submitSuggestion', { topic: topic.value, text: text.value.trim() })
  busy.value = false
  if (!r.ok) {
    say(r.error)
    return
  }
  say('建议已提交 · 将在 15 个工作日内答复')
  open.value = false
  topic.value = ''
  text.value = ''
  tried.value = false
  await refresh()
}

const ICON: Record<C3Icon, string> = {
  coin: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM9 10h6M9 14h6M12 10v7',
  shield: 'M12 3l8 3v6c0 4.5-3.4 8.3-8 9-4.6-.7-8-4.5-8-9V6l8-3zM8.5 12l2.5 2.5 4.5-5',
  doc: 'M6 3h9l5 5v13H6zM14 3v6h6M9 13h7M9 17h5',
  check: 'M20 6L9 17l-5-5',
}

const cellClass = (e: C3CalendarEvent) =>
  !e.done ? 'bg-line-4 text-ink-4' : e.kind === 'monthly' ? 'bg-brand text-white' : 'bg-violet text-white'
</script>

<template>
  <PageSection label="C3 外部监督">
    <PageHeader title="外部监督视图" subtitle="人大代表 · 政协委员 · 社会监督员 · 仅汇总口径,不含任何机构与个人数据">
      <Badge variant="ok" class="px-3 py-1 text-xs">公开层</Badge>
    </PageHeader>

    <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <StatCard
        v-for="k in data.kpis"
        :key="k.label"
        :label="k.label"
        :value="k.value"
        :sub="k.sub"
        :tone="k.tone"
        size="lg"
        :icon="ICON[k.icon]"
      />
    </div>

    <div class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)]">
      <div class="yb-card flex flex-col px-card-x py-card-y">
        <div class="mb-3.5 text-[15px] font-semibold">数据公开日历 · {{ data.year }}</div>
        <div class="grid grid-cols-12 gap-1.5">
          <div v-for="m in data.calendar" :key="m.month" class="flex flex-col items-center gap-1">
            <span class="yb-num text-[11px] text-ink-5">{{ m.month }}</span>
            <span
              v-for="(e, i) in m.events"
              :key="i"
              :class="['flex h-[30px] w-full max-xl:h-10 items-center justify-center rounded-[5px] text-[10px]', cellClass(e)]"
            >{{ e.label }}</span>
          </div>
        </div>
        <div class="flex-1" />
        <div class="mt-4 flex flex-wrap gap-x-3.5 gap-y-1 text-[11px] text-ink-4">
          <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-[2px] bg-brand" />月告知</span>
          <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-[2px] bg-violet" />季度/专题</span>
          <span class="flex items-center gap-[5px]"><span class="size-2.5 rounded-[2px] bg-line-4" />计划中</span>
        </div>
      </div>

      <div class="yb-card px-card-x py-card-y">
        <div class="mb-3 text-[15px] font-semibold">反馈闭环</div>
        <div v-for="r in data.feedback" :key="r.label" class="border-t border-line-3 py-2.5">
          <div class="flex justify-between text-[13px]">
            <span>{{ r.label }}</span>
            <span class="yb-num font-semibold">{{ r.value }}</span>
          </div>
          <div class="mt-1.5 h-1.5 rounded-[3px] bg-line-2">
            <div class="h-1.5 rounded-[3px] bg-ok" :style="{ width: r.pct + '%' }" />
          </div>
        </div>
        <Button v-if="!open" variant="outline" class="mt-3.5 w-full font-normal max-xl:h-11" @click="open = true">提交监督建议</Button>
        <form v-else class="mt-3.5 flex flex-col gap-2.5 rounded-xl border border-line-1 p-3.5" aria-label="提交监督建议" @submit.prevent="submit">
          <div class="text-xs text-ink-4">建议类别</div>
          <div class="flex flex-wrap gap-1.5" role="radiogroup" aria-label="建议类别">
            <button
              v-for="t in topics"
              :key="t"
              type="button"
              role="radio"
              :aria-checked="topic === t"
              :class="cn('rounded-lg border px-2.5 py-[5px] text-xs whitespace-nowrap max-xl:min-h-10',
                         topic === t ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3')"
              @click="topic = t"
            >{{ t }}</button>
          </div>
          <div v-if="topicErr" class="text-xs text-bad">{{ topicErr }}</div>
          <Textarea
            v-model="text"
            :maxlength="MAX_TEXT"
            aria-label="建议内容"
            :aria-invalid="textErr ? 'true' : undefined"
            placeholder="请描述您对数据公开内容、时效或反馈闭环的建议(10–500 字)"
            class="min-h-24 rounded-lg border-line-4 text-[13px] shadow-none md:text-[13px]"
          />
          <div class="flex justify-between text-[11px]">
            <span class="text-bad">{{ textErr }}</span>
            <span class="text-ink-5">{{ len }} / {{ MAX_TEXT }}</span>
          </div>
          <div class="flex gap-2">
            <Button type="submit" class="flex-1 max-xl:h-11" :disabled="busy">提交</Button>
            <Button type="button" variant="outline" class="font-normal max-xl:h-11" :disabled="busy" @click="open = false">取消</Button>
          </div>
        </form>
      </div>
    </div>

    <div v-if="mine.length" class="yb-card px-card-x py-card-y">
      <div class="mb-1 text-[15px] font-semibold">我提交的监督建议</div>
      <div class="mb-2 text-xs text-ink-4">由医保局在意见与申诉中办理 · 15 个工作日内答复</div>
      <div v-for="m in mine" :key="m.id" class="flex flex-col gap-1.5 border-t border-line-3 py-3">
        <div class="flex flex-wrap items-center gap-2">
          <Badge :variant="FBS[m.status].variant" class="border-0 py-px">{{ FBS[m.status].label }}</Badge>
          <span class="text-xs text-ink-4">{{ m.report }} · 提交 {{ m.track?.submittedAt ?? '—' }}</span>
          <span class="ml-auto font-mono text-[11px] text-ink-5">{{ m.id }}</span>
        </div>
        <div class="text-[13px] leading-[1.7] whitespace-pre-wrap text-ink-2">{{ m.text }}</div>
        <div v-if="m.track?.reply" class="rounded-lg bg-ok-soft px-3 py-2 text-[13px] leading-[1.7] text-ink-2">
          <span class="text-xs text-ok-ink">答复 · {{ m.track.repliedBy }} · {{ m.track.repliedAt }}</span>
          <div class="whitespace-pre-wrap">{{ m.track.reply }}</div>
        </div>
      </div>
    </div>
  </PageSection>
</template>
