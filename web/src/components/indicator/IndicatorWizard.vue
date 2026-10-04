<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  indicatorApi,
  type FormulaCheck,
  type IndMeta,
  type PreviewResult,
  type SubmitResult,
  type WizConfig,
  type WizDraft,
} from '@/api/indicator'
import Chip from '@/components/shared/Chip.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { notify, notifyError } from '@/lib/notify'
import FormulaEditor from './FormulaEditor.vue'
import PctBar from './PctBar.vue'
import { fmt } from './tones'

/**
 * 新建指标四步向导：定义指标 → 设规则 → 绑呈现 → 预览提交。草稿在服务端自动保存；
 * 公式由引擎校验；第 4 步“以机构身份预览”由服务端调用引擎裁剪（机构只拿到本院值与同级分位）；提交生成上线审批单号。
 */
const props = defineProps<{ meta: IndMeta }>()
const emit = defineEmits<{ back: [submitted: boolean] }>()

const STEPS = ['定义指标', '设规则', '绑呈现', '预览提交']
const TIER_DESC = ['默认 · 机构仅见本院在同级中的分位', '如“三级医院A”,不具名横向对比', '显示机构名称与排名']
const step = ref(1)
const draft = ref<WizDraft | null>(null)
const cfg = ref<WizConfig | null>(null)
const saving = ref(false)
const saveErr = ref('')
const check = ref<FormulaCheck | null>(null)
const checking = ref(false)
const result = ref<SubmitResult | null>(null)
const busy = ref(false)

const locked = computed(() => !!draft.value?.submitted)

onMounted(async () => {
  try {
    const d = await indicatorApi.openDraft()
    apply(d)
  } catch (e) {
    notifyError(e)
    emit('back', false)
  }
})

let ready = false
function apply(d: WizDraft) {
  ready = false
  draft.value = d
  cfg.value = structuredClone(d.config)
  setTimeout(() => (ready = true))
  void runCheck()
}

// ---------------------------------------------------------------- 自动保存（防抖）
let saveTimer: ReturnType<typeof setTimeout> | undefined
watch(
  cfg,
  () => {
    if (!ready || !cfg.value || !draft.value || locked.value) return
    clearTimeout(saveTimer)
    saveTimer = setTimeout(save, 600)
  },
  { deep: true },
)
async function save() {
  if (!cfg.value || !draft.value || locked.value) return
  saving.value = true
  try {
    const d = await indicatorApi.saveDraft(draft.value.id, cfg.value)
    draft.value = { ...d, config: draft.value.config }
    saveErr.value = ''
  } catch (e) {
    saveErr.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}
async function flush() {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = undefined
    await save()
  }
}

// ---------------------------------------------------------------- 公式校验（防抖，引擎）
let checkTimer: ReturnType<typeof setTimeout> | undefined
let checkSeq = 0
watch(
  () => [cfg.value?.formula, cfg.value?.dims.join(',')],
  () => {
    if (!ready) return
    checking.value = true
    clearTimeout(checkTimer)
    checkTimer = setTimeout(runCheck, 400)
  },
)
async function runCheck() {
  if (!cfg.value) return
  const seq = ++checkSeq
  checking.value = true
  try {
    const r = await indicatorApi.validate(cfg.value.formula, cfg.value.dims)
    if (seq !== checkSeq) return
    check.value = r
    if (r.ok && r.name && cfg.value.name !== r.name) cfg.value.name = r.name
  } catch (e) {
    if (seq === checkSeq) check.value = { ok: false, errors: [], atoms: [], functions: [], groupBy: [], message: e instanceof Error ? e.message : '校验失败' }
  } finally {
    if (seq === checkSeq) checking.value = false
  }
}
onBeforeUnmount(() => {
  clearTimeout(checkTimer)
  void flush()
})

// ---------------------------------------------------------------- 第 1 步：定义
function atomRole(a: string) {
  return cfg.value?.numerator === a ? '分子' : cfg.value?.denominator === a ? '分母' : ''
}
function toggleAtom(a: string) {
  const c = cfg.value
  if (!c || locked.value) return
  if (c.numerator === a) c.numerator = null
  else if (c.denominator === a) c.denominator = null
  else if (!c.numerator) c.numerator = a
  else if (!c.denominator) c.denominator = a
  else notify('分子、分母已选满,请先取消一个已选原子指标')
}
function toggle<T>(arr: T[], v: T) {
  const i = arr.indexOf(v)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(v)
}
function toggleDim(d: string) {
  if (!cfg.value || locked.value) return
  toggle(cfg.value.dims, d)
  cfg.value.dims.sort((a, b) => props.meta.dims.indexOf(a) - props.meta.dims.indexOf(b))
}
function addFilter() {
  cfg.value?.filters.push({ field: props.meta.filterFields[0], op: '=', value: '' })
}

/** 按左侧配置生成公式文本。 */
function generate() {
  const c = cfg.value
  if (!c) return
  const n = c.numerator ?? '分子指标'
  const d = c.denominator
  const expr: string[] =
    c.calc === '求和' ? [`SUM(${n})`]
    : c.calc === '均值' ? [`AVG(${n})`]
    : c.calc === '分位' ? [`PCTL(${n}, 50)`]
    : d ? [`SUM(${n})`, `/ COUNT(${d})`] : [`SUM(${n})`]
  const lit = (v: string) => (/^\d+(\.\d+)?$/.test(v.trim()) ? v.trim() : `'${v.trim().replace(/'/g, '')}'`)
  const where = c.filters.filter((f) => f.value.trim()).map((f) => `${f.field} ${f.op === '≠' ? '!=' : f.op} ${lit(f.value)}`)
  const out = [`${c.name || '新指标'} =`, ...expr.map((e) => `  ${e}`)]
  if (where.length) out.push(`WHERE ${where.join(' AND ')}`)
  if (c.dims.length) out.push(`GROUP BY ${c.dims.join(', ')}`)
  c.formula = out.join('\n')
}

// ---------------------------------------------------------------- 第 2 步：规则
const peerRows = computed(() => props.meta.peerGroups.map((g) => ({ ...g, suppressed: !!cfg.value && g.n < cfg.value.minOrgs })))
const tierWarn = computed(() =>
  cfg.value && !cfg.value.internal && cfg.value.tier !== 0
    ? `切换至「${props.meta.tierNames[cfg.value.tier]}」需提交召集人审批;审批通过前按「匿名分位」发布。`
    : '',
)
function clampInt(key: 'minOrgs' | 'minCases' | 'warnRise', min: number, max: number) {
  if (!cfg.value) return
  const v = Math.round(Number(cfg.value[key]))
  cfg.value[key] = Number.isFinite(v) ? Math.min(max, Math.max(min, v)) : min
}

// ---------------------------------------------------------------- 第 4 步：以机构身份预览
const orgs = computed(() => [...props.meta.previewOrgs, '召集人(全量)'])
const orgSel = ref(0)
const pv = ref<PreviewResult | null>(null)
const pvAll = ref<PreviewResult | null>(null)
const pvErr = ref('')
const pvLoading = ref(false)

async function loadPreview() {
  if (!draft.value || !cfg.value) return
  await flush()
  pvLoading.value = true
  try {
    const org = orgSel.value < props.meta.previewOrgs.length ? props.meta.previewOrgs[orgSel.value] : null
    pv.value = await indicatorApi.preview(draft.value.id, org)
    // 仅内部：机构身份下不渲染，下方给出医保局内部视图
    pvAll.value = pv.value.mode === 'internal' ? await indicatorApi.preview(draft.value.id, null) : null
    pvErr.value = ''
  } catch (e) {
    pv.value = null
    pvErr.value = e instanceof Error ? e.message : '预览失败'
  } finally {
    pvLoading.value = false
  }
}
watch([step, orgSel], () => {
  if (step.value === 4) void loadPreview()
})

async function submit() {
  if (!draft.value) return
  busy.value = true
  try {
    await flush()
    const r = await indicatorApi.submit(draft.value.id)
    result.value = r
    draft.value = r.draft
    notify(r.message)
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

async function restart() {
  if (!draft.value) return
  try {
    if (locked.value) apply(await indicatorApi.openDraft())
    else apply(await indicatorApi.resetDraft(draft.value.id))
    result.value = null
    step.value = 1
    notify('已按默认配置重新开始')
  } catch (e) {
    notifyError(e)
  }
}

const savedAt = computed(() => (draft.value ? new Date(draft.value.savedAt).toTimeString().slice(0, 5) : ''))
const showTplWarn = computed(() => cfg.value?.template === '排行条' && cfg.value.tier !== 2)
</script>

<template>
  <section class="rounded-[10px] border border-line bg-surface" data-testid="wizard">
    <div class="flex flex-wrap items-center gap-3 border-b border-divider px-5 py-3">
      <button type="button" class="cursor-pointer text-[12px] text-primary hover:underline" data-testid="back-list" @click="flush().then(() => emit('back', locked))">‹ 返回指标列表</button>
      <span class="text-[13px] font-semibold text-ink" data-testid="wiz-title">新建指标:{{ cfg?.name || '未命名' }}</span>
      <Tag v-if="cfg" :tone="cfg.internal ? 'muted' : 'muted'">{{ cfg.internal ? '仅内部' : '地方增选' }}</Tag>
      <span class="text-[11px] text-ink-faint" data-testid="save-state">
        <template v-if="locked">已提交 · {{ draft?.approvalNo }} · {{ draft?.version }}</template>
        <template v-else-if="saveErr"><span class="text-danger">自动保存失败:{{ saveErr }}</span></template>
        <template v-else>草稿自动保存 · {{ draft?.version }}{{ saving ? ' · 保存中…' : savedAt ? ` · ${savedAt} 已保存` : '' }}</template>
      </span>
      <button type="button" class="ml-auto cursor-pointer text-[12px] text-ink-muted hover:text-primary" data-testid="restart" @click="restart">{{ locked ? '再建一个指标' : '重新开始' }}</button>
    </div>

    <!-- 步骤条（可点） -->
    <div class="flex items-center px-10 py-4" role="tablist" data-testid="wiz-steps">
      <button
        v-for="(l, i) in STEPS"
        :key="l"
        type="button"
        role="tab"
        :aria-selected="step === i + 1"
        class="flex flex-1 cursor-pointer items-center gap-2 text-left"
        :class="i === STEPS.length - 1 && '!flex-none'"
        :data-step="i + 1"
        @click="step = i + 1"
      >
        <span
          class="flex size-[26px] flex-none items-center justify-center rounded-full border-[1.5px] text-[12px] font-semibold"
          :class="
            step === i + 1 ? 'border-primary bg-primary-solid text-white'
            : i + 1 < step ? 'border-success bg-success-solid text-white'
            : 'border-line bg-surface text-ink-faint'
          "
        >{{ i + 1 < step ? '✓' : i + 1 }}</span>
        <span class="font-medium whitespace-nowrap" :class="step === i + 1 ? 'text-ink' : 'text-ink-muted'">{{ l }}</span>
        <span v-if="i < STEPS.length - 1" class="mx-2 h-px flex-1 bg-line" />
      </button>
    </div>

    <div v-if="cfg" class="min-h-[380px] px-5 pt-1 pb-5 text-[12px]">
      <!-- ============================================================ 1 定义指标 -->
      <div v-if="step === 1" class="grid grid-cols-2 gap-6" data-testid="step-1">
        <div class="flex flex-col gap-4">
          <div>
            <div class="lbl">原子指标 <span class="font-normal text-ink-faint">依次点选分子、分母</span></div>
            <div class="flex flex-wrap gap-1.5" data-testid="atoms">
              <Chip v-for="a in meta.atoms" :key="a.name" :on="!!atomRole(a.name)" :disabled="locked" :data-atom="a.name" @click="toggleAtom(a.name)">
                {{ a.name }}<template v-if="atomRole(a.name)"> · {{ atomRole(a.name) }}</template>
              </Chip>
            </div>
          </div>
          <div>
            <div class="lbl">维度</div>
            <div class="flex flex-wrap gap-1.5" data-testid="dims">
              <Chip v-for="d in meta.dims" :key="d" :on="cfg.dims.includes(d)" :disabled="locked" :data-dim="d" @click="toggleDim(d)">{{ d }}</Chip>
            </div>
          </div>
          <div>
            <div class="lbl">过滤条件</div>
            <div class="flex flex-col gap-1.5" data-testid="filters">
              <div v-for="(f, i) in cfg.filters" :key="i" class="flex items-center gap-1.5">
                <span class="w-4 text-ink-faint">{{ i ? '且' : '' }}</span>
                <select v-model="f.field" class="fld" :disabled="locked" aria-label="过滤字段"><option v-for="x in meta.filterFields" :key="x">{{ x }}</option></select>
                <select v-model="f.op" class="fld" :disabled="locked" aria-label="运算符"><option v-for="x in meta.filterOps" :key="x">{{ x }}</option></select>
                <input v-model="f.value" class="fld w-[96px]" :readonly="locked" placeholder="取值" aria-label="取值">
                <button v-if="!locked" type="button" class="cursor-pointer px-1 text-ink-faint hover:text-danger" aria-label="删除条件" @click="cfg.filters.splice(i, 1)">×</button>
              </div>
              <button v-if="!locked && cfg.filters.length < 6" type="button" class="w-max cursor-pointer pl-5.5 text-primary hover:underline" data-testid="add-filter" @click="addFilter">+ 条件</button>
            </div>
          </div>
          <div>
            <div class="lbl">计算方式</div>
            <div class="seg" role="radiogroup" aria-label="计算方式">
              <button v-for="x in meta.calcs" :key="x" type="button" role="radio" :aria-checked="cfg.calc === x" :class="cfg.calc === x && 'on'" :disabled="locked" @click="cfg.calc = x">{{ x }}</button>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-x-5 gap-y-2">
            <div class="flex items-center gap-1.5">
              <span class="font-semibold">归属分组</span>
              <Chip v-for="g in meta.groups" :key="g" :on="cfg.grp === g" :disabled="locked" @click="cfg.grp = g">{{ g }}</Chip>
            </div>
            <label class="flex items-center gap-1.5">
              <span class="font-semibold">主题域</span>
              <select v-model="cfg.domain" class="fld" :disabled="locked"><option v-for="d in meta.domains" :key="d">{{ d }}</option></select>
            </label>
          </div>
        </div>
        <div>
          <div class="lbl">公式编辑器</div>
          <FormulaEditor v-model="cfg.formula" :functions="meta.functions" :check="check" :checking="checking" :disabled="locked" @generate="generate" />
          <div class="mt-1.5 text-[11px] text-ink-faint">首行“指标名称 =”即指标名称;GROUP BY 只能引用左侧已选维度。</div>
        </div>
      </div>

      <!-- ============================================================ 2 设规则 -->
      <div v-else-if="step === 2" class="grid grid-cols-2 gap-6" data-testid="step-2">
        <div class="flex flex-col gap-4">
          <div>
            <div class="lbl">同级分组(默认五档)</div>
            <div class="grid grid-cols-5 gap-1.5 text-center" data-testid="peer-groups">
              <div
                v-for="g in peerRows"
                :key="g.name"
                class="rounded-lg border px-1 py-2"
                :class="g.suppressed ? 'border-warning-line bg-warning-soft' : 'border-line'"
                :data-peer="g.name"
              >
                <div class="font-medium text-ink">{{ g.name }}</div>
                <div class="text-[11px]" :class="g.suppressed ? 'text-warning-ink' : 'text-ink-faint'">{{ g.n }} 家{{ g.suppressed ? ' · 抑制' : '' }}</div>
              </div>
            </div>
          </div>
          <div>
            <div class="lbl">小样本抑制阈值</div>
            <div class="flex flex-wrap items-center gap-2">
              同级机构数 &lt;
              <input v-model.number="cfg.minOrgs" type="number" min="1" max="50" class="fld num" :readonly="locked" aria-label="同级机构数阈值" data-testid="min-orgs" @change="clampInt('minOrgs', 1, 50)"> 家 或 病例数 &lt;
              <input v-model.number="cfg.minCases" type="number" min="0" max="1000" class="fld num" :readonly="locked" aria-label="病例数阈值" @change="clampInt('minCases', 0, 1000)"> 例时,不输出同级分位
            </div>
          </div>
          <div>
            <div class="lbl">预警阈值</div>
            <div class="flex flex-wrap items-center gap-1.5">
              高于同级
              <select v-model="cfg.warnPct" class="fld" :disabled="locked" aria-label="预警分位"><option v-for="p in meta.warnPcts" :key="p">{{ p }}</option></select>
              且 环比上升 &gt;
              <input v-model.number="cfg.warnRise" type="number" min="0" max="100" class="fld num" :readonly="locked" aria-label="环比上升阈值" @change="clampInt('warnRise', 0, 100)">% → 触发提醒函候选
            </div>
          </div>
          <div class="flex items-center justify-between rounded-lg border border-line p-3">
            <div>
              <div class="font-semibold">仅内部</div>
              <div class="text-[11px] text-ink-faint">个人级、病例级指标须开启;开启后不能加入任何发布包</div>
            </div>
            <Switch v-model="cfg.internal" :disabled="locked" aria-label="仅内部" data-testid="internal-switch" />
          </div>
        </div>
        <div>
          <div class="lbl">对标档位</div>
          <div class="flex flex-col gap-2" role="radiogroup" aria-label="对标档位" data-testid="tiers">
            <button
              v-for="(t, i) in meta.tierNames"
              :key="t"
              type="button"
              role="radio"
              :aria-checked="cfg.tier === i"
              :disabled="locked || cfg.internal"
              class="cursor-pointer rounded-[10px] border-[1.5px] px-3.5 py-3 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50"
              :class="cfg.tier === i ? 'border-primary bg-primary-tint' : 'border-line bg-surface hover:border-primary-line-strong'"
              :data-tier="t"
              @click="cfg.tier = i"
            >
              <div class="flex justify-between">
                <span class="font-semibold" :class="cfg.tier === i ? 'text-primary' : 'text-ink'">{{ t }}</span>
                <span class="text-[10px] text-ink-faint">{{ i === 0 ? '默认' : '需审批' }}</span>
              </div>
              <div class="mt-0.5 text-ink-muted">{{ TIER_DESC[i] }}</div>
            </button>
          </div>
          <div v-if="cfg.internal" class="mt-2.5 rounded-lg bg-chip px-3 py-2.5 text-ink-muted">已设为“仅内部”:不对外对标,对标档位不适用。</div>
          <div v-else-if="tierWarn" class="mt-2.5 rounded-lg border border-warning-line bg-warning-soft px-3 py-2.5 text-warning-ink" data-testid="tier-warn">⚠ {{ tierWarn }}</div>
        </div>
      </div>

      <!-- ============================================================ 3 绑呈现 -->
      <div v-else-if="step === 3" class="grid grid-cols-2 gap-6" data-testid="step-3">
        <div>
          <div class="lbl">图表模板</div>
          <div class="grid grid-cols-5 gap-1.5" role="radiogroup" aria-label="图表模板">
            <button
              v-for="t in meta.templates"
              :key="t"
              type="button"
              role="radio"
              :aria-checked="cfg.template === t"
              :disabled="locked"
              class="cursor-pointer rounded-lg border px-1 py-3 text-center transition-colors"
              :class="cfg.template === t ? 'border-primary bg-primary-tint font-semibold text-primary' : 'border-line text-ink-sub hover:border-primary-line-strong'"
              @click="cfg.template = t"
            >{{ t }}</button>
          </div>
          <div class="mt-4 grid grid-cols-[72px_1fr] items-center gap-2.5">
            <span class="text-ink-muted">标题</span><input v-model="cfg.title" class="fld" :readonly="locked" maxlength="64">
            <span class="text-ink-muted">单位</span><input v-model="cfg.unit" class="fld w-[120px]" :readonly="locked" maxlength="8">
            <span class="self-start pt-1.5 text-ink-muted">解读文字</span>
            <textarea v-model="cfg.note" class="fld min-h-[64px] resize-y leading-[1.6]" :readonly="locked" maxlength="256" />
          </div>
        </div>
        <div class="flex flex-col gap-4">
          <div>
            <div class="lbl">默认受众</div>
            <div v-if="cfg.internal" class="rounded-lg bg-chip px-2.5 py-2 text-ink-muted" data-testid="aud-locked">已设为“仅内部”:受众固定为医保局内部,不可选择外部受众</div>
            <div v-else class="flex flex-wrap gap-1.5">
              <Chip v-for="a in meta.audiences" :key="a" :on="cfg.audiences.includes(a)" :disabled="locked" @click="toggle(cfg.audiences, a)">{{ a }}</Chip>
            </div>
          </div>
          <div>
            <div class="lbl">粒度上限</div>
            <div class="seg" role="radiogroup" aria-label="粒度上限">
              <button
                v-for="g in meta.granularity"
                :key="g"
                type="button"
                role="radio"
                :aria-checked="cfg.granularity === g"
                :class="cfg.granularity === g && 'on'"
                :disabled="locked || g === '诊疗行为'"
                :title="g === '诊疗行为' ? '诊疗行为级仅分析监测区可见' : undefined"
                @click="cfg.granularity = g"
              >{{ g }}</button>
            </div>
            <div class="mt-1 text-[11px] text-ink-faint">发布区最细到病组;诊疗行为级仅分析监测区可见</div>
          </div>
          <div class="rounded-[10px] border border-dashed border-line-strong p-3">
            <div class="mb-2 text-[11px] text-ink-faint">模板预览 · {{ cfg.template }}</div>
            <PctBar v-if="cfg.template === '分位条'" :pct="62" :height="12" />
            <svg v-else viewBox="0 0 300 64" class="h-16 w-full" aria-hidden="true">
              <template v-if="cfg.template === '趋势线'">
                <polyline points="6,50 56,44 106,46 156,30 206,34 256,18 294,22" fill="none" stroke="var(--c-primary)" stroke-width="2" />
              </template>
              <template v-else-if="cfg.template === '排行条' || cfg.template === '分组柱'">
                <rect v-for="(w, i) in [260, 210, 180, 140, 96]" :key="i" :x="cfg.template === '排行条' ? 6 : 14 + i * 58" :y="cfg.template === '排行条' ? 4 + i * 12 : 60 - w / 4.6" :width="cfg.template === '排行条' ? w : 36" :height="cfg.template === '排行条' ? 8 : w / 4.6" rx="2" :fill="i === 1 ? 'var(--c-primary)' : 'var(--c-bar-neutral-a)'" />
              </template>
              <template v-else>
                <rect x="6" y="22" width="288" height="20" rx="3" fill="var(--c-divider)" />
                <rect x="6" y="22" width="120" height="20" rx="3" fill="var(--c-primary)" opacity=".85" />
                <rect x="126" y="22" width="90" height="20" fill="var(--c-bar-blue-lt-a)" />
              </template>
            </svg>
          </div>
          <div v-if="showTplWarn" class="rounded-lg border border-warning-line bg-warning-soft px-3 py-2 text-warning-ink">⚠ 排行条仅用于「具名PK与排行」档位;当前档位提交时将被拒绝,请改用分位条。</div>
        </div>
      </div>

      <!-- ============================================================ 4 预览提交 -->
      <div v-else class="flex flex-col gap-3.5" data-testid="step-4">
        <div class="flex flex-wrap items-center gap-2.5">
          <span class="font-semibold">以某机构身份预览</span>
          <div class="flex flex-wrap gap-1.5" data-testid="preview-orgs">
            <button
              v-for="(o, i) in orgs"
              :key="o"
              type="button"
              class="cursor-pointer rounded-lg border px-3 py-1"
              :class="orgSel === i ? 'border-primary bg-primary-solid text-white' : 'border-line bg-surface text-ink-sub hover:bg-hover'"
              :aria-pressed="orgSel === i"
              :data-org="o"
              @click="orgSel = i"
            >{{ o }}</button>
          </div>
          <span class="text-[11px] text-ink-faint">真实数据 · 2026年8月</span>
        </div>

        <div v-if="cfg.internal" class="max-w-[760px] rounded-lg bg-chip px-3 py-2.5 text-ink-sub" data-testid="pv-internal">
          该指标为“仅内部”:机构身份下不渲染,且不能加入发布包。以下为医保局内部视图。
        </div>

        <div class="max-w-[760px] rounded-[10px] border border-line px-[18px] py-4" data-testid="preview-card" :data-mode="pv?.mode">
          <div v-if="pvErr" class="py-6 text-center text-danger">{{ pvErr }}</div>
          <div v-else-if="!pv || pvLoading" class="py-6 text-center text-ink-faint">加载中…</div>
          <!-- 机构身份 · 正常：本院值 + 同级 P25/P50/P75 -->
          <template v-else-if="pv.mode === 'normal'">
            <div class="flex items-baseline justify-between">
              <span class="font-semibold text-ink">{{ pv.org }} 看到的内容</span>
              <span class="text-[11px] text-ink-faint">{{ pv.group }} · 同级 {{ pv.peerCount }} 家 · {{ pv.tierLabel }}</span>
            </div>
            <div class="my-3 flex gap-7">
              <div>
                <div class="text-[11px] text-ink-faint">本院{{ pv.indicator }}</div>
                <div class="text-[24px] font-semibold text-ink" data-testid="pv-value">{{ fmt(pv.value, 1) }} <span class="text-[13px] font-normal">{{ pv.unit }}</span></div>
              </div>
              <div>
                <div class="text-[11px] text-ink-faint">同级 P25 / P50 / P75</div>
                <div class="mt-1.5 text-[16px] text-ink" data-testid="pv-quantiles">{{ fmt(pv.p25, 1) }} / {{ fmt(pv.p50, 1) }} / {{ fmt(pv.p75, 1) }} {{ pv.unit }}</div>
              </div>
            </div>
            <PctBar :pct="pv.pct" high-is-bad />
            <div class="mt-2.5 text-ink-muted" data-testid="pv-pct">本院位于同级 P{{ pv.pct }}{{ (pv.pct ?? 0) >= 70 ? ',高于同级 P70,关注' : '' }}。{{ pv.note }}</div>
          </template>
          <!-- 机构身份 · 小样本抑制 -->
          <template v-else-if="pv.mode === 'suppressed'">
            <div class="flex items-baseline justify-between">
              <span class="font-semibold text-ink">{{ pv.org }} 看到的内容</span>
              <span class="text-[11px] text-ink-faint">{{ pv.group }} · 同级 {{ pv.peerCount }} 家</span>
            </div>
            <div class="my-3 flex gap-7">
              <div>
                <div class="text-[11px] text-ink-faint">本院{{ pv.indicator }}</div>
                <div class="text-[24px] font-semibold text-ink" data-testid="pv-value">{{ fmt(pv.value, 1) }} <span class="text-[13px] font-normal">{{ pv.unit }}</span></div>
              </div>
              <div>
                <div class="text-[11px] text-ink-faint">全市均值</div>
                <div class="mt-1.5 text-[16px] text-ink">{{ fmt(pv.cityMean, 1) }} {{ pv.unit }}</div>
              </div>
            </div>
            <div class="rounded-lg border border-warning-line bg-warning-soft px-3 py-2.5 text-warning-ink" data-testid="pv-note">{{ pv.note }}</div>
          </template>
          <!-- 召集人全量视图 / 仅内部时的内部视图 -->
          <template v-else>
            <div class="mb-2 flex items-baseline justify-between">
              <span class="font-semibold text-ink">{{ pv.mode === 'internal' ? '医保局内部视图(分析监测区)' : '召集人全量视图(分析监测区)' }}</span>
              <span v-if="(pvAll ?? pv).cityMean != null" class="text-[11px] text-ink-faint">全市均值 {{ fmt((pvAll ?? pv).cityMean, 1) }} {{ pv.unit }} · 取数 {{ fmt((pvAll ?? pv).totalCases) }} 例</span>
            </div>
            <div class="grid grid-cols-[1fr_60px_70px_70px_70px] gap-x-1.5 gap-y-1.5" data-testid="pv-all">
              <span class="text-ink-faint">同级组</span><span class="text-right text-ink-faint">机构数</span><span class="text-right text-ink-faint">P25</span><span class="text-right text-ink-faint">P50</span><span class="text-right text-ink-faint">P75</span>
              <template v-for="g in (pvAll ?? pv).groups ?? []" :key="g.name">
                <span class="text-ink">{{ g.name }}</span><span class="text-right">{{ g.n }}</span>
                <template v-if="g.suppressed"><span v-for="k in 3" :key="k" class="text-right text-ink-ghost">抑制</span></template>
                <template v-else><span class="text-right">{{ fmt(g.p25, 1) }}</span><span class="text-right">{{ fmt(g.p50, 1) }}</span><span class="text-right">{{ fmt(g.p75, 1) }}</span></template>
              </template>
            </div>
          </template>
        </div>

        <div v-if="result || locked" class="max-w-[760px] rounded-lg border border-success-line bg-success-soft px-3.5 py-3 text-success-ink" data-testid="submitted">
          ✓ 已提交上线审批(审批单 <b class="font-mono" data-testid="approval-no">{{ result?.approvalNo ?? draft?.approvalNo }}</b>)。{{ result?.detail ?? '召集人批准后上线。' }}
          <div v-if="result?.tierNote" class="mt-1">{{ result.tierNote }}</div>
        </div>
      </div>
    </div>
    <div v-else class="px-5 py-16 text-center text-[12px] text-ink-faint">加载草稿…</div>

    <div class="flex items-center justify-end gap-2 border-t border-divider px-5 py-3">
      <span v-if="step === 4 && !locked && check && !check.ok" class="mr-auto text-[12px] text-danger">公式校验未通过,请回到第 1 步修改</span>
      <Button v-if="step > 1" variant="outline" size="sm" @click="step--">上一步</Button>
      <Button v-if="step < 4" size="sm" data-testid="wiz-next" @click="step++">下一步</Button>
      <Button v-if="step === 4 && !locked" size="sm" :disabled="busy || !check?.ok" data-testid="wiz-submit" @click="submit">提交上线审批</Button>
      <Button v-if="locked" variant="outline" size="sm" @click="emit('back', true)">返回指标列表</Button>
    </div>
  </section>
</template>

<style scoped>
.lbl {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--c-text);
}
.fld {
  height: 28px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  background: var(--c-surface);
  padding: 0 8px;
  color: var(--c-text);
  outline: none;
}
textarea.fld {
  height: auto;
  padding: 6px 10px;
}
.fld:focus { border-color: var(--ring); }
.fld.num { width: 56px; text-align: center; }
.seg {
  display: inline-flex;
  overflow: hidden;
  border: 1px solid var(--c-border);
  border-radius: 6px;
}
.seg button {
  padding: 4px 13px;
  color: var(--c-text-sub);
  cursor: pointer;
}
.seg button + button { border-left: 1px solid var(--c-border); }
.seg button:hover:not(:disabled):not(.on) { background: var(--c-hover); }
.seg button.on { background: var(--c-primary-solid); color: white; }
.seg button:disabled:not(.on) { color: var(--c-text-ghost); cursor: not-allowed; }
</style>
