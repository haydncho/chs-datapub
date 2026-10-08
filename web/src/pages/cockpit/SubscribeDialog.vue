<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { cn } from '@/lib/utils'
import type { CockpitSavedSubscription } from '@/mock/cockpit'
import { useCockpit } from './store'

/**
 * 订阅推送。对话框只编辑草稿:打开时从「已保存的订阅」(服务端)复制一份,取消 / Esc 直接丢弃;
 * 保存交给父组件提交服务端,成功后才关闭并更新全局状态。
 */
const props = defineProps<{ busy?: boolean }>()
const emit = defineEmits<{ save: [sub: Omit<CockpitSavedSubscription, 'updatedAt'>] }>()
const { s, I, data, savedSub } = useCockpit()

const open = computed({ get: () => s.subOn, set: v => { if (!props.busy) s.subOn = v } })
const sub = computed(() => data.value.subscription)

const draft = reactive({ fq: 0, ct: [] as number[], ch: 0, to: [] as number[] })
function reset() {
  const x = savedSub.value
  const o = sub.value
  const idx = (list: string[], v: string | undefined) => Math.max(0, v ? list.indexOf(v) : 0)
  const pick = (list: string[], vs: string[]) => list.map((l, i) => (vs.includes(l) ? i : -1)).filter(i => i >= 0)
  draft.fq = idx(o.frequencies, x?.frequency)
  draft.ch = idx(o.channels, x?.channel)
  draft.ct = x ? pick(o.contents, x.contents) : [0, 1]
  draft.to = x ? pick(I.value.recipients, x.recipients) : [0]
}
watch(() => s.subOn, on => { if (on) reset() }, { immediate: true })

const toggle = (list: number[], i: number) => (list.includes(i) ? list.filter(x => x !== i) : [...list, i].sort((a, b) => a - b))
const groups = computed(() => [
  { title: '推送频率', chips: sub.value.frequencies.map((l, i) => ({ l, on: i === draft.fq, click: () => (draft.fq = i) })) },
  { title: '推送内容', need: '至少选择 1 项推送内容', bad: draft.ct.length === 0, chips: sub.value.contents.map((l, i) => ({ l, on: draft.ct.includes(i), multi: true, click: () => (draft.ct = toggle(draft.ct, i)) })) },
  { title: '渠道', chips: sub.value.channels.map((l, i) => ({ l, on: i === draft.ch, click: () => (draft.ch = i) })) },
  { title: '接收人', need: '至少选择 1 名接收人', bad: draft.to.length === 0, chips: I.value.recipients.map((l, i) => ({ l, on: draft.to.includes(i), multi: true, click: () => (draft.to = toggle(draft.to, i)) })) },
])
const valid = computed(() => draft.ct.length > 0 && draft.to.length > 0)
const payload = (enabled: boolean) => ({
  enabled,
  frequency: sub.value.frequencies[draft.fq] ?? '',
  contents: draft.ct.map(i => sub.value.contents[i]!),
  channel: sub.value.channels[draft.ch] ?? '',
  recipients: draft.to.map(i => I.value.recipients[i]!),
})
function save() { if (valid.value && !props.busy) emit('save', payload(true)) }
function stop() {
  const x = savedSub.value
  if (x && !props.busy) emit('save', { ...x, enabled: false })
}
const status = computed(() => {
  const x = savedSub.value
  return !x ? '当前未开启订阅' : x.enabled ? '当前订阅:已开启' : '当前订阅:已停用'
})

const lines = computed(() => [
  ...(draft.ct.includes(1) ? I.value.kpis.slice(0, 3).map(k => ({ k: k.label, v: k.value + k.unit, c: 'text-ink-1' })) : []),
  ...(draft.ct.includes(2) ? [{ k: '本期告警', v: I.value.alerts.length + ' 条', c: 'text-bad' }] : []),
  ...(draft.ct.includes(3) ? [{ k: '待办', v: I.value.todo, c: 'text-warn-ink' }] : []),
])
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      overlay-class="z-[130] bg-[rgba(3,7,15,.6)]"
      :show-close="false"
      class="z-[131] max-h-[calc(100dvh-32px)] w-[min(860px,calc(100%-32px))] grid-cols-1 gap-0 overflow-y-auto md:grid-cols-[minmax(0,1fr)_300px] xl:w-[min(860px,calc(100%-64px))] rounded-2xl border-0 bg-white p-0 text-ink-1 shadow-[0_24px_64px_rgba(0,0,0,.4)]"
    >
      <div class="flex flex-col gap-[18px] px-[26px] py-6">
        <div>
          <DialogTitle class="text-xl font-semibold">订阅全景图推送</DialogTitle>
          <DialogDescription class="text-xs text-ink-4">按计划生成快照与摘要,推送给指定接收人 · 带水印 · <span data-testid="sub-status">{{ status }}</span></DialogDescription>
        </div>
        <div v-for="g in groups" :key="g.title">
          <div class="mb-2 flex items-baseline gap-2 text-xs text-ink-4">
            {{ g.title }}<span v-if="'bad' in g && g.bad" class="text-bad" role="alert">{{ g.need }}</span>
          </div>
          <div class="flex flex-wrap gap-1.5">
            <button
              v-for="c in g.chips"
              :key="c.l"
              type="button"
              :aria-pressed="c.on"
              :class="cn(
                'cursor-pointer rounded-lg border px-3 py-1.5 text-xs whitespace-nowrap max-xl:min-h-10 max-xl:px-3.5',
                c.on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3',
              )"
              @click="c.click"
            >{{ (c.on && 'multi' in c ? '✓ ' : '') + c.l }}</button>
          </div>
        </div>
        <div class="mt-1 flex items-center justify-end gap-2">
          <Button v-if="savedSub?.enabled" variant="ghost" class="mr-auto h-9 px-3 font-normal text-ink-4 max-xl:h-11" :disabled="busy" @click="stop">停用订阅</Button>
          <Button variant="outline" class="h-9 px-4 font-normal max-xl:h-11" :disabled="busy" @click="open = false">取消</Button>
          <Button class="h-9 px-[18px] max-xl:h-11" :disabled="!valid || busy" data-testid="sub-save" @click="save">{{ busy ? '保存中…' : '保存订阅' }}</Button>
        </div>
      </div>
      <div class="flex flex-col gap-2.5 border-t border-line-1 bg-background p-5 md:border-t-0 md:border-l">
        <div class="text-xs font-semibold text-ink-4">消息预览 · {{ sub.channels[draft.ch] }}</div>
        <div class="overflow-hidden rounded-xl bg-white shadow-[0_1px_3px_rgba(15,23,42,.08)]">
          <div class="flex items-center gap-2 border-b border-line-3 px-3 py-2.5">
            <span class="flex size-[22px] items-center justify-center rounded-md bg-brand text-[11px] font-bold text-white">医</span>
            <span class="text-xs font-semibold">医保数据公开全景图</span>
            <span class="ml-auto text-[11px] whitespace-nowrap text-ink-5">{{ sub.whenLabels[draft.fq] }}</span>
          </div>
          <div v-if="draft.ct.includes(0)" class="relative h-24 overflow-hidden bg-[#040A16]">
            <span class="absolute top-2.5 right-2.5 left-2.5 h-3 rounded-[2px] bg-[rgba(58,160,255,.25)]" />
            <span class="absolute top-7 bottom-2.5 left-2.5 w-[28%] rounded-[3px] bg-[rgba(58,160,255,.14)]" />
            <span class="absolute top-7 right-2.5 bottom-2.5 left-[38%] rounded-[3px] bg-[rgba(58,160,255,.14)]" />
            <span class="absolute top-[46px] left-[62%] size-3.5 rounded-full bg-[#FF6B5E] shadow-[0_0_10px_#FF6B5E]" />
          </div>
          <div class="flex flex-col gap-1.5 px-3 py-2.5 text-xs">
            <div v-for="l in lines" :key="l.k" class="flex justify-between gap-2 whitespace-nowrap">
              <span class="text-ink-4">{{ l.k }}</span>
              <span :class="cn('yb-num font-semibold', l.c)">{{ l.v }}</span>
            </div>
          </div>
          <div class="border-t border-line-3 px-3 py-2 text-xs text-brand">查看完整全景图 ›</div>
        </div>
        <div class="text-[11px] leading-[1.6] text-ink-5">接收人仅能查看其身份可见范围的数据;快照叠加接收人水印。</div>
      </div>
    </DialogContent>
  </Dialog>
</template>
