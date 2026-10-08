<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { PageSection } from '@/components/yb'
import { getJson, usePageData, runAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import { B5_SEED, type B5Attachment, type B5Data, type B5Result } from '@/mock/B5'
import { FBS } from './A10/meta'

const data = usePageData('B5', B5_SEED)

async function refresh() {
  try {
    const r = await getJson<B5Data>('/pages/B5')
    if (r && typeof r === 'object') data.value = { ...B5_SEED, ...r }
  } catch { /* keep the current view */ }
}

const MAX_FILES = 5
const MAX_SIZE = 20 * 1024 * 1024

const ck = ref<Record<string, B5Result>>({})
const own = ref<Record<string, string>>({})
const reason = ref<Record<string, string>>({})
const files = ref<Record<string, B5Attachment[]>>({})
/** item ids whose 有差异 fields failed validation (shown inline) */
const invalid = ref<Record<string, boolean>>({})
const busy = ref(false)

/** the stored submission (server) wins over local edits — a reload keeps 已提交 */
const submitted = computed(() => data.value.submission)
const closed = computed(() => !!data.value.round?.closed)
const canSubmit = computed(() => data.value.viewer?.canSubmit ?? true)
/** locked: already submitted, past the deadline, or a 医保局 identity */
const locked = computed(() => !!submitted.value || !canSubmit.value)

const answerOf = (id: string) => submitted.value?.items.find(a => a.id === id)
const resultOf = (id: string): B5Result | undefined => answerOf(id)?.result ?? ck.value[id]

const total = computed(() => data.value.items.length)
const n = computed(() => data.value.items.filter(it => resultOf(it.id)).length)
const nd = computed(() => data.value.items.filter(it => resultOf(it.id) === 'diff').length)
const done = computed(() => n.value === total.value)

function mark(id: string, v: B5Result) {
  if (locked.value) return
  ck.value = { ...ck.value, [id]: v }
  if (v === 'ok') invalid.value = { ...invalid.value, [id]: false }
}

function pickFiles(id: string, e: Event) {
  const input = e.target as HTMLInputElement
  const picked = Array.from(input.files ?? [])
  input.value = ''
  const cur = files.value[id] ?? []
  const next = [...cur]
  for (const f of picked) {
    if (f.size > MAX_SIZE) {
      say(`「${f.name}」超过 20MB,未添加`)
      continue
    }
    if (next.length >= MAX_FILES) {
      say(`每项最多 ${MAX_FILES} 个佐证材料`)
      break
    }
    if (!next.some(x => x.name === f.name)) next.push({ name: f.name.slice(0, 100), size: f.size, type: f.type })
  }
  files.value = { ...files.value, [id]: next }
}
function dropFile(id: string, name: string) {
  files.value = { ...files.value, [id]: (files.value[id] ?? []).filter(f => f.name !== name) }
}
const kb = (b: number) => (b >= 1024 * 1024 ? (b / 1024 / 1024).toFixed(1) + ' MB' : Math.max(1, Math.round(b / 1024)) + ' KB')

async function submit() {
  if (locked.value || busy.value) return
  if (!done.value) {
    say('还有 ' + (total.value - n.value) + ' 项未确认')
    return
  }
  const bad: Record<string, boolean> = {}
  for (const it of data.value.items) {
    if (ck.value[it.id] === 'diff' && (!(own.value[it.id] ?? '').trim() || !(reason.value[it.id] ?? '').trim())) bad[it.id] = true
  }
  invalid.value = bad
  if (Object.keys(bad).length) {
    say('选择「有差异」的项须填写本院数值和差异原因')
    return
  }
  busy.value = true
  const r = await runAction('B5', 'submitVerification', {
    items: data.value.items.map(it => ({
      id: it.id,
      result: ck.value[it.id],
      ...(ck.value[it.id] === 'diff'
        ? { ownValue: own.value[it.id]!.trim(), reason: reason.value[it.id]!.trim(), attachments: files.value[it.id] ?? [] }
        : {}),
    })),
  })
  busy.value = false
  if (!r.ok) {
    say(r.error)
    return
  }
  say(nd.value ? '核对意见已提交 · 将进入意见与申诉受理' : '已确认全部数据一致')
  await refresh()
}

const btn = 'h-[34px] px-4 max-xl:h-10 max-xl:min-w-20 font-medium text-ink-3 border-line-4 hover:brightness-[.92] disabled:opacity-100'
</script>

<template>
  <PageSection label="B5 意见核对" class="max-w-[1100px]">
    <!-- header -->
    <div class="yb-card flex flex-wrap items-center gap-x-6 gap-y-3 px-6 py-5 max-xl:px-card-x">
      <div class="min-w-0 flex-1 max-md:basis-full">
        <div class="text-xs font-medium text-violet">{{ data.draft }}</div>
        <div class="mt-1 text-[22px] font-semibold">{{ data.title }}</div>
        <div class="text-[13px] text-ink-4">{{ data.subtitle }}</div>
      </div>
      <div class="shrink-0 border-l border-line-2 px-5 text-center">
        <div class="text-xs text-ink-4">{{ closed ? '核对' : '剩余' }}</div>
        <div v-if="closed" class="text-[22px] font-semibold text-bad">已截止</div>
        <div v-else class="yb-num text-[30px] font-semibold text-warn-ink">{{ data.remainingDays }} 天</div>
      </div>
      <div class="w-[180px] shrink-0">
        <div class="flex justify-between text-xs">
          <span class="text-ink-4">已确认</span>
          <span class="yb-num font-semibold">{{ n }}/{{ total }}</span>
        </div>
        <div class="mt-1.5 h-1.5 rounded-[3px] bg-line-2">
          <div class="h-1.5 rounded-[3px] bg-brand transition-[width]" :style="{ width: (n / total) * 100 + '%' }" />
        </div>
      </div>
    </div>

    <div v-if="!submitted && data.viewer?.reason" role="status" class="rounded-[10px] border border-warn/30 bg-warn-soft px-4 py-3 text-[13px] text-warn-ink">
      {{ data.viewer.reason }}
    </div>

    <!-- items -->
    <div class="yb-card overflow-hidden">
      <div v-for="r in data.items" :key="r.id" class="flex flex-col gap-2.5 border-b border-line-3 px-6 py-4 max-xl:px-card-x">
        <div class="flex flex-wrap items-center gap-4">
          <div class="min-w-0 flex-1">
            <div class="text-xs text-ink-4">{{ r.label }}</div>
            <div class="yb-num text-2xl font-semibold">{{ r.value }}</div>
            <div v-if="r.reference" class="text-xs text-ok-ink">✓ {{ r.reference }}</div>
          </div>
          <div class="flex gap-1.5" role="group" :aria-label="`${r.label} 核对结果`">
            <Button
              variant="outline"
              :aria-pressed="resultOf(r.id) === 'ok'"
              :disabled="locked"
              :class="cn(btn, resultOf(r.id) === 'ok' && 'border-ok bg-ok text-white')"
              @click="mark(r.id, 'ok')"
            >一致</Button>
            <Button
              variant="outline"
              :aria-pressed="resultOf(r.id) === 'diff'"
              :disabled="locked"
              :class="cn(btn, resultOf(r.id) === 'diff' && 'border-bad bg-bad text-white')"
              @click="mark(r.id, 'diff')"
            >有差异</Button>
          </div>
        </div>

        <!-- stored answer (read-only) -->
        <div v-if="answerOf(r.id)?.result === 'diff'" class="rounded-lg bg-surface-1 px-3 py-2 text-[13px] text-ink-2">
          本院数值 <b class="font-semibold">{{ answerOf(r.id)!.ownValue }}</b> · 原因:{{ answerOf(r.id)!.reason }}
          <span v-for="f in answerOf(r.id)!.attachments ?? []" :key="f.name" class="ml-2 text-xs text-ink-4">📎 {{ f.name }}</span>
        </div>

        <template v-else-if="!submitted && ck[r.id] === 'diff'">
          <div class="grid grid-cols-1 gap-2.5 md:grid-cols-[160px_1fr]">
            <Input
              v-model="own[r.id]"
              placeholder="本院数值(必填)"
              :aria-label="`${r.label} 本院数值`"
              :aria-invalid="invalid[r.id] && !(own[r.id] ?? '').trim() ? 'true' : undefined"
              maxlength="64"
              :disabled="locked"
              class="h-9 rounded-lg border-line-4 px-3 text-[13px] shadow-none max-xl:h-10 md:text-[13px]"
            />
            <Input
              v-model="reason[r.id]"
              placeholder="差异原因(必填),例如:12 例特例单议未剔除"
              :aria-label="`${r.label} 差异原因`"
              :aria-invalid="invalid[r.id] && !(reason[r.id] ?? '').trim() ? 'true' : undefined"
              maxlength="200"
              :disabled="locked"
              class="h-9 rounded-lg border-line-4 px-3 text-[13px] shadow-none max-xl:h-10 md:text-[13px]"
            />
          </div>
          <div v-if="invalid[r.id]" class="text-xs text-bad">选择「有差异」须填写本院数值和差异原因</div>
          <div class="flex flex-wrap items-center gap-2">
            <label
              :class="cn('inline-flex w-fit items-center text-xs text-brand max-xl:min-h-10 focus-within:underline', locked ? 'pointer-events-none opacity-50' : 'cursor-pointer')"
            >
              + 上传佐证材料
              <input type="file" multiple class="sr-only" :disabled="locked" :aria-label="`${r.label} 上传佐证材料`" @change="pickFiles(r.id, $event)">
            </label>
            <span class="text-xs text-ink-4">每项最多 {{ MAX_FILES }} 个、单个 ≤ 20MB;提交时登记文件名与大小,原件由本院留存备查</span>
          </div>
          <div v-if="files[r.id]?.length" class="flex flex-wrap gap-1.5">
            <span v-for="f in files[r.id]" :key="f.name" class="inline-flex items-center gap-1 rounded-lg border border-line-1 px-2 py-1 text-xs text-ink-3 max-xl:py-0 max-xl:pr-0">
              📎 {{ f.name }} <span class="text-ink-4">{{ kb(f.size) }}</span>
              <button type="button" class="ml-1 inline-flex items-center justify-center text-ink-4 hover:text-bad max-xl:size-10 max-xl:text-base" :aria-label="`移除 ${f.name}`" @click="dropFile(r.id, f.name)">×</button>
            </span>
          </div>
        </template>
      </div>
      <div class="flex flex-wrap items-center gap-3 bg-surface-1 px-6 py-4 max-xl:px-card-x">
        <span class="min-w-[220px] flex-1 text-xs text-ink-4">{{ data.footnote }}</span>
        <span v-if="submitted" class="text-[13px] font-medium text-ok-ink">
          ✓ 已提交 {{ submitted.submittedAt }} · {{ submitted.submittedBy }} · 处理进度见下方
        </span>
        <Button
          v-else
          :disabled="locked || busy"
          :class="cn('h-[38px] px-5 max-xl:h-11', !done && 'bg-brand-mute')"
          @click="submit"
        >{{ closed ? '已截止' : '提交核对结果' }}</Button>
      </div>
    </div>

    <!-- 答复回流: this institution's feedback items with the 医保局 reply -->
    <div v-if="data.progress" class="yb-card overflow-hidden">
      <div class="border-b border-line-2 px-6 py-3.5 max-xl:px-card-x">
        <div class="text-[15px] font-semibold">本院意见处理进度</div>
        <div class="text-xs text-ink-4">医保局在「意见与申诉」中的分派与答复同步显示于此</div>
      </div>
      <div v-if="!data.progress.length" class="px-6 py-8 text-center text-sm text-ink-4">本院暂无提交的意见</div>
      <div v-for="p in data.progress" :key="p.id" class="flex flex-col gap-1.5 border-b border-line-3 px-6 py-3.5 last:border-b-0 max-xl:px-card-x">
        <div class="flex flex-wrap items-center gap-2">
          <Badge :variant="FBS[p.status].variant" class="border-0 py-px text-xs">{{ FBS[p.status].label }}</Badge>
          <span class="min-w-0 flex-1 text-sm font-semibold">{{ p.title }}</span>
          <span class="shrink-0 font-mono text-xs text-ink-4">{{ p.id }}</span>
        </div>
        <div class="text-xs text-ink-4">
          {{ p.report }} · 提交 {{ p.track?.submittedAt ?? '—' }}
          <template v-if="p.assignee"> · 承办 {{ p.assignee }}</template>
          <template v-if="p.status !== 'done' && p.dueDate"> · 答复时限 {{ p.dueDate }}</template>
        </div>
        <div v-if="p.track?.reply" class="rounded-lg bg-ok-soft px-3 py-2 text-[13px] leading-[1.7] text-ink-2">
          <span class="text-xs text-ok-ink">医保局答复 · {{ p.track.repliedBy }} · {{ p.track.repliedAt }}</span>
          <div class="whitespace-pre-wrap">{{ p.track.reply }}</div>
        </div>
      </div>
    </div>
  </PageSection>
</template>
