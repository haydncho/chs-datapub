<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { runAction, sendAction, usePageData } from '@/api/client'
import { goPage } from '@/app/router'
import { session } from '@/app/session'
import { say } from '@/app/shell'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { B4_SEED, type ReportStatus } from '@/mock/B4'
import ReportPaper from './B4/ReportPaper.vue'
import VersionDiff from './B4/VersionDiff.vue'
import { vPress } from '@/lib/a11y'

const data = usePageData('B4', B4_SEED)

const RPS: Record<ReportStatus, { label: string; variant: 'warn' | 'violet' | 'ok' | 'muted' }> = {
  sign: { label: '待签收', variant: 'warn' },
  check: { label: '核对中', variant: 'violet' },
  signed: { label: '已签收', variant: 'ok' },
  old: { label: '已更正', variant: 'muted' },
}

/** reports the server accepted a sign-off for in this session, keyed by name (the reload shows the same) */
const signed = reactive<Record<string, boolean>>({})
const rsel = ref(0)
const ack = ref(false)
const diffOn = ref(false)
const busy = ref(false)

/** only a 定点医疗机构 identity signs for its own institution (the server refuses everyone else) */
const canSign = computed(() => data.value.canSign ?? session.current?.identity.role === 'hospital')
const signer = computed(() => {
  const s = session.current
  if (s && s.identity.role === 'hospital') return s.user.name + (s.identity.orgName ? ' · ' + s.identity.orgName : '')
  return data.value.signer
})

const reports = computed(() => data.value.reports)
watch(reports, r => {
  if (rsel.value >= r.length) rsel.value = 0
})
const stOf = (i: number): ReportStatus => {
  const r = reports.value[i]!
  return signed[r.name] ? 'signed' : r.status
}

const list = computed(() => reports.value.map((r, i) => ({ r, i, st: stOf(i), on: i === rsel.value })))
const rp = computed(() => reports.value[Math.min(rsel.value, Math.max(0, reports.value.length - 1))])
const st = computed<ReportStatus | null>(() => (rp.value ? stOf(reports.value.indexOf(rp.value)) : null))
const pend = computed(() => reports.value.filter((_, i) => stOf(i) === 'sign').length)
const showDiff = computed(() => st.value === 'old' && diffOn.value)

function select(i: number) {
  rsel.value = i
  ack.value = false
  diffOn.value = false
}

async function doSign() {
  const r = rp.value
  if (!r) return
  if (!canSign.value) {
    say('签收须由机构本院身份完成,医保局账号不能代签')
    return
  }
  if (!ack.value) {
    say('请先勾选确认已阅')
    return
  }
  if (busy.value) return
  busy.value = true
  const res = await runAction('B4', 'signReport', { reportId: r.id, name: r.name, version: r.version })
  busy.value = false
  if (!res.ok) {
    say(res.error || '签收未成功')
    return
  }
  signed[r.name] = true
  ack.value = false
  say('已签收 · 回执已发送至示例市医保局')
}

function doPrint() {
  if (!rp.value) return
  sendAction('B4', 'exportReport', { name: rp.value.name, version: rp.value.version, format: 'pdf' })
  say('正在生成打印版式 · A4 纵向 · 含水印')
  setTimeout(() => window.print(), 300)
}

// ---- 对本报告提意见 ----
const fbOpen = ref(false)
const fbSection = ref('')
const fbText = ref('')
const sections = computed(() => {
  const r = rp.value
  const toc = (r && data.value.contents?.[r.name]?.toc) || data.value.toc
  return ['全文', ...toc.map(t => t.n + '、' + t.label)]
})
function openFeedback() {
  if (!canSign.value) {
    say('意见须由机构本院身份提交')
    return
  }
  fbSection.value = sections.value[0]!
  fbText.value = ''
  fbOpen.value = true
}
async function submitFeedback() {
  const r = rp.value
  if (!r) return
  const text = fbText.value.trim()
  if (text.length < 5) {
    say('请填写意见内容(至少 5 个字)')
    return
  }
  if (busy.value) return
  busy.value = true
  const res = await runAction<{ result?: { id?: string } }>('B4', 'submitFeedback', {
    reportId: r.id, report: r.name, section: fbSection.value, text,
  })
  busy.value = false
  if (!res.ok) {
    say(res.error || '意见未提交成功')
    return
  }
  fbOpen.value = false
  const id = res.data?.result?.id
  say('意见已提交' + (id ? ' · 编号 ' + id : '') + ' · 医保局将在 5 个工作日内答复')
}
</script>

<template>
  <section
    data-screen-label="B4 报告中心"
    :class="cn(
      'grid min-h-[calc(100vh-132px)] grid-cols-1 lg:grid-cols-[minmax(0,1fr)_300px] lg:max-xl:grid-rows-[auto_1fr] xl:grid-cols-[320px_minmax(0,1fr)_320px]',
      rp ? 'max-lg:grid-rows-[auto_auto_1fr]' : 'max-lg:grid-rows-[auto_1fr]',
    )"
  >
    <!-- report list -->
    <aside class="flex min-w-0 flex-col gap-1.5 border-r border-line-1 bg-surface-1 px-3.5 py-5 max-xl:border-r-0 max-xl:border-b max-xl:pb-3.5 lg:max-xl:col-span-2">
      <div class="flex items-baseline justify-between px-2 pb-2">
        <span class="text-lg font-semibold">报告中心</span>
        <span v-if="pend > 0" class="text-xs font-medium text-warn-ink">{{ pend }} 份待签收</span>
      </div>
      <!-- Pad: one horizontally scrolling strip; the faded right edge hints at more reports -->
      <div class="flex flex-col gap-1.5 max-xl:snap-x max-xl:flex-row max-xl:gap-2.5 max-xl:overflow-x-auto max-xl:pr-10 max-xl:pb-1.5 max-xl:[mask-image:linear-gradient(to_right,#000_calc(100%-40px),transparent)]">
      <div v-press
        v-for="x in list"
        :key="x.r.name"
        :class="cn(
          'flex cursor-pointer flex-col gap-1 rounded-[10px] border p-3 hover:bg-white max-xl:min-h-[88px] max-xl:w-[270px] max-xl:shrink-0 max-xl:snap-start max-xl:bg-white',
          x.on ? 'border-brand-line bg-white shadow-[0_1px_3px_rgba(15,23,42,.06)]' : 'border-transparent bg-transparent',
        )"
        @click="select(x.i)"
      >
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-ink-4 max-xl:text-[12px]">{{ x.r.type }}</span>
          <Badge :variant="RPS[x.st].variant" class="border-0 py-px">{{ RPS[x.st].label }}</Badge>
        </div>
        <div class="text-[13px] font-semibold">{{ x.r.name }}</div>
        <div class="text-[11px] text-ink-5 max-xl:text-[12px]">{{ x.r.date }} 发布 · {{ x.r.pages }} · {{ x.r.version }}</div>
      </div>
      </div>
      <div v-if="!list.length" class="px-2 py-8 text-center text-xs text-ink-5 max-xl:py-2 max-xl:text-left">{{ data.noOwnData ? '暂无本院数据' : '暂无已发布的报告' }}</div>
    </aside>

    <!-- preview (print area) -->
    <main data-print-area :class="cn('flex min-w-0 justify-center bg-line-2 px-8 pt-6 pb-14 max-xl:px-4 max-lg:order-3', !rp && 'lg:max-xl:col-span-2')">
      <div v-if="data.noOwnData || !rp" class="mt-10 h-fit w-full max-w-[720px] rounded-md bg-white px-6 py-16 text-center shadow-[0_2px_10px_rgba(15,23,42,.08)]">
        <div class="text-base font-semibold text-ink-2">{{ data.noOwnData ? '暂无本院数据' : '暂无已发布的报告' }}</div>
        <div class="mt-1.5 text-xs text-ink-4">
          {{ !data.noOwnData ? '医保局批准发布后,报告将在此显示。'
            : rp ? '本院数据尚未接入平台,报告正文暂不可预览;签收与意见不受影响。'
            : (data.audience || '本院') + ' 尚无定向发布的报告,医保局发布后将在此显示。' }}
        </div>
      </div>
      <template v-else-if="rp">
        <VersionDiff v-if="showDiff" :name="rp.name" :diff="data.diff" />
        <ReportPaper v-else :report="rp" :data="data" />
      </template>
    </main>

    <!-- state-dependent panel -->
    <aside v-if="rp" class="border-l border-line-1 bg-white px-5 py-[22px] max-lg:order-2 max-lg:border-b max-lg:border-l-0 max-lg:px-6 max-lg:py-5">
      <!-- landscape Pad: panel sticks beside the preview; portrait: compact two-column block above the preview -->
      <div class="flex flex-col gap-4 md:max-lg:grid md:max-lg:grid-cols-2 md:max-lg:items-start lg:max-xl:sticky lg:max-xl:top-[calc(var(--sticky-top)+16px)]">
      <div class="md:max-lg:col-span-2">
        <div class="text-xs text-ink-4">{{ rp.type }} · {{ rp.version }}</div>
        <div class="mt-0.5 text-base font-semibold">{{ rp.name }}</div>
      </div>

      <div v-if="st === 'sign' && canSign" class="flex flex-col gap-3 rounded-xl border border-[#F6DFB8] bg-[#FFFBF4] p-3.5">
        <div class="font-semibold text-[#7A4510]">签收确认</div>
        <div class="text-xs leading-[1.7] text-ink-3">{{ data.signNote }}</div>
        <label class="flex min-h-5 cursor-pointer items-center gap-2 text-xs max-xl:min-h-10">
          <Checkbox
            v-model="ack"
            class="size-4 rounded-[4px] border-[1.5px] border-ink-6 bg-white shadow-none data-[state=checked]:border-brand data-[state=checked]:bg-brand [&_svg]:size-3"
          />本人已阅读全文 · {{ signer }}
        </label>
        <Button :class="cn(!ack && 'bg-brand-mute', 'max-xl:h-11')" :disabled="busy" @click="doSign">{{ busy ? '签收中…' : '签收' }}</Button>
      </div>
      <div v-else-if="st === 'sign'" class="rounded-[10px] bg-surface-1 px-3.5 py-3 text-xs leading-[1.7] text-ink-3">
        待机构签收 · 签收须由定点医疗机构本院身份完成,医保局账号仅可查看。
      </div>

      <div v-if="st === 'signed'" class="rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs text-ok-ink">✓ 已签收{{ rp.signedAt ? ' ' + rp.signedAt : '' }} · 回执已发送 · 意见期内可提出异议</div>

      <div v-if="st === 'check'" class="flex flex-col gap-2 rounded-[10px] bg-violet-soft px-3.5 py-3 text-xs text-violet">
        <span>{{ data.checkNote }}</span>
        <button type="button" class="cursor-pointer font-semibold max-xl:min-h-10" @click="goPage('B5')">进入意见核对 →</button>
      </div>

      <div v-if="st === 'old'" class="rounded-[10px] bg-line-3 px-3.5 py-3 text-xs leading-[1.7] text-ink-3">
        {{ data.correctionNote }}
        <div class="mt-2">
          <button type="button" class="cursor-pointer font-semibold text-brand max-xl:min-h-10" @click="diffOn = !diffOn">{{ diffOn ? '← 返回报告预览' : '查看版本对比 →' }}</button>
        </div>
      </div>

      <div class="flex flex-col gap-2">
        <Button variant="outline" class="font-normal max-xl:h-11" @click="openFeedback">对本报告提意见</Button>
        <Button variant="outline" class="font-normal text-ink-3 max-xl:h-11" @click="doPrint">打印 / 导出 PDF · 带水印</Button>
      </div>

      <div class="border-t border-line-2 pt-3.5 text-xs leading-[1.8] text-ink-4 md:max-lg:col-start-2 md:max-lg:border-t-0 md:max-lg:pt-0">
        查阅记录 · 本院 {{ data.readLog.count }} 人次<br>最近:{{ data.readLog.last }}
      </div>
      </div>
    </aside>

    <Dialog v-model:open="fbOpen">
      <DialogContent
        overlay-class="z-[96] bg-[rgba(11,21,38,.4)]"
        class="z-[96] flex max-h-[calc(100dvh-32px)] w-[min(520px,calc(100%-32px))] flex-col gap-3.5 overflow-y-auto rounded-2xl border-0 bg-white p-6"
      >
        <div>
          <DialogTitle class="text-lg font-semibold">对本报告提意见</DialogTitle>
          <DialogDescription class="mt-0.5 text-xs text-ink-4">{{ rp?.name }} · 提交后进入医保局「意见与申诉」受理</DialogDescription>
        </div>
        <label class="flex flex-col gap-1.5 text-xs text-ink-4">关联章节
          <select
            v-model="fbSection"
            class="h-9 rounded-lg border border-line-1 bg-surface-1 px-2.5 text-[13px] text-ink-1 outline-none focus:border-brand-line max-xl:h-11"
          >
            <option v-for="x in sections" :key="x" :value="x">{{ x }}</option>
          </select>
        </label>
        <label class="flex flex-col gap-1.5 text-xs text-ink-4">意见内容
          <Textarea
            v-model="fbText"
            placeholder="请写明数据项、本院口径或依据(至少 5 个字)"
            maxlength="2000"
            class="h-[120px] min-h-[120px] resize-none rounded-lg border-line-1 bg-surface-1 p-3 text-[13px] text-ink-1 shadow-none [field-sizing:fixed]"
          />
        </label>
        <div class="flex justify-end gap-2">
          <Button variant="outline" class="h-[38px] px-4 font-normal max-xl:h-11" @click="fbOpen = false">取消</Button>
          <Button class="h-[38px] px-5 max-xl:h-11" :disabled="busy" @click="submitFeedback">{{ busy ? '提交中…' : '提交意见' }}</Button>
        </div>
      </DialogContent>
    </Dialog>
  </section>
</template>
