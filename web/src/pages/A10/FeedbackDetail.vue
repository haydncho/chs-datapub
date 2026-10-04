<script setup lang="ts">
import { computed, ref } from 'vue'
import { sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { cn } from '@/lib/utils'
import type { A10Data, A10Item, A10Template, FeedbackStatus } from '@/mock/A10'
import { FBS, FBT } from './meta'

const props = defineProps<{
  item: A10Item
  status: FeedbackStatus
  templates: A10Template[]
  assignTo: A10Data['assignTo']
  track: A10Data['track']
  /** assigned locally in this session (track shows 刚刚) */
  justAssigned?: boolean
}>()
const emit = defineEmits<{
  status: [id: string, st: FeedbackStatus]
  assign: [id: string, name: string]
}>()

// reset per item (parent re-keys this component on selection)
const tpl = ref(-1)
const fix = ref(false)

const replyTxt = computed(() =>
  tpl.value < 0 ? '' : props.templates[tpl.value]!.text.replace('{title}', props.item.title),
)

const thread = computed(() => {
  const f = props.item
  const t: [string, string, string][] = [['提交', f.org, props.track.submittedAt]]
  if (f.assignee) {
    t.push(props.justAssigned
      ? ['分派', `${f.assignee} · 由王倩分派`, '刚刚']
      : ['分派', `${f.assignee} · 由${props.track.assignedBy}分派`, props.track.assignedAt])
  }
  if (props.status === 'done') t.push(['答复', f.assignee || '王倩', '刚刚'])
  return t.map((x, i, a) => ({ k: x[0], v: x[1], t: x[2], line: i < a.length - 1 }))
})

function pickTpl(i: number) {
  tpl.value = i
  fix.value = props.templates[i]!.triggersCorrection
}

function assign() {
  const { name, team } = props.assignTo
  emit('status', props.item.id, 'doing')
  emit('assign', props.item.id, name)
  sendAction('A10', 'assignFeedback', { id: props.item.id, assignee: name, team })
  say(`已分派给 ${name}(${team}),时限 5 个工作日`)
}

function send() {
  if (tpl.value < 0) {
    say('请先选择答复模板')
    return
  }
  sendAction('A10', 'replyFeedback', {
    id: props.item.id,
    template: props.templates[tpl.value]!.label,
    text: replyTxt.value,
    triggerCorrection: fix.value,
  })
  emit('status', props.item.id, 'done')
  say(fix.value ? '已答复并创建更正任务 → 发布工作流' : '已答复 · 机构端将收到通知')
  tpl.value = -1
}
</script>

<template>
  <div class="flex flex-col gap-4 bg-white px-6 pt-[22px] pb-8">
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

    <div class="text-[13px] leading-[1.75] text-ink-2">{{ item.text }}</div>

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

    <Button v-if="status === 'todo'" class="bg-ink-1 font-medium" @click="assign">
      分派给 {{ assignTo.name }} · {{ assignTo.team }}
    </Button>

    <div v-if="status !== 'done'" class="flex flex-col gap-3 rounded-xl border border-line-1 p-3.5">
      <div class="text-xs text-ink-4">答复模板</div>
      <div class="flex flex-wrap gap-1.5">
        <button type="button"
          v-for="(t, i) in templates"
          :key="t.label"
          :class="cn(
            'cursor-pointer rounded-lg border px-2.5 py-[5px] text-xs whitespace-nowrap',
            i === tpl ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
          )"
          @click="pickTpl(i)"
        >{{ t.label }}</button>
      </div>
      <div v-if="tpl >= 0" class="min-h-16 rounded-lg bg-surface-1 px-3 py-2.5 text-[13px] leading-[1.7]">{{ replyTxt }}</div>
      <div v-else class="min-h-16 rounded-lg border border-dashed border-line-4 px-3 py-2.5 text-xs text-ink-5">选择模板生成答复草稿,可再编辑</div>
      <div class="flex items-center justify-between">
        <div>
          <div class="text-[13px] font-medium">触发报告更正</div>
          <div class="text-[11px] text-ink-4">自动创建更正任务进入发布工作流</div>
        </div>
        <Switch v-model="fix" size="lg" aria-label="触发报告更正" />
      </div>
      <Button @click="send">发送答复</Button>
    </div>

    <div v-else class="rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs text-ok-ink">✓ 已答复并闭环 · 机构端已收到通知</div>
  </div>
</template>
