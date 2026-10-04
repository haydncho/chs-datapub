<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { publishApi, type FlowDetail, type TodoItem } from '@/api/publish'
import KpiCard from '@/components/shared/KpiCard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { pageDef } from '@/lib/nav'
import { notifyError } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

/**
 * 月度发布向导:把「归集校验 → 分析成稿 → 专家审核 → 召集人审批 → 定向发布 → 签收 → 意见整改 → 归档」这条主线
 * 画成一条时间线,当前步骤展开说明「谁来做、做什么、去哪做」并给出下一步按钮;进度与状态全部来自发布工作流的真实数据。
 */
const page = pageDef('W1')!
const router = useRouter()
const auth = useAuthStore()

interface Guide { who: string; todo: string; to: string; cta: string; also?: { to: string; label: string } }
/** 流程模板节点名 → 该步怎么做、去哪做(节点名取自流程设计器的默认模板)。 */
const GUIDE: Record<string, Guide> = {
  计划选题: { who: '行政管理组', todo: '确定本期要发布的内容:月度运行报告、预警汇总,或由算法推荐的病组专题。', to: '/a6', cta: '查看选题推荐' },
  归集校验: { who: '行政管理组', todo: '确认十类数据源均已到数并完成质量校验,数据口径不全不得往下走。', to: '/a3', cta: '去数据归集中心' },
  分析成稿: { who: '行政管理组 / 委托分析团队', todo: '用图表与报告模板拼装报告,或在专题工作台完成七段式专题并逐段人工审定。', to: '/a5', cta: '去拼装报告', also: { to: '/a7', label: '病组专题工作台' } },
  专家组审核: { who: '专家组', todo: '专家组列席审阅专题稿与解读,意见回到成稿环节修改。', to: '/a7', cta: '去专题审阅' },
  召集人审批: { who: '召集人', todo: '审阅发布包与定向范围,批准后数据才从分析监测区进入发布区;未批准不外发。', to: '/a8', cta: '去审批发布' },
  定向发布: { who: '行政管理组', todo: '按定向范围向机构生成发布报告并送达,同时生成实名水印编号。', to: '/a8', cta: '去执行定向发布' },
  签收查阅: { who: '各定点机构', todo: '机构在报告中心(或手机版)签收并查阅,医保局在此跟踪签收率。', to: '/a8', cta: '查看签收进度' },
  意见申诉: { who: '机构 → 意见承办人', todo: '机构提出数据异议与意见,承办人在时限内答复。', to: '/a10', cta: '去意见管理' },
  答复整改: { who: '意见承办人', todo: '所有意见答复完毕、预警整改跟踪到位后方可归档。', to: '/a10', cta: '去处理意见', also: { to: '/a11', label: '预警与整改' } },
  归档复盘: { who: '行政管理组', todo: '归档本期发布物与全部留痕,复盘本期问题,进入下一期。', to: '/a8', cta: '去归档' },
}

const items = ref<TodoItem[]>([])
const selId = ref<number | null>(null)
const d = ref<FlowDetail | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const groups = await publishApi.todos()
    items.value = groups.flatMap((g) => g.items).filter((i) => i.type === 'flow')
    const first = items.value.find((i) => i.name.includes('月度') && i.status !== '已归档') ?? items.value.find((i) => i.status !== '已归档') ?? items.value[0]
    selId.value = first?.id ?? null
  } catch (e) {
    notifyError(e)
  } finally {
    loading.value = false
  }
})
watch(selId, async (id) => {
  d.value = null
  if (id == null) return
  try {
    d.value = await publishApi.flow(id)
  } catch (e) {
    notifyError(e)
  }
})

const steps = computed(() => d.value?.steps ?? [])
const cur = computed(() => d.value?.flow.step ?? 0)
const state = (idx: number) => (d.value?.flow.archived || idx < cur.value ? 'done' : idx === cur.value ? 'now' : 'todo')
const doneCount = computed(() => steps.value.filter((s) => state(s.idx) === 'done').length)
const nowStep = computed(() => steps.value.find((s) => state(s.idx) === 'now'))
const myRoleIsConvener = computed(() => auth.user?.role === 'CONVENER')
const go = (to: string) => void router.push(to)
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div v-if="loading" class="mt-5 h-64 animate-pulse rounded-2xl bg-subtle" />
    <div v-else-if="!items.length" class="mt-5 rounded-2xl border border-line-soft bg-surface px-6 py-12 text-center text-[13px] text-ink-muted">当前没有在办的发布流程。</div>

    <template v-else>
      <div class="mt-5 flex flex-wrap items-center gap-2" data-testid="guide-flows">
        <span class="text-[12px] text-ink-muted">发布物</span>
        <button
          v-for="i in items"
          :key="i.id"
          type="button"
          class="cursor-pointer rounded-full border px-3.5 py-1.5 text-[13px] transition-colors"
          :class="i.id === selId ? 'border-primary bg-primary-tint font-semibold text-primary' : 'border-line bg-surface text-ink-sub hover:bg-hover'"
          :data-flow="i.id"
          @click="selId = i.id"
        >{{ i.name }}</button>
      </div>

      <div v-if="d" class="mt-4 grid grid-cols-4 gap-3.5" data-testid="guide-kpis">
        <KpiCard label="当前步骤" :value="d.flow.archived ? '已归档' : d.flow.stepLabel.replace(/^第\d+步\s*·\s*/, '')" icon="flow" tone="primary" small :desc="`第 ${cur} / ${steps.length} 步`" />
        <KpiCard label="主线进度" :value="`${doneCount}/${steps.length}`" unit="步" icon="check" tone="success" :spark="steps.map((s) => (state(s.idx) === 'done' ? 3 : state(s.idx) === 'now' ? 2 : 1))" desc="已完成步骤" />
        <KpiCard label="覆盖机构" :value="String(d.coverage?.count ?? '—')" unit="家" icon="inst" tone="ai" desc="定向发布范围" />
        <KpiCard label="下一步由" :value="nowStep ? GUIDE[nowStep.name]?.who.split(' ')[0] ?? '—' : '—'" icon="users" tone="warning" small :desc="nowStep?.sub ?? ''" />
      </div>

      <div v-if="d" class="mt-4 grid grid-cols-[minmax(0,1fr)_340px] items-start gap-4">
        <!-- 主线时间线 -->
        <ol class="flex flex-col" data-testid="guide-steps">
          <li v-for="(s, i) in steps" :key="s.idx" class="relative flex gap-4 pb-3" :data-step="s.idx" :data-state="state(s.idx)">
            <div class="flex flex-col items-center">
              <span
                class="z-[1] flex size-9 flex-none items-center justify-center rounded-full border-2 text-[13px] font-semibold"
                :class="state(s.idx) === 'done' ? 'border-success-solid bg-success-solid text-white' : state(s.idx) === 'now' ? 'border-primary-solid bg-primary-solid text-white shadow-[0_0_0_5px_var(--c-primary-soft)]' : 'border-line-strong bg-surface text-ink-faint'"
              >{{ state(s.idx) === 'done' ? '✓' : s.idx }}</span>
              <span v-if="i < steps.length - 1" class="mt-1 w-0.5 flex-1" :class="state(s.idx) === 'done' ? 'bg-success-solid' : 'bg-line'" />
            </div>
            <div
              class="min-w-0 flex-1 rounded-2xl border px-5 py-3.5 transition-colors"
              :class="state(s.idx) === 'now' ? 'border-primary-line bg-primary-tint/50 shadow-card' : 'border-line-soft bg-surface'"
            >
              <div class="flex items-center gap-2.5">
                <span class="text-[15px] font-semibold" :class="state(s.idx) === 'todo' ? 'text-ink-muted' : 'text-ink'">{{ s.name }}</span>
                <Tag v-if="s.gate" tone="warning">审批关口</Tag>
                <Tag v-if="state(s.idx) === 'now'" tone="primary">进行中</Tag>
                <span class="ml-auto text-[12px] text-ink-faint">{{ GUIDE[s.name]?.who }} · {{ s.days }} 天</span>
              </div>
              <div class="mt-1 text-[12px] text-ink-muted">{{ s.sub }}</div>
              <template v-if="state(s.idx) === 'now' && GUIDE[s.name]">
                <div class="mt-2.5 text-[13px] leading-[1.7] text-ink-body">{{ GUIDE[s.name].todo }}</div>
                <div v-if="s.gate && !myRoleIsConvener" class="mt-2 rounded-lg bg-warning-soft px-3 py-2 text-[12px] text-warning-ink">此步由召集人批准;你可以提交与调整定向范围,批准与驳回由召集人办理。</div>
                <div class="mt-3 flex flex-wrap items-center gap-2.5">
                  <Button data-testid="guide-cta" @click="go(GUIDE[s.name].to)">{{ GUIDE[s.name].cta }} →</Button>
                  <Button v-if="GUIDE[s.name].also" variant="outline" @click="go(GUIDE[s.name].also!.to)">{{ GUIDE[s.name].also!.label }}</Button>
                </div>
              </template>
            </div>
          </li>
        </ol>

        <!-- 右栏:本期概况 + 最近留痕 -->
        <div class="flex flex-col gap-3.5">
          <Panel title="本期概况" icon="doc">
            <div class="grid grid-cols-[64px_1fr] gap-y-2 text-[12px]">
              <span class="text-ink-muted">发布物</span><span class="font-medium text-ink">{{ d.flow.subject }}</span>
              <span class="text-ink-muted">类型</span><span>{{ d.flow.kind }}</span>
              <span class="text-ink-muted">发布包</span><span>{{ d.flow.packageVersion }} · {{ d.pkg.items.length }} 项</span>
              <span class="text-ink-muted">排除</span><span class="text-ink-sub">{{ d.pkg.excluded.length ? `${d.pkg.excluded.length} 项“仅内部”指标` : '无' }}</span>
            </div>
            <Button variant="outline" size="sm" class="mt-3 w-full" @click="go('/a8')">打开发布工作流查看详情</Button>
          </Panel>
          <Panel title="最近留痕" icon="audit">
            <ol class="flex flex-col gap-2.5 border-l-2 border-line pl-3.5 text-[12px]">
              <li v-for="(l, i) in d.logs.slice(-5).reverse()" :key="i">
                <div><span class="font-mono text-ink-muted">{{ l.at }}</span> · <span class="font-medium text-ink">{{ l.who }}</span></div>
                <div class="text-ink-body">{{ l.what }}</div>
              </li>
            </ol>
          </Panel>
        </div>
      </div>
    </template>
  </div>
</template>
