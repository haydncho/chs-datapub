<script setup lang="ts">
import { computed, ref } from 'vue'
import { runAction } from '@/api/client'
import { say } from '@/app/shell'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import type { A10Data, A10Item, A10Template, FeedbackStatus } from '@/mock/A10'
import { FBS, FBT } from './meta'

const props = defineProps<{
  item: A10Item
  status: FeedbackStatus
  templates: A10Template[]
  assignTo: A10Data['assignTo']
  /** seed-only fallback when the item carries no server track */
  track: A10Data['track']
}>()
/** the server accepted an action — the parent re-reads the queue */
const emit = defineEmits<{ changed: [] }>()

const MAX_REPLY = 2000

// reset per item (parent re-keys this component on selection / state change)
const tpl = ref(-1)
const fix = ref(false)
const draft = ref('')
const busy = ref(false)

const closed = computed(() => props.status === 'done')

const thread = computed(() => {
  const f = props.item
  const tr = f.track
  const t: [string, string, string][] = []
  if (tr) {
    t.push(['提交', tr.submittedBy ? `${f.org} · ${tr.submittedBy}` : f.org, tr.submittedAt])
    if (f.assignee) t.push(['分派', tr.assignedBy ? `${f.assignee} · 由${tr.assignedBy}分派` : f.assignee, tr.assignedAt ?? '—'])
    if (tr.repliedAt) t.push(['答复', tr.repliedBy ?? '—', tr.repliedAt])
  } else {
    t.push(['提交', f.org, props.track.submittedAt])
    if (f.assignee) t.push(['分派', `${f.assignee} · 由${props.track.assignedBy}分派`, props.track.assignedAt])
  }
  return t.map((x, i, a) => ({ k: x[0], v: x[1], t: x[2], line: i < a.length - 1 }))
})

function pickTpl(i: number) {
  tpl.value = i
  fix.value = props.templates[i]!.triggersCorrection
  draft.value = props.templates[i]!.text.replace('{title}', props.item.title)
}

async function assign() {
  if (busy.value) return
  const { name, team } = props.assignTo
  busy.value = true
  const r = await runAction('A10', 'assignFeedback', { id: props.item.id, assignee: name, team })
  busy.value = false
  if (!r.ok) {
    say(r.error)
    return
  }
  say(`已分派给 ${name}(${team}),时限 5 个工作日`)
  emit('changed')
}

async function send() {
  if (busy.value) return
  const text = draft.value.trim()
  if (!text) {
    say('请填写答复内容(可先选择模板生成草稿)')
    return
  }
  if (text.length > MAX_REPLY) {
    say(`答复内容不能超过 ${MAX_REPLY} 字`)
    return
  }
  busy.value = true
  const r = await runAction('A10', 'replyFeedback', {
    id: props.item.id,
    template: tpl.value >= 0 ? props.templates[tpl.value]!.label : '',
    text,
    triggerCorrection: fix.value,
  })
  busy.value = false
  if (!r.ok) {
    say(r.error)
    return
  }
  say(fix.value ? '已答复并创建更正任务 → 发布工作流' : '已答复 · 机构端可在处理进度中查看')
  emit('changed')
}
</script>

<template>
  <div class="flex min-w-0 flex-col gap-4 bg-white px-6 pt-[22px] pb-8 max-xl:px-5 max-xl:pt-5">
    <div>
      <div class="flex items-center gap-2">
        <span
          class="rounded px-[7px] py-px text-[11px] font-semibold"
          :style="{ background: FBT[item.type][0], color: FBT[item.type][1] }"
        >{{ item.type }}</span>
        <Badge :variant="FBS[status].variant" class="border-0 py-px font-normal">{{ FBS[status].label }}</Badge>
        <span class="font-mono text-[11px] text-ink-5">{{ item.id }}</span>
      </div>
      <div class="mt-2 text-lg font-semibold">{{ item.title }}</div>
      <div class="text-xs text-ink-4">{{ item.org }} · 承办 {{ item.assignee || '未分派' }}</div>
    </div>

    <div class="grid grid-cols-[64px_1fr] gap-1.5 rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs">
      <span class="text-ink-4">关联报告</span><span class="text-brand">{{ item.report }}</span>
      <span class="text-ink-4">位置</span><span>{{ item.section }}</span>
    </div>

    <div class="text-[13px] leading-[1.75] text-pretty text-ink-2">{{ item.text }}</div>

    <div v-if="item.attachments.length" class="flex flex-wrap gap-1.5">
      <span
        v-for="a in item.attachments"
        :key="a"
        class="rounded-lg border border-line-1 px-2.5 py-[5px] text-xs whitespace-nowrap text-ink-3"
      >📎 {{ a }}</span>
    </div>

    <!-- processing track -->
    <div>
      <div v-for="t in thread" :key="t.k" class="flex gap-2.5">
        <div class="flex w-2.5 flex-col items-center">
          <span class="mt-[5px] size-2 rounded-full bg-brand" />
          <span v-if="t.line" class="w-px flex-1 bg-line-1" />
        </div>
        <div class="flex flex-1 justify-between pb-2.5 text-xs">
          <span><b class="font-semibold">{{ t.k }}</b> <span class="text-ink-3">{{ t.v }}</span></span>
          <span class="yb-num text-ink-5">{{ t.t }}</span>
        </div>
      </div>
    </div>

    <Button v-if="status === 'todo'" class="bg-ink-1 font-medium max-xl:h-11" :disabled="busy" @click="assign">
      分派给 {{ assignTo.name }} · {{ assignTo.team }}
    </Button>

    <div v-if="!closed" class="flex flex-col gap-3 rounded-xl border border-line-1 p-3.5">
      <div class="text-xs text-ink-4">答复模板</div>
      <div class="flex flex-wrap gap-1.5">
        <button type="button"
          v-for="(t, i) in templates"
          :key="t.label"
          :class="cn(
            'cursor-pointer rounded-lg border px-2.5 py-[5px] text-xs whitespace-nowrap max-xl:min-h-10 max-xl:px-3',
            i === tpl ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
          )"
          @click="pickTpl(i)"
        >{{ t.label }}</button>
      </div>
      <Textarea
        v-model="draft"
        :maxlength="MAX_REPLY"
        aria-label="答复内容"
        placeholder="选择模板生成答复草稿,可再编辑;也可直接输入答复"
        class="min-h-24 rounded-lg border-line-4 bg-surface-1 px-3 py-2.5 text-[13px] leading-[1.7] shadow-none md:text-[13px]"
      />
      <div class="-mt-2 text-right text-[11px] text-ink-5">{{ draft.trim().length }} / {{ MAX_REPLY }}</div>
      <div class="flex items-center justify-between gap-3">
        <div>
          <div class="text-[13px] font-medium">触发报告更正</div>
          <div class="text-[11px] text-ink-4">自动创建更正任务进入发布工作流</div>
        </div>
        <Switch v-model="fix" size="lg" aria-label="触发报告更正" />
      </div>
      <Button class="max-xl:h-11" :disabled="busy || !draft.trim()" @click="send">发送答复</Button>
    </div>

    <div v-else class="flex flex-col gap-1.5 rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs text-ok-ink">
      <div class="font-medium">✓ 已答复并闭环 · 机构端可在处理进度中查看<template v-if="item.track?.correction"> · 已创建更正任务</template></div>
      <div v-if="item.track?.reply" class="text-[13px] leading-[1.7] whitespace-pre-wrap text-ink-2">{{ item.track.reply }}</div>
    </div>
  </div>
</template>
