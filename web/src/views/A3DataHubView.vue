<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { collectionApi } from '@/api'
import type { CollectionOverview, LineageStep, SourceDetail, Tone } from '@/api/types'
import Chip from '@/components/shared/Chip.vue'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/**
 * A3 数据归集中心：六步流水线、十类数据源、依赖指标、质量报告、血缘查询。
 * 流程 1：数据到达 → 依赖指标恢复 → 完成质量校验 → 生成月度报告（A5）。
 */
const page = pageDef('A3')!
const router = useRouter()

const ov = ref<CollectionOverview | null>(null)
const error = ref('')
const selId = ref<number | null>(null)
const detail = ref<SourceDetail | null>(null)
const lin = ref('例均基金差额')
const lineage = ref<LineageStep[]>([])
const busy = ref(false)

async function load() {
  try {
    ov.value = await collectionApi.overview()
    error.value = ''
    if (selId.value == null) selId.value = ov.value.sources.find((s) => s.status === 'LATE')?.id ?? ov.value.sources[0]?.id ?? null
    await Promise.all([loadDetail(), loadLineage()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function loadDetail() {
  if (selId.value != null) detail.value = await collectionApi.source(selId.value)
}

async function loadLineage() {
  lineage.value = await collectionApi.lineage(lin.value)
}

onMounted(load)
watch(selId, () => loadDetail().catch(notifyError))
watch(lin, () => loadLineage().catch(notifyError))

const lateSource = computed(() => ov.value?.sources.find((s) => s.status === 'LATE'))

async function arrive() {
  if (!lateSource.value) return
  busy.value = true
  try {
    const r = await collectionApi.arrival(lateSource.value.id)
    selId.value = lateSource.value.id
    notify(r.message)
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

async function qualityCheck() {
  busy.value = true
  try {
    notify((await collectionApi.qualityCheck()).message)
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

const stepStyle: Record<string, { label: string; cls: string; fg: string }> = {
  done: { label: '完成', cls: 'border-success-line bg-success-soft', fg: 'text-success' },
  run: { label: '进行中', cls: 'border-primary-line bg-primary-tint', fg: 'text-primary' },
  wait: { label: '等待', cls: 'border-line bg-subtle', fg: 'text-ink-faint' },
}

const statusTone = (s: string): Tone => (s === 'LATE' ? 'danger' : s === 'PART' ? 'warning' : 'success')
const arrived = computed(() => (ov.value?.sources ?? []).filter((s) => s.status === 'OK').length)
const scoreSpark = computed(() => (ov.value?.sources ?? []).map((s) => s.score ?? 0))
const timeliness = computed(() => ov.value?.quality.timelinessPct)
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" :title="ov ? `数据归集中心 · ${ov.title.split(' ')[0]}` : page.label">
      <template #actions>
        <Button v-if="lateSource" variant="outline" class="px-4 py-[7px]" :disabled="busy" data-testid="arrive-btn" @click="arrive">
          登记到数 · {{ lateSource.name }}
        </Button>
        <Button v-if="ov?.allArrived && !ov.qcDone" class="px-4 py-[7px]" :disabled="busy" data-testid="qc-btn" @click="qualityCheck">完成质量校验</Button>
        <Button v-if="ov?.qcDone" class="px-4 py-[7px]" data-testid="go-a5" @click="router.push({ name: 'A5' })">生成月度报告 →</Button>
      </template>
    </PageHeader>

    <div v-if="error" class="mt-5 rounded-[10px] border border-line bg-surface px-6 py-10 text-center text-[13px] text-ink-muted">
      数据归集加载失败:{{ error }} <button type="button" class="ml-2 cursor-pointer text-primary" @click="load">重试</button>
    </div>

    <template v-else-if="ov">
      <div class="mt-5 grid grid-cols-4 gap-3.5" data-testid="a3-kpis">
        <KpiCard label="数据源按时到数" :value="`${arrived}`" :unit="`/ ${ov.sources.length} 个`" icon="data" :tone="arrived === ov.sources.length ? 'success' : 'warning'" :spark="scoreSpark" :desc="ov.allArrived ? '本期已全部到数' : '尚有数据源未到齐'" />
        <KpiCard label="数据完整性" :value="`${ov.quality.completenessPct}%`" icon="check" tone="primary" :desc="`一致性 ${ov.quality.consistencyPct}%`" />
        <KpiCard label="及时性" :value="ov.quality.timelinessPct != null ? `${ov.quality.timelinessPct}%` : '—'" icon="clock" tone="ai" desc="按约定到数日期统计" />
        <KpiCard label="质量校验" :value="ov.qcDone ? '已完成' : '待校验'" icon="shield" :tone="ov.qcDone ? 'success' : 'warning'" :desc="ov.qcDone ? '可生成月度报告' : '到齐后由行政管理组执行'" small />
      </div>

      <!-- 流水线 -->
      <Panel :title="ov.title" icon="data" class="mt-3.5">
        <div class="grid grid-cols-6 gap-2" data-testid="pipeline">
          <div v-for="p in ov.pipeline" :key="p.no" class="rounded-lg border px-3 py-2.5" :class="stepStyle[p.state].cls" :data-state="p.state">
            <div class="flex justify-between text-[11px]" :class="stepStyle[p.state].fg"><span>{{ p.no }}</span><span>{{ stepStyle[p.state].label }}</span></div>
            <div class="mt-0.5 text-[13px] font-semibold text-ink">{{ p.name }}</div>
            <div class="text-[12px] text-ink-muted">{{ p.count }}</div>
          </div>
        </div>
      </Panel>

      <!-- 数据源 -->
      <div class="mt-3.5 grid grid-cols-5 gap-3" data-testid="sources">
        <button
          v-for="s in ov.sources"
          :key="s.id"
          type="button"
          class="flex cursor-pointer flex-col gap-1.5 rounded-[10px] border-[1.5px] bg-surface p-3 text-left transition-colors hover:border-primary"
          :class="s.id === selId ? 'border-primary' : 'border-line'"
          :data-source="s.name"
          @click="selId = s.id"
        >
          <div class="text-[13px] font-semibold text-ink">{{ s.name }}</div>
          <div class="text-[11px] text-ink-muted">{{ s.provider }}</div>
          <div class="flex gap-1.5 text-[11px] text-ink-muted">
            <span class="rounded border border-line px-1.5">{{ s.mode }}</span><span class="rounded border border-line px-1.5">{{ s.frequency }}</span>
          </div>
          <div class="mt-0.5 flex items-center justify-between">
            <Tag :tone="statusTone(s.status)">{{ s.statusLabel }}</Tag>
            <span class="text-[12px] text-ink-muted">质量 <b class="text-[14px]" :class="s.score != null && s.score < 80 ? 'text-warning' : 'text-ink'">{{ s.score ?? '—' }}</b></span>
          </div>
        </button>
      </div>

      <div class="mt-3.5 grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_minmax(0,1.1fr)] items-start gap-3.5">
        <!-- 依赖指标 -->
        <Panel :title="detail?.name ?? '数据源'" sub="依赖该数据源的指标">
          <div v-for="d in detail?.indicators ?? []" :key="d.name" class="flex items-center justify-between border-b border-divider py-[7px] text-[12px]">
            <span class="text-ink">{{ d.name }}</span>
            <Tag :tone="d.suspended ? 'warning' : 'success'">{{ d.suspended ? '本期暂缓' : '可计算' }}</Tag>
          </div>
          <div v-if="detail?.late" class="mt-2.5 rounded-lg border border-warning-line bg-warning-soft px-2.5 py-2 text-[12px] text-warning-ink" data-testid="late-note">
            应于 {{ detail.dueDate }} 到数,已逾期。依赖指标本期暂缓计算,已通知省平台。
          </div>
        </Panel>

        <!-- 质量报告 -->
        <Panel title="质量报告">
          <div class="mb-3 grid grid-cols-3 gap-2">
            <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">完整性</div><div class="text-[18px] font-semibold">{{ ov.quality.completenessPct }}%</div></div>
            <div class="rounded-lg bg-subtle px-2.5 py-2"><div class="text-[11px] text-ink-muted">一致性</div><div class="text-[18px] font-semibold">{{ ov.quality.consistencyPct }}%</div></div>
            <div class="rounded-lg bg-subtle px-2.5 py-2">
              <div class="text-[11px] text-ink-muted">及时性</div>
              <div class="text-[18px] font-semibold" :class="timeliness != null && timeliness < 100 ? 'text-warning' : ''" data-testid="timeliness">
                {{ timeliness != null ? `${timeliness}%` : '—' }}
              </div>
            </div>
          </div>
          <div class="grid grid-cols-[1fr_84px_76px] border-b border-line py-1 text-[11px] text-ink-muted"><span>机构</span><span class="text-right">合并症编码率</span><span class="text-right">清单质控率</span></div>
          <div v-for="q in ov.quality.orgs" :key="q.org" class="grid grid-cols-[1fr_84px_76px] border-b border-divider py-[5px] text-[12px]">
            <span class="text-ink">{{ q.org }}</span>
            <span class="text-right" :class="q.comorbidityLow ? 'text-warning' : 'text-ink'">{{ q.comorbidityPct }}%</span>
            <span class="text-right" :class="q.listQcLow ? 'text-warning' : 'text-ink'">{{ Number(q.listQcPct).toFixed(1) }}%</span>
          </div>
          <div class="mt-1.5 text-[11px] text-ink-faint">
            {{ ov.quality.engineAvailable ? '橙色:合并症编码率 < 60% 或质控率 < 95%' : '统计服务暂不可用,及时性与关注项稍后显示' }}
          </div>
        </Panel>

        <!-- 血缘查询 -->
        <Panel title="血缘查询" sub="选择指标反查来源">
          <div class="mb-3.5 flex flex-wrap gap-1.5">
            <Chip v-for="c in ov.lineageIndicators" :key="c" :on="c === lin" @click="lin = c">{{ c }}</Chip>
          </div>
          <div class="flex flex-col" data-testid="lineage">
            <div v-for="(l, i) in lineage" :key="l.key" class="flex gap-2.5">
              <div class="flex w-3 flex-col items-center">
                <span class="mt-1 size-2.5 rounded-full" :class="i === 0 ? 'bg-primary-solid' : l.missing ? 'bg-warning' : 'bg-neutral-2'" />
                <span v-if="i < lineage.length - 1" class="w-[1.5px] flex-1 bg-line" />
              </div>
              <div class="pb-3">
                <div class="text-[11px] text-ink-muted">{{ l.key }}</div>
                <div class="font-mono text-[12px]" :class="l.missing ? 'text-warning' : 'text-ink'">{{ l.value }}</div>
              </div>
            </div>
          </div>
        </Panel>
      </div>
    </template>
    <div v-else class="mt-5 grid grid-cols-5 gap-3">
      <div v-for="i in 10" :key="i" class="h-[118px] animate-pulse rounded-[10px] border border-line bg-surface" />
    </div>
  </div>
</template>
