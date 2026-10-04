<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import type { Tone } from '@/api/types'
import { portalReportsApi, type PortalReport, type PortalReportDetail, type ReportStatus } from '@/api/portalReports'
import ReportPaper from '@/components/portal-reports/ReportPaper.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/** B4 报告中心：按月度 / 专题 / 体检筛选；点击预览（A4 纸样 + 实名水印）；待签收报告可签收（流程 1）；已更正版本保留并加标识。 */
const page = pageDef('B4')!
const router = useRouter()
const TABS = ['全部', '月度报告', '专题报告', '体检报告'] as const
const tab = ref<string>('全部')
const rows = ref<PortalReport[]>([])
const selId = ref<number | null>(null)
const detail = ref<PortalReportDetail | null>(null)
const busy = ref(false)

const ST: Record<ReportStatus, [string, Tone]> = {
  SIGN: ['待签收', 'warning'],
  CHECK: ['核对中', 'ai'],
  SIGNED: ['已签收', 'success'],
  OLD: ['已更正', 'muted'],
  WITHDRAWN: ['已撤回', 'danger'],
}

const shown = computed(() => rows.value.filter((r) => tab.value === '全部' || r.kind === tab.value))

onMounted(async () => {
  try {
    rows.value = await portalReportsApi.reports()
    selId.value = rows.value[0]?.id ?? null
  } catch (e) {
    notifyError(e)
  }
})

watch(selId, async (id) => {
  if (id == null) return
  try {
    const d = await portalReportsApi.report(id)
    if (selId.value === id) detail.value = d
  } catch (e) {
    notifyError(e)
  }
})

const signNote = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.status === 'OLD') return '原版本只读保留'
  if (d.status === 'WITHDRAWN') return '该报告已撤回,仅保留只读,不需签收'
  if (d.status === 'CHECK') return '核对稿:请在意见与核对页完成核对'
  return `✓ 已签收 · ${d.signedAt ?? ''}`
})

async function sign() {
  const d = detail.value
  if (!d) return
  busy.value = true
  try {
    const r = await portalReportsApi.sign(d.id)
    rows.value = rows.value.map((x) => (x.id === r.id ? r : x))
    detail.value = { ...d, ...r }
    notify('已签收,签收记录同步至医保局发布工作流')
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

const toOpinion = () => detail.value && router.push({ name: 'B5', query: { report: String(detail.value.id) } })
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div class="mt-5 grid grid-cols-[400px_minmax(0,1fr)] items-start gap-3">
      <!-- 报告列表 -->
      <section class="rounded-[10px] border border-line bg-surface" data-testid="report-list">
        <div class="flex gap-5 border-b border-divider px-4" role="tablist">
          <button
            v-for="t in TABS"
            :key="t"
            type="button"
            role="tab"
            :aria-selected="tab === t"
            class="-mb-px cursor-pointer border-b-2 px-0.5 py-[11px] text-[13px] transition-colors"
            :class="tab === t ? 'border-primary font-semibold text-primary' : 'border-transparent text-ink-muted hover:text-ink'"
            :data-tab="t"
            @click="tab = t"
          >{{ t }}</button>
        </div>
        <div v-if="!shown.length" class="px-4 py-8 text-center text-[12px] text-ink-faint">暂无报告</div>
        <button
          v-for="r in shown"
          :key="r.id"
          type="button"
          class="block w-full cursor-pointer border-b border-divider px-4 py-3 text-left transition-colors last:rounded-b-[10px] last:border-b-0"
          :class="r.id === selId ? 'bg-primary-tint' : 'hover:bg-hover-soft'"
          :data-report="r.id"
          :data-status="r.status"
          @click="selId = r.id"
        >
          <div class="flex justify-between gap-2">
            <span class="text-[13px] font-medium" :class="r.status === 'OLD' || r.status === 'WITHDRAWN' ? 'text-ink-muted' : 'text-ink'">{{ r.title }}</span>
            <Tag :tone="ST[r.status][1]" class="h-max">{{ ST[r.status][0] }}</Tag>
          </div>
          <div class="mt-0.5 text-[11px] text-ink-faint">{{ r.kind }} · 发布于 {{ r.published }} · {{ r.pages }} 页</div>
        </button>
      </section>

      <!-- 预览 -->
      <section v-if="detail" class="rounded-[10px] border border-line bg-surface" data-testid="report-preview">
        <div class="flex items-center justify-between gap-3 border-b border-divider px-4 py-3">
          <span class="text-[13px] font-semibold text-ink">{{ detail.title }}</span>
          <div class="flex flex-none items-center gap-2">
            <Button v-if="detail.status === 'SIGN'" size="sm" :disabled="busy" data-testid="sign-btn" @click="sign">签收</Button>
            <span v-else class="text-[12px]" :class="detail.status === 'SIGNED' ? 'text-success' : detail.status === 'CHECK' ? 'text-ai' : 'text-ink-muted'" data-testid="sign-note">{{ signNote }}</span>
            <Button size="sm" variant="outline" data-testid="to-opinion" @click="toOpinion">对本报告提意见</Button>
          </div>
        </div>
        <div class="flex justify-center rounded-b-[10px] bg-ink-tint p-5">
          <ReportPaper :report="detail" />
        </div>
      </section>
    </div>
  </div>
</template>
