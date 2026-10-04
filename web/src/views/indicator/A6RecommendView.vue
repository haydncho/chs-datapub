<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  recommendApi,
  type AnomalyRow,
  type AttrRow,
  type BenchResult,
  type MethodCard,
  type PresentationRow,
  type TopicList,
  type TopicRow,
} from '@/api/indicator'
import { fmt } from '@/components/indicator/tones'
import Chip from '@/components/shared/Chip.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/**
 * A6 智能推荐中心：五类推荐（选题 / 归因 / 标杆 / 异常 / 呈现）均为“候选 · 需人工确认”，可追溯到方法卡。
 * 得分、归因拆分、标杆分组由引擎计算；选题处理状态持久化且可撤销。
 */
const page = pageDef('A6')!
type Tab = 'topic' | 'attr' | 'bench' | 'anom' | 'pres'
const TABS: { id: Tab; label: string }[] = [
  { id: 'topic', label: '选题推荐' },
  { id: 'attr', label: '归因推荐' },
  { id: 'bench', label: '标杆推荐' },
  { id: 'anom', label: '异常推荐' },
  { id: 'pres', label: '呈现推荐' },
]
const tab = ref<Tab>('topic')
const errors = ref<Partial<Record<Tab, string>>>({})

const topics = ref<TopicList | null>(null)
const attr = ref<AttrRow[] | null>(null)
const th = ref(75)
const bench = ref<BenchResult | null>(null)
const anomalies = ref<AnomalyRow[] | null>(null)
const pres = ref<PresentationRow[] | null>(null)
const methods = ref<MethodCard[]>([])
const methodOpen = ref(false)
const busy = ref<string | null>(null)

const errMsg = (e: unknown) => (e instanceof Error ? e.message : '加载失败')

async function load(t: Tab) {
  try {
    if (t === 'topic') topics.value = await recommendApi.topics()
    else if (t === 'attr') attr.value = (await recommendApi.attribution()).rows
    else if (t === 'bench') bench.value = await recommendApi.benchmark(th.value)
    else if (t === 'anom') anomalies.value = await recommendApi.anomalies()
    else pres.value = await recommendApi.presentations()
    errors.value = { ...errors.value, [t]: '' }
  } catch (e) {
    errors.value = { ...errors.value, [t]: errMsg(e) }
  }
}
onMounted(async () => {
  void load('topic')
  try {
    methods.value = await recommendApi.methods()
  } catch {
    /* 方法卡加载失败时入口置灰 */
  }
})
watch(tab, (t) => {
  if ((t === 'attr' && !attr.value) || (t === 'bench' && !bench.value) || (t === 'anom' && !anomalies.value) || (t === 'pres' && !pres.value)) void load(t)
})
// 阈值步进：每档实时重算（引擎）
watch(th, () => void load('bench'))

const method = computed(() => methods.value.find((m) => m.tab === tab.value))

// ---------------------------------------------------------------- 选题
const ST: Record<string, { label: string; tone: 'warning' | 'success' | 'muted' }> = {
  CAND: { label: '候选', tone: 'warning' },
  ADOPT: { label: '已采纳', tone: 'success' },
  REJECT: { label: '已否决', tone: 'muted' },
}
function replace(row: TopicRow) {
  if (topics.value) topics.value.rows = topics.value.rows.map((r) => (r.code === row.code ? row : r))
}
async function act(code: string, fn: () => Promise<{ row: TopicRow; message: string }>) {
  busy.value = code
  try {
    const r = await fn()
    replace(r.row)
    notify(r.message)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}
const decide = (r: TopicRow, a: 'adopt' | 'reject') => act(r.code, () => recommendApi.decide(r.code, a))
const undo = (r: TopicRow) => act(r.code, () => recommendApi.undo(r.code))

const editing = ref<TopicRow | null>(null)
const editReasons = ref<string[]>([])
const editNote = ref('')
function openEdit(r: TopicRow) {
  editing.value = r
  editReasons.value = [...r.reasons]
  editNote.value = r.note ?? ''
}
function toggleReason(x: string) {
  const i = editReasons.value.indexOf(x)
  if (i >= 0) editReasons.value.splice(i, 1)
  else editReasons.value.push(x)
}
async function saveEdit() {
  const r = editing.value
  if (!r) return
  await act(r.code, () => recommendApi.modify(r.code, editReasons.value, editNote.value))
  editing.value = null
}

// ---------------------------------------------------------------- 标杆
const thMin = 60
const thMax = 90
const pctW = (n: number | undefined, total: number) => `${((n ?? 0) / total) * 100}%`

// ---------------------------------------------------------------- 异常
async function letter(a: AnomalyRow) {
  busy.value = `a${a.id}`
  try {
    const r = await recommendApi.letter(a.id)
    if (anomalies.value) anomalies.value = anomalies.value.map((x) => (x.id === a.id ? r.row : x))
    notify(r.message)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <section class="mt-5 rounded-[10px] border border-line bg-surface">
      <div class="flex items-center gap-6 border-b border-divider px-[18px]">
        <div class="flex gap-6" role="tablist" data-testid="a6-tabs">
          <button
            v-for="t in TABS"
            :key="t.id"
            type="button"
            role="tab"
            :aria-selected="tab === t.id"
            class="-mb-px cursor-pointer border-b-2 px-0.5 py-3 text-[13px]"
            :class="tab === t.id ? 'border-primary font-semibold text-primary' : 'border-transparent text-ink-muted hover:text-ink'"
            :data-tab="t.id"
            @click="tab = t.id"
          >{{ t.label }}</button>
        </div>
        <div class="flex-1" />
        <Tag tone="warning" data-testid="cand-badge">候选 · 需人工确认</Tag>
        <button type="button" class="cursor-pointer text-[12px] text-primary hover:underline disabled:opacity-40" :disabled="!method" data-testid="open-method" @click="methodOpen = true">方法卡 ›</button>
      </div>

      <div class="px-[18px] py-3.5 text-[12px]">
        <div v-if="errors[tab]" class="py-10 text-center text-danger" data-testid="tab-error">{{ errors[tab] }}</div>

        <!-- ============================================================ 选题推荐 -->
        <template v-else-if="tab === 'topic'">
          <div class="mb-2.5 text-ink-muted">{{ topics?.period }} 选题候选 · 综合得分 = 病例量、结算金额、趋势、基金差额、机构关注度、机构推荐、收治结构 Z 分数加权</div>
          <table class="data-table [&_td]:px-2.5 [&_th]:px-2.5" data-testid="topics">
            <thead>
              <tr><th class="w-[64px]">编码</th><th>病组</th><th class="w-[170px]">综合得分 ↓</th><th>入选理由</th><th class="w-[76px]">状态</th><th class="w-[150px]">操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="r in topics?.rows ?? []" :key="r.code" :data-topic="r.code" :class="r.status === 'REJECT' && 'opacity-50'">
                <td class="font-mono font-semibold text-primary">{{ r.code }}</td>
                <td class="font-medium text-ink">{{ r.name }}</td>
                <td>
                  <div class="flex items-center gap-2">
                    <div class="h-1.5 flex-1 rounded-[3px] bg-divider"><div class="h-1.5 rounded-[3px] bg-primary-solid" :style="{ width: `${r.score}%` }" /></div>
                    <span class="w-[22px] text-right font-semibold text-ink" data-testid="score">{{ r.score }}</span>
                  </div>
                </td>
                <td>
                  <div class="flex flex-wrap items-center gap-1">
                    <span v-for="x in r.reasons" :key="x" class="rounded-[3px] bg-chip px-[7px] py-px text-[11px] text-ink-sub">{{ x }}</span>
                    <Tag v-if="r.modified" tone="ai" class="!text-[10px]" :title="r.note ? `选题范围:${r.note}` : '入选理由经人工修改'">已修改</Tag>
                  </div>
                  <div v-if="r.note" class="mt-0.5 truncate text-[11px] text-ink-faint">范围:{{ r.note }}</div>
                </td>
                <td><Tag :tone="ST[r.status].tone" data-testid="topic-status">{{ ST[r.status].label }}</Tag></td>
                <td>
                  <div v-if="r.status === 'CAND'" class="flex gap-2.5">
                    <button type="button" class="act font-medium text-primary" :disabled="busy === r.code" data-act="adopt" @click="decide(r, 'adopt')">采纳</button>
                    <button type="button" class="act text-primary" :disabled="busy === r.code" data-act="edit" @click="openEdit(r)">修改</button>
                    <button type="button" class="act text-danger" :disabled="busy === r.code" data-act="reject" @click="decide(r, 'reject')">否决</button>
                  </div>
                  <div v-else class="flex items-center gap-2">
                    <button type="button" class="act text-ink-muted" :disabled="busy === r.code" data-act="undo" @click="undo(r)">撤销</button>
                    <span v-if="r.decidedBy" class="truncate text-[11px] text-ink-faint">{{ r.decidedBy }}</span>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </template>

        <!-- ============================================================ 归因推荐 -->
        <template v-else-if="tab === 'attr'">
          <div class="grid grid-cols-2 gap-3" data-testid="attr">
            <div
              v-for="a in attr ?? []"
              :key="a.code"
              class="rounded-[10px] border border-line p-3.5"
              :class="a.status === 'withheld' && 'bg-subtle'"
              :data-attr="a.code"
            >
              <div class="flex items-center justify-between">
                <span class="font-semibold text-ink">{{ a.code }} {{ a.name }}</span>
                <Tag :tone="a.status === 'ok' ? 'warning' : 'muted'">{{ a.status === 'ok' ? '候选' : '暂不输出' }}</Tag>
              </div>
              <template v-if="a.status === 'ok'">
                <div class="mt-1 mb-2.5 text-ink-muted">机构间例均费用差异 {{ fmt(a.totalDiff) }} 元的拆分</div>
                <div class="flex h-[22px] overflow-hidden rounded text-[11px] text-white">
                  <span class="flex items-center overflow-hidden pl-2 whitespace-nowrap" :style="{ width: `${a.patientPct}%`, background: 'var(--c-muted-solid)' }">{{ (a.patientPct ?? 0) < 32 ? '患者' : '患者差异' }} {{ Math.round(a.patientPct ?? 0) }}%</span>
                  <span class="flex items-center overflow-hidden pl-2 whitespace-nowrap" :style="{ width: `${a.behaviorPct}%`, background: 'var(--c-warning-solid)' }">行为差异 {{ Math.round(a.behaviorPct ?? 0) }}%</span>
                </div>
                <div class="mt-2 text-ink-body">主要行为:{{ a.topBehaviors.map((b) => `${b.name}(+${fmt(b.amount)} 元)`).join('、') }}</div>
              </template>
              <template v-else>
                <div class="mt-1 mb-2.5 text-ink-muted">归因结论暂不输出</div>
                <div class="rounded-lg bg-chip px-3 py-2.5 text-ink-body" data-testid="withheld-reason">{{ a.reason }}</div>
              </template>
            </div>
          </div>
          <div class="mt-2.5 flex gap-4 text-[11px] text-ink-faint">
            <span class="flex items-center gap-1.5"><i class="inline-block size-2.5 rounded-sm" style="background: var(--c-muted-solid)" />患者差异(年龄、合并症等病情因素)</span>
            <span class="flex items-center gap-1.5"><i class="inline-block size-2.5 rounded-sm" style="background: var(--c-warning-solid)" />行为差异(诊疗行为选择)</span>
          </div>
        </template>

        <!-- ============================================================ 标杆推荐 -->
        <template v-else-if="tab === 'bench'">
          <div class="mb-3 flex flex-wrap items-center gap-4">
            <div class="text-ink-body" data-testid="bench-rule">分组规则:{{ bench?.rule ?? '…' }}</div>
            <div class="flex-1" />
            <span class="text-ink-muted">偏离阈值</span>
            <div class="flex items-center overflow-hidden rounded-lg border border-line" data-testid="th-stepper">
              <button type="button" class="cursor-pointer border-r border-line px-2.5 py-[3px] hover:bg-hover disabled:cursor-not-allowed disabled:opacity-40" :disabled="th <= thMin" aria-label="降低阈值" data-testid="th-down" @click="th -= 5">−</button>
              <span class="w-[52px] py-[3px] text-center font-semibold text-ink" data-testid="th-value">P{{ th }}</span>
              <button type="button" class="cursor-pointer border-l border-line px-2.5 py-[3px] hover:bg-hover disabled:cursor-not-allowed disabled:opacity-40" :disabled="th >= thMax" aria-label="提高阈值" data-testid="th-up" @click="th += 5">+</button>
            </div>
          </div>
          <table class="data-table [&_td]:px-2.5 [&_th]:px-2.5" data-testid="bench">
            <thead><tr><th class="w-[100px]">同级组</th><th class="w-[70px]">机构数</th><th>分组分布</th><th class="w-[200px]">标杆 / 中间 / 偏离</th></tr></thead>
            <tbody>
              <tr v-for="b in bench?.rows ?? []" :key="b.name" :data-bench="b.name">
                <td class="font-medium text-ink">{{ b.name }}</td>
                <td>{{ b.n }}</td>
                <td>
                  <div v-if="b.small" class="text-warning" data-testid="bench-small">同级机构数 &lt; 5,样本不足,不分组</div>
                  <div v-else class="flex h-3.5 overflow-hidden rounded-[3px]" :title="`标杆线 ≤ ${fmt(b.benchLine)} 元 · 偏离线 > ${fmt(b.deviantLine)} 元`">
                    <span :style="{ width: pctW(b.bench, b.n), background: 'var(--c-success-solid)' }" />
                    <span :style="{ width: pctW(b.middle, b.n), background: 'var(--c-bar-neutral-a)' }" />
                    <span :style="{ width: pctW(b.deviant, b.n), background: 'var(--c-warning-solid)' }" />
                  </div>
                </td>
                <td data-testid="bench-counts">
                  <template v-if="b.small"><span class="text-ink-faint">— / — / —</span></template>
                  <template v-else><b class="text-success">{{ b.bench }}</b> / {{ b.middle }} / <b class="text-warning">{{ b.deviant }}</b> 家</template>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="mt-2.5 flex gap-4 text-[11px] text-ink-faint">
            <span class="flex items-center gap-1.5"><i class="inline-block size-2.5 rounded-sm" style="background: var(--c-success-solid)" />标杆组</span>
            <span class="flex items-center gap-1.5"><i class="inline-block size-2.5 rounded-sm" style="background: var(--c-bar-neutral-a)" />中间组</span>
            <span class="flex items-center gap-1.5"><i class="inline-block size-2.5 rounded-sm" style="background: var(--c-warning-solid)" />偏离组</span>
            <span>悬停分布条查看标杆线 / 偏离线</span>
          </div>
        </template>

        <!-- ============================================================ 异常推荐 -->
        <template v-else-if="tab === 'anom'">
          <table class="data-table [&_td]:px-2.5 [&_th]:px-2.5" data-testid="anomalies">
            <thead><tr><th class="w-[170px]">规则</th><th>机构(仅医保局可见)</th><th>病组</th><th>触发值</th><th class="w-[64px]">级别</th><th class="w-[160px]">操作</th></tr></thead>
            <tbody>
              <tr v-for="a in anomalies ?? []" :key="a.id" :data-anomaly="a.id">
                <td>{{ a.rule }}</td>
                <td class="text-ink">{{ a.org }}</td>
                <td>{{ a.drg }}</td>
                <td>{{ a.value }}</td>
                <td><span class="font-semibold" :class="a.level === '预警' ? 'text-danger' : 'text-warning'">{{ a.level }}</span></td>
                <td>
                  <span v-if="a.letterNo" class="text-ink-muted" :title="`${a.letterBy ?? ''} 生成`" data-testid="letter-no">已生成 <span class="font-mono">{{ a.letterNo }}</span><RouterLink v-if="a.alertStatus" to="/a11" class="ml-1.5 text-primary" data-testid="alert-status">A11 · {{ a.alertStatus }}</RouterLink></span>
                  <button v-else type="button" class="act text-primary" :disabled="busy === `a${a.id}`" data-act="letter" @click="letter(a)">生成提醒函</button>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="mt-2.5 text-[11px] text-ink-faint">机构名称仅在分析监测区对医保局可见;提醒函草稿进入发布工作流「预警提醒函」,经审批后定向发出。</div>
        </template>

        <!-- ============================================================ 呈现推荐 -->
        <template v-else>
          <div class="grid grid-cols-2 gap-3" data-testid="pres">
            <div v-for="p in pres ?? []" :key="p.indicator" class="rounded-[10px] border border-line p-3.5">
              <div class="flex items-center justify-between">
                <span class="font-semibold text-ink">{{ p.indicator }}</span>
                <span class="text-primary">推荐:{{ p.chart }}</span>
              </div>
              <div class="mt-1 text-ink-muted">{{ p.reason }}</div>
            </div>
          </div>
        </template>
      </div>
    </section>

    <!-- 方法卡 -->
    <Dialog v-model:open="methodOpen">
      <DialogContent class="w-[480px] max-w-[480px] gap-0 rounded-[14px] border-line bg-surface p-0 sm:max-w-[480px]" data-testid="method-dialog">
        <div class="border-b border-divider px-5 py-4"><DialogTitle class="text-[15px] font-semibold text-ink">方法卡 · {{ method?.title }}</DialogTitle></div>
        <div class="grid grid-cols-[72px_1fr] gap-2.5 px-5 py-4 text-[12px]">
          <template v-for="r in method?.rows ?? []" :key="r[0]"><span class="text-ink-muted">{{ r[0] }}</span><span class="text-ink">{{ r[1] }}</span></template>
        </div>
        <div class="px-5 pb-4 text-[11px] text-ink-faint">所有推荐结果均为“候选”,须经工作组人工确认后方可进入发布流程。</div>
      </DialogContent>
    </Dialog>

    <!-- 修改选题 -->
    <Dialog :open="!!editing" @update:open="(v: boolean) => !v && (editing = null)">
      <DialogContent class="w-[480px] max-w-[480px] gap-0 rounded-[14px] border-line bg-surface p-0 sm:max-w-[480px]" data-testid="edit-dialog">
        <div class="border-b border-divider px-5 py-4"><DialogTitle class="text-[15px] font-semibold text-ink">修改选题 · {{ editing?.code }} {{ editing?.name }}</DialogTitle></div>
        <div class="flex flex-col gap-3 px-5 py-4 text-[12px]">
          <div>
            <div class="mb-1.5 text-ink-muted">入选理由(至少保留一个)</div>
            <div class="flex flex-wrap gap-1.5">
              <Chip v-for="x in topics?.reasonOptions ?? []" :key="x" :on="editReasons.includes(x)" :data-reason="x" @click="toggleReason(x)">{{ x }}</Chip>
            </div>
            <div class="mt-1 text-[11px] text-ink-faint">算法理由:{{ editing?.engineReasons.join('、') }}</div>
          </div>
          <div>
            <div class="mb-1.5 text-ink-muted">选题范围说明</div>
            <Textarea v-model="editNote" class="h-16 resize-none text-[12px]" maxlength="256" placeholder="如:限定收治该病组 ≥ 30 例的二级及以上机构" data-testid="edit-note" />
          </div>
        </div>
        <div class="flex justify-end gap-2 px-5 pb-4">
          <Button variant="outline" size="sm" @click="editing = null">取消</Button>
          <Button size="sm" :disabled="!editReasons.length || busy === editing?.code" data-testid="edit-save" @click="saveEdit">保存修改</Button>
        </div>
      </DialogContent>
    </Dialog>
  </div>
</template>

<style scoped>
.act {
  cursor: pointer;
}
.act:hover:not(:disabled) {
  text-decoration: underline;
}
.act:disabled {
  opacity: 0.4;
  cursor: default;
}
</style>
