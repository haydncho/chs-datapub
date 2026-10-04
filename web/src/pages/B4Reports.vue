<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { sendAction, usePageData } from '@/api/client'
import { goPage } from '@/app/router'
import { say } from '@/app/shell'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
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

/** reports signed in this session, keyed by index */
const signed = reactive<Record<number, boolean>>({})
const rsel = ref(0)
const ack = ref(false)
const diffOn = ref(false)

const stOf = (i: number): ReportStatus => (signed[i] ? 'signed' : data.value.reports[i]!.status)

const list = computed(() => data.value.reports.map((r, i) => ({ r, i, st: stOf(i), on: i === rsel.value })))
const rp = computed(() => data.value.reports[rsel.value]!)
const st = computed(() => stOf(rsel.value))
const pend = computed(() => data.value.reports.filter((_, i) => stOf(i) === 'sign').length)
const showDiff = computed(() => st.value === 'old' && diffOn.value)

function select(i: number) {
  rsel.value = i
  ack.value = false
}

function doSign() {
  if (!ack.value) {
    say('请先勾选确认已阅')
    return
  }
  signed[rsel.value] = true
  ack.value = false
  sendAction('B4', 'signReport', { name: rp.value.name, version: rp.value.version, signer: data.value.signer })
  say('已签收 · 回执已发送至示例市医保局')
}

function doPrint() {
  sendAction('B4', 'exportReport', { name: rp.value.name, version: rp.value.version, format: 'pdf' })
  say('正在生成打印版式 · A4 纵向 · 含水印')
  setTimeout(() => window.print(), 300)
}
</script>

<template>
  <section data-screen-label="B4 报告中心" class="grid min-h-[calc(100vh-132px)] grid-cols-[320px_minmax(0,1fr)_320px]">
    <!-- report list -->
    <aside class="flex flex-col gap-1.5 border-r border-line-1 bg-surface-1 px-3.5 py-5">
      <div class="flex items-baseline justify-between px-2 pb-2">
        <span class="text-lg font-semibold">报告中心</span>
        <span v-if="pend > 0" class="text-xs font-medium text-warn-ink">{{ pend }} 份待签收</span>
      </div>
      <div v-press
        v-for="x in list"
        :key="x.r.name"
        :class="cn(
          'flex cursor-pointer flex-col gap-1 rounded-[10px] border p-3 hover:bg-white',
          x.on ? 'border-brand-line bg-white shadow-[0_1px_3px_rgba(15,23,42,.06)]' : 'border-transparent bg-transparent',
        )"
        @click="select(x.i)"
      >
        <div class="flex items-center justify-between">
          <span class="text-[11px] text-ink-4">{{ x.r.type }}</span>
          <Badge :variant="RPS[x.st].variant" class="border-0 py-px">{{ RPS[x.st].label }}</Badge>
        </div>
        <div class="text-[13px] font-semibold">{{ x.r.name }}</div>
        <div class="text-[11px] text-ink-5">{{ x.r.date }} 发布 · {{ x.r.pages }} · {{ x.r.version }}</div>
      </div>
    </aside>

    <!-- preview (print area) -->
    <main data-print-area class="flex min-w-0 justify-center bg-line-2 px-8 pt-6 pb-14">
      <VersionDiff v-if="showDiff" :name="rp.name" :diff="data.diff" />
      <ReportPaper v-else :report="rp" :data="data" />
    </main>

    <!-- state-dependent panel -->
    <aside class="flex flex-col gap-4 border-l border-line-1 bg-white px-5 py-[22px]">
      <div>
        <div class="text-xs text-ink-4">{{ rp.type }} · {{ rp.version }}</div>
        <div class="mt-0.5 text-base font-semibold">{{ rp.name }}</div>
      </div>

      <div v-if="st === 'sign'" class="flex flex-col gap-3 rounded-xl border border-[#F6DFB8] bg-[#FFFBF4] p-3.5">
        <div class="font-semibold text-[#7A4510]">签收确认</div>
        <div class="text-xs leading-[1.7] text-ink-3">{{ data.signNote }}</div>
        <label class="flex cursor-pointer items-center gap-2 text-xs">
          <Checkbox
            v-model="ack"
            class="size-4 rounded-[4px] border-[1.5px] border-ink-6 bg-white shadow-none data-[state=checked]:border-brand data-[state=checked]:bg-brand [&_svg]:size-3"
          />本人已阅读全文 · {{ data.signer }}
        </label>
        <Button :class="cn(!ack && 'bg-brand-mute')" @click="doSign">签收</Button>
      </div>

      <div v-if="st === 'signed'" class="rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs text-ok-ink">✓ 已签收 · 回执已发送 · 意见期内可提出异议</div>

      <div v-if="st === 'check'" class="flex flex-col gap-2 rounded-[10px] bg-violet-soft px-3.5 py-3 text-xs text-violet">
        <span>{{ data.checkNote }}</span>
        <button type="button" class="cursor-pointer font-semibold" @click="goPage('B5')">进入意见核对 →</button>
      </div>

      <div v-if="st === 'old'" class="rounded-[10px] bg-line-3 px-3.5 py-3 text-xs leading-[1.7] text-ink-3">
        {{ data.correctionNote }}
        <div class="mt-2">
          <button type="button" class="cursor-pointer font-semibold text-brand" @click="diffOn = !diffOn">{{ diffOn ? '← 返回报告预览' : '查看版本对比 →' }}</button>
        </div>
      </div>

      <div class="flex flex-col gap-2">
        <Button variant="outline" class="font-normal" @click="say('已打开意见表单 · 将关联到当前章节')">对本报告提意见</Button>
        <Button variant="outline" class="font-normal text-ink-3" @click="doPrint">打印 / 导出 PDF · 带水印</Button>
      </div>

      <div class="border-t border-line-2 pt-3.5 text-xs leading-[1.8] text-ink-4">
        查阅记录 · 本院 {{ data.readLog.count }} 人次<br>最近:{{ data.readLog.last }}
      </div>
    </aside>
  </section>
</template>
