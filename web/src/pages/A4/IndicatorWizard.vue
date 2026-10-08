<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { runAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import type { A4Data, A4Group, A4Tier } from '@/mock/A4'
import { GROUP_NAME, GROUPS, TIER_LABEL } from './meta'
import { vPress } from '@/lib/a11y'

/**
 * 新建指标 wizard: 定义指标 → 设规则 → 绑呈现 → 预览提交.
 * Every field is validated here (required / length / special characters / duplicate name) and again
 * by the server, which issues the 审批编号. A submitted draft is cleared; the next opening starts fresh.
 */
const props = defineProps<{ wizard: A4Data['wizard']; existing: string[] }>()
const emit = defineEmits<{ submitted: [approvalNo: string] }>()
const open = defineModel<boolean>('open', { required: true })

/** same character rules as the server (Checks.NAME / Checks.EXPR) */
const NAME_RE = /^[\p{Script=Han}A-Za-z0-9][\p{Script=Han}A-Za-z0-9 ()\uFF08\uFF09·._/%+\-、,\uFF0C]*$/u
const EXPR_RE = /^[\p{Script=Han}A-Za-z0-9Σ][\p{Script=Han}A-Za-z0-9Σ ()\uFF08\uFF09·._/%+\-×÷*=≠<>≥≤、,\uFF0C]*$/u
const len = (s: string) => [...s].length

function blank() {
  return {
    name: '',
    group: '效' as A4Group,
    domain: '',
    freq: '月',
    numerator: '',
    denominator: '',
    filters: [] as string[],
    dims: [...props.wizard.defaultDims],
    tier: 'pct' as A4Tier,
    internalOnly: false,
    chart: props.wizard.charts[0] ?? '分位条',
    granularity: '病组',
    note: '',
  }
}
const form = reactive(blank())
const filterDraft = ref('')
const step = ref(1)
const reached = ref(1)
const org = ref(0)
const done = ref<string | null>(null)
const busy = ref(false)
const tried = ref(false)

// a submitted draft is gone: reopening starts a new indicator at step 1 (no second submit of the same 审批编号)
watch(open, v => {
  if (v && done.value) {
    Object.assign(form, blank())
    filterDraft.value = ''
    step.value = 1
    reached.value = 1
    done.value = null
    tried.value = false
  }
})
watch(() => form.internalOnly, on => {
  form.tier = on ? 'none' : 'pct'
})

function textErr(v: string, label: string, max: number, re: RegExp, min = 1): string | null {
  const s = v.trim()
  if (!s) return `请填写${label}`
  if (len(s) < min) return `${label}至少 ${min} 个字`
  if (len(s) > max) return `${label}不能超过 ${max} 个字`
  if (!re.test(s)) return `${label}含有不允许的特殊字符`
  return null
}

const errors = computed(() => {
  const e: Record<string, string | null> = {
    name: textErr(form.name, '指标名称', 30, NAME_RE, 2),
    domain: textErr(form.domain, '监测子域', 16, NAME_RE),
    numerator: textErr(form.numerator, '分子', 60, EXPR_RE),
    denominator: textErr(form.denominator, '分母', 60, EXPR_RE),
    dims: form.dims.length ? null : '请至少选择 1 个维度',
    note: len(form.note) > 200 ? '解读文字不能超过 200 个字' : /[<>]/.test(form.note) ? '解读文字含有不允许的特殊字符' : null,
  }
  if (!e.name && props.existing.includes(form.name.trim())) e.name = `已存在同名指标「${form.name.trim()}」`
  if (!e.numerator && !e.denominator && form.numerator.trim() === form.denominator.trim()) e.denominator = '分子与分母不能相同'
  return e
})
const step1Errors = computed(() => ['name', 'domain', 'numerator', 'denominator', 'dims'].map(k => errors.value[k]).filter(Boolean) as string[])
const stepOk = (n: number) => (n === 1 ? step1Errors.value.length === 0 : n === 3 ? !errors.value.note : true)

const filterErr = computed(() => {
  const s = filterDraft.value.trim()
  if (!s) return null
  if (len(s) > 30) return '每个条件不能超过 30 个字'
  if (!EXPR_RE.test(s)) return '条件含有不允许的特殊字符'
  if (form.filters.includes(s)) return '该条件已添加'
  if (form.filters.length >= 8) return '最多 8 个条件'
  return null
})
function addFilter() {
  const s = filterDraft.value.trim()
  if (!s || filterErr.value) return
  form.filters.push(s)
  filterDraft.value = ''
}
function toggleDim(d: string) {
  form.dims = form.dims.includes(d) ? form.dims.filter(x => x !== d) : [...form.dims, d]
}

const STEPS = ['定义指标', '设规则', '绑呈现', '预览提交']
const steps = computed(() =>
  STEPS.map((l, i) => {
    const n = i + 1
    return { n, l, on: n === step.value, done: n < reached.value && n !== step.value && stepOk(n), locked: n > reached.value }
  }),
)
function go(n: number) {
  if (n > reached.value) return
  step.value = n
}
function next() {
  tried.value = true
  if (!stepOk(step.value)) {
    say(step.value === 1 ? step1Errors.value[0]! : errors.value.note!)
    return
  }
  tried.value = false
  step.value = Math.min(4, step.value + 1)
  reached.value = Math.max(reached.value, step.value)
}
const showErr = (k: string) => (tried.value || (form as Record<string, unknown>)[k] !== '') && errors.value[k]

async function submit() {
  if (busy.value || done.value) return
  const bad = [1, 3].find(n => !stepOk(n))
  if (bad) {
    step.value = bad
    tried.value = true
    say('请先修正第 ' + bad + ' 步中的问题')
    return
  }
  busy.value = true
  const r = await runAction<{ result?: { approvalNo: string } }>('A4', 'submitIndicator', {
    name: form.name.trim(),
    group: form.group,
    domain: form.domain.trim(),
    freq: form.freq,
    numerator: form.numerator.trim(),
    denominator: form.denominator.trim(),
    filters: form.filters,
    dims: form.dims,
    tier: form.internalOnly ? 'none' : form.tier,
    internalOnly: form.internalOnly,
    chart: form.chart,
    granularity: form.granularity,
    note: form.note.trim(),
  })
  busy.value = false
  if (!r.ok) {
    say(r.error)
    return
  }
  const no = r.data?.result?.approvalNo ?? ''
  done.value = no
  say('已提交上线审批 · ' + no)
  emit('submitted', no)
}

const chipCls = (on: boolean) =>
  on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3'
const inputCls = (bad: unknown) =>
  cn('h-9 w-full rounded-lg border bg-white px-3 text-[13px] outline-none focus:border-brand max-xl:h-10', bad ? 'border-bad' : 'border-line-1')
const previewName = computed(() => form.name.trim() || props.wizard.name)
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      overlay-class="z-[80] bg-[rgba(11,21,38,.4)]"
      :show-close="false"
      class="z-[81] h-[min(760px,calc(100%-64px))] w-[min(1180px,calc(100%-64px))] grid-cols-[220px_minmax(0,1fr)_380px] gap-0 overflow-hidden max-xl:h-[min(760px,calc(100%-32px))] max-xl:w-[calc(100%-32px)] max-xl:grid-cols-1 lg:max-xl:grid-cols-[minmax(0,1fr)_320px] max-xl:content-start max-xl:overflow-y-auto rounded-2xl border-0 bg-white p-0 text-[13px] text-ink-1 shadow-[0_24px_64px_rgba(11,21,38,.3)]"
    >
      <!-- steps -->
      <div class="flex flex-col gap-1 border-r border-line-1 bg-surface-1 px-[18px] py-[22px] max-xl:col-span-full max-xl:grid max-xl:grid-cols-4 max-xl:gap-x-2 max-xl:border-r-0 max-xl:border-b max-xl:px-5 max-xl:pt-4 max-xl:pb-2">
        <DialogTitle class="mb-1 max-xl:col-span-2 max-xl:row-start-1 max-xl:self-center text-[15px] font-semibold">新建指标</DialogTitle>
        <DialogDescription class="mb-4 max-xl:col-span-4 max-xl:row-start-2 max-xl:mb-2 text-xs text-ink-4">{{ form.name.trim() || '未命名指标' }} · 草稿 {{ wizard.draftVersion }}</DialogDescription>
        <div
          v-for="st in steps"
          :key="st.n"
          v-press="!st.locked"
          :aria-disabled="st.locked || undefined"
          :class="cn('flex items-center gap-2.5 px-1.5 py-2 max-xl:min-h-11 max-xl:flex-col max-xl:justify-center max-xl:gap-1 max-xl:text-xs', st.locked ? 'cursor-not-allowed opacity-60' : 'cursor-pointer')"
          @click="go(st.n)"
        >
          <span
            :class="cn(
              'yb-num flex size-6 items-center justify-center rounded-full border-[1.5px] text-xs font-semibold',
              st.on ? 'border-brand bg-brand text-white' : st.done ? 'border-ok bg-ok text-white' : 'border-line-4 bg-white text-ink-5',
            )"
          >{{ st.done ? '✓' : st.n }}</span>
          <span :class="st.on ? 'font-semibold text-ink-1' : 'text-ink-4'">{{ st.l }}</span>
        </div>
        <div class="flex-1 max-xl:hidden" />
        <button type="button" class="cursor-pointer text-xs text-ink-4 max-xl:col-start-3 max-xl:col-end-5 max-xl:row-start-1 max-xl:min-h-11 max-xl:justify-self-end max-xl:whitespace-nowrap max-xl:text-right" @click="open = false">{{ done ? '关闭' : '关闭 · 草稿保留至本页关闭' }}</button>
      </div>

      <!-- step body -->
      <div class="flex flex-col gap-4 overflow-y-auto px-7 py-6 max-xl:overflow-visible max-xl:px-5">
        <template v-if="step === 1">
          <div class="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-3 max-md:grid-cols-1">
            <label class="flex flex-col gap-1.5">
              <span class="text-xs text-ink-4">指标名称 <span class="text-bad">*</span></span>
              <input v-model="form.name" :class="inputCls(showErr('name'))" :placeholder="'如:' + wizard.name" maxlength="40" aria-label="指标名称">
              <span v-if="showErr('name')" class="text-[11px] max-xl:text-xs text-bad-ink">{{ errors.name }}</span>
            </label>
            <label class="flex flex-col gap-1.5">
              <span class="text-xs text-ink-4">监测子域 <span class="text-bad">*</span></span>
              <input v-model="form.domain" :class="inputCls(showErr('domain'))" placeholder="如:住院效率" maxlength="20" aria-label="监测子域">
              <span v-if="showErr('domain')" class="text-[11px] max-xl:text-xs text-bad-ink">{{ errors.domain }}</span>
            </label>
          </div>
          <div class="flex flex-wrap gap-x-6 gap-y-3">
            <div>
              <div class="mb-1.5 text-xs text-ink-4">监测维度</div>
              <div class="flex gap-1.5">
                <button v-for="g in GROUPS" :key="g" type="button" :aria-pressed="form.group === g"
                  :class="cn('cursor-pointer rounded-lg border px-3 py-[5px] text-xs max-xl:min-h-10', chipCls(form.group === g))"
                  @click="form.group = g">{{ GROUP_NAME[g] }}</button>
              </div>
            </div>
            <div>
              <div class="mb-1.5 text-xs text-ink-4">更新频次</div>
              <div class="flex gap-1.5">
                <button v-for="f in wizard.freqs" :key="f" type="button" :aria-pressed="form.freq === f"
                  :class="cn('cursor-pointer rounded-lg border px-3 py-[5px] text-xs max-xl:min-h-10', chipCls(form.freq === f))"
                  @click="form.freq = f">{{ f }}</button>
              </div>
            </div>
          </div>
          <div>
            <div class="mb-2 text-xs text-ink-4">分子 / 分母 <span class="text-bad">*</span></div>
            <div class="flex flex-col items-center gap-2 rounded-xl bg-surface-1 p-[18px]">
              <input v-model="form.numerator" :class="cn(inputCls(showErr('numerator')), 'max-w-[420px] text-center')" placeholder="分子,如:Σ术前住院天数" maxlength="70" aria-label="分子">
              <span class="h-[1.5px] w-3/5 bg-ink-1" />
              <input v-model="form.denominator" :class="cn(inputCls(showErr('denominator')), 'max-w-[420px] text-center')" placeholder="分母,如:手术出院人次" maxlength="70" aria-label="分母">
            </div>
            <div v-if="showErr('numerator') || showErr('denominator')" class="mt-1 text-[11px] max-xl:text-xs text-bad-ink">{{ errors.numerator || errors.denominator }}</div>
          </div>
          <div>
            <div class="mb-2 text-xs text-ink-4">过滤条件(选填,最多 8 个)</div>
            <div class="flex flex-wrap items-center gap-1.5 text-xs">
              <span v-for="(f, i) in form.filters" :key="f" class="flex items-center gap-1 rounded-lg bg-surface-3 py-[3px] pr-1 pl-2.5">
                {{ f }}
                <button type="button" class="flex size-6 cursor-pointer items-center justify-center rounded text-ink-4 hover:bg-line-2" :aria-label="'删除条件 ' + f" @click="form.filters.splice(i, 1)">×</button>
              </span>
              <input v-model="filterDraft" class="h-8 w-[180px] rounded-lg border border-dashed border-ink-6 px-2.5 text-xs outline-none focus:border-brand" placeholder="如:手术标志 = 1" maxlength="40" aria-label="新增过滤条件" @keydown.enter.prevent="addFilter">
              <button type="button" :disabled="!filterDraft.trim() || !!filterErr" class="h-8 cursor-pointer rounded-lg border border-line-1 px-2.5 text-xs text-brand disabled:cursor-not-allowed disabled:text-ink-5" @click="addFilter">+ 条件</button>
            </div>
            <div v-if="filterErr" class="mt-1 text-[11px] max-xl:text-xs text-bad-ink">{{ filterErr }}</div>
          </div>
          <div>
            <div class="mb-2 text-xs text-ink-4">维度 <span class="text-bad">*</span></div>
            <div class="flex flex-wrap gap-1.5 text-xs">
              <button v-for="d in wizard.dims" :key="d" type="button" :aria-pressed="form.dims.includes(d)"
                :class="cn('cursor-pointer rounded-lg border px-3 py-[5px] max-xl:min-h-10', chipCls(form.dims.includes(d)))"
                @click="toggleDim(d)">{{ form.dims.includes(d) ? '✓ ' : '' }}{{ d }}</button>
            </div>
          </div>
          <div v-if="step1Errors.length === 0" class="rounded-[10px] bg-ok-soft px-3.5 py-2.5 text-xs text-ok-ink">✓ 口径校验通过 · {{ form.numerator.trim() }} ÷ {{ form.denominator.trim() }} · 维度 {{ form.dims.join('、') }}</div>
          <div v-else class="rounded-[10px] bg-surface-2 px-3.5 py-2.5 text-xs text-ink-3">口径校验:{{ step1Errors.length }} 项待完善 — {{ step1Errors[0] }}</div>
        </template>

        <template v-if="step === 2">
          <div>
            <div class="mb-2 text-xs text-ink-4">对标档位</div>
            <div class="flex flex-col gap-2">
              <div
                v-for="t in wizard.tiers"
                :key="t.id"
                v-press="!form.internalOnly"
                :aria-disabled="form.internalOnly || undefined"
                :class="cn(
                  'rounded-[10px] border-[1.5px] px-3.5 py-3',
                  form.internalOnly ? 'cursor-not-allowed border-line-1 bg-surface-1 opacity-50' : 'cursor-pointer',
                  !form.internalOnly && (t.id === form.tier ? 'border-brand bg-brand-soft' : 'border-line-1 bg-white'),
                )"
                @click="!form.internalOnly && (form.tier = t.id)"
              >
                <div class="flex justify-between"><span class="font-semibold">{{ t.name }}</span><span class="text-[11px] max-xl:text-xs text-ink-4">{{ t.tag }}</span></div>
                <div class="text-xs text-ink-3">{{ t.desc }}</div>
              </div>
            </div>
            <div v-if="form.internalOnly" class="mt-2 rounded-[10px] bg-line-3 px-3 py-2.5 text-xs text-ink-3">仅内部指标不可配置对标档位,也不可加入发布包。</div>
            <div v-else-if="form.tier !== 'pct'" class="mt-2 rounded-[10px] bg-warn-soft px-3 py-2.5 text-xs text-warn-ink">非默认档位需召集人审批,通过前按匿名分位发布。</div>
          </div>
          <div class="flex items-center justify-between rounded-[10px] border border-line-1 p-3.5">
            <div>
              <div class="font-semibold">仅内部</div>
              <div class="text-xs text-ink-4">病例级、个人级指标须开启;开启后不可发布</div>
            </div>
            <Switch v-model="form.internalOnly" size="lg" aria-label="仅内部" />
          </div>
          <div class="text-xs text-ink-3">小样本抑制:同级 &lt; 5 家 或 病例 &lt; 30 例不输出分位 · 预警:&gt; P75 且环比 &gt; 10%</div>
        </template>

        <template v-if="step === 3">
          <div>
            <div class="mb-2 text-xs text-ink-4">图表模板</div>
            <div class="grid grid-cols-4 gap-2 text-xs max-md:grid-cols-2">
              <button v-for="c in wizard.charts" :key="c" type="button" :aria-pressed="form.chart === c"
                :class="cn('cursor-pointer rounded-[10px] p-3 text-center', form.chart === c ? 'border-[1.5px] border-brand bg-brand-soft font-medium text-brand' : 'border border-line-1 bg-white')"
                @click="form.chart = c">{{ c }}</button>
            </div>
          </div>
          <label class="flex flex-col gap-1.5">
            <span class="text-xs text-ink-4">解读文字(选填,200 字以内)</span>
            <textarea v-model="form.note" rows="3" maxlength="220" :class="cn('rounded-[10px] border p-3 text-[13px] leading-[1.7] outline-none focus:border-brand', errors.note ? 'border-bad' : 'border-line-1')" placeholder="如:术前等待时间越短,床位周转与费用控制通常越好。本指标为同级比较,不作为考核指标。" aria-label="解读文字" />
            <span class="flex justify-between text-[11px] max-xl:text-xs"><span class="text-bad-ink">{{ errors.note ?? '' }}</span><span class="text-ink-5">{{ [...form.note].length }}/200</span></span>
          </label>
          <div>
            <div class="mb-2 text-xs text-ink-4">粒度上限</div>
            <div class="flex w-max max-w-full flex-wrap overflow-hidden rounded-lg border border-line-1 text-xs" role="radiogroup" aria-label="粒度上限">
              <button v-for="g in wizard.granularities" :key="g" type="button" role="radio" :aria-checked="form.granularity === g"
                :class="cn('cursor-pointer px-3.5 py-1.5 max-xl:min-h-10', form.granularity === g ? 'bg-ink-1 text-white' : 'bg-white')"
                @click="form.granularity = g">{{ g }}</button>
              <span class="px-3.5 py-1.5 text-ink-6 max-xl:flex max-xl:min-h-10 max-xl:items-center" title="诊疗行为粒度仅在受控分析环境可用">诊疗行为</span>
            </div>
          </div>
        </template>

        <template v-if="step === 4">
          <div class="text-sm font-semibold">提交前确认</div>
          <div class="grid grid-cols-[100px_minmax(0,1fr)] gap-x-2.5 gap-y-2 text-[13px]">
            <span class="text-ink-4">名称</span><span>{{ form.name.trim() }}</span>
            <span class="text-ink-4">分组</span><span>{{ GROUP_NAME[form.group] }} · {{ form.domain.trim() }} · {{ form.internalOnly ? '仅内部' : '地方增选' }}</span>
            <span class="text-ink-4">公式</span><span>{{ form.numerator.trim() }} ÷ {{ form.denominator.trim() }}</span>
            <span class="text-ink-4">过滤条件</span><span>{{ form.filters.length ? form.filters.join(';') : '无' }}</span>
            <span class="text-ink-4">维度 · 频次</span><span>{{ form.dims.join('、') }} · {{ form.freq }}</span>
            <span class="text-ink-4">对标档位</span><span>{{ form.internalOnly ? '—(仅内部,不可配置)' : TIER_LABEL[form.tier] }}</span>
            <span class="text-ink-4">呈现</span><span>{{ form.chart }} · 粒度上限 {{ form.granularity }}</span>
            <span class="text-ink-4">审批人</span><span>{{ wizard.approver }}</span>
            <span class="text-ink-4">审批编号</span><span class="font-mono">{{ done ?? wizard.approvalNo + '(提交时由系统分配)' }}</span>
          </div>
          <div v-if="done" class="rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs text-ok-ink">
            ✓ 已提交上线审批 {{ done }} · {{ form.internalOnly ? '批准后仅在分析监测区内部使用,不进入任何发布包' : '批准后出现在全景图“' + form.group + '”与机构门户核心指标' }}
          </div>
        </template>

        <div class="flex-1" />
        <!-- Pad:整窗滚动时上一步 / 下一步吸底,始终可点 -->
        <div class="flex justify-end gap-2 max-xl:sticky max-xl:bottom-0 max-xl:z-[1] max-xl:-mx-5 max-xl:-mb-6 max-xl:border-t max-xl:border-line-2 max-xl:bg-white max-xl:px-5 max-xl:py-3">
          <Button variant="outline" class="font-normal max-xl:h-10" :disabled="step === 1" @click="step = Math.max(1, step - 1)">上一步</Button>
          <Button v-if="step < 4" class="px-[18px] max-xl:h-10" @click="next">下一步</Button>
          <Button v-else-if="!done" class="px-[18px] max-xl:h-10" :disabled="busy" @click="submit">提交上线审批</Button>
        </div>
      </div>

      <!-- live preview -->
      <div class="flex flex-col gap-3 border-l border-line-1 bg-surface-1 px-5 py-[22px] max-lg:border-t max-lg:border-l-0">
        <div class="text-xs font-semibold text-ink-4">实时预览 · 以某身份查看 <span class="font-normal text-ink-5">(示例数据)</span></div>
        <div class="flex flex-col gap-1.5">
          <button type="button"
            v-for="(o, i) in wizard.previewOrgs"
            :key="o"
            :class="cn('cursor-pointer rounded-lg border px-2.5 py-1.5 text-xs whitespace-nowrap max-xl:min-h-10', chipCls(i === org))"
            @click="org = i"
          >{{ o }}</button>
        </div>
        <div class="rounded-xl border border-line-1 bg-white p-4">
          <template v-if="org === 0 && !form.internalOnly">
            <div class="text-xs text-ink-4">本院{{ previewName }}</div>
            <div class="yb-num text-[28px] font-semibold">2.8</div>
            <div class="relative mt-2.5 h-3 rounded-[3px] bg-line-2">
              <div class="absolute inset-y-0 left-1/4 w-1/2 bg-[color-mix(in_srgb,var(--brand)_14%,white)]" />
              <div class="absolute -top-0.5 -bottom-0.5 left-1/2 border-l-[1.5px] border-ink-4" />
              <div class="absolute -top-1 left-[71%] -ml-0.5 h-5 w-1 rounded-[2px] bg-warn" />
            </div>
            <div class="mt-1 flex justify-between text-[10px] text-ink-5 max-xl:text-xs"><span>P0</span><span>P25</span><span>P50</span><span>P75</span><span>P100</span></div>
            <div class="mt-2 text-xs text-ink-3">{{ form.tier === 'named' ? '同级第 4 名 · 具名排行(审批通过后)' : form.tier === 'anon' ? '同级 P71 · 他院以匿名编号显示' : '同级 P71 · 不显示他院名称与数值' }}</div>
          </template>
          <template v-if="org === 1 && !form.internalOnly">
            <div class="text-xs text-ink-4">本院{{ previewName }}</div>
            <div class="yb-num text-[28px] font-semibold">2.1</div>
            <div class="mt-2.5 rounded-lg bg-warn-soft p-2.5 text-xs text-warn-ink">同级仅 4 家,低于阈值:不输出分位,仅显示全市均值 2.4</div>
          </template>
          <template v-if="org === 2">
            <div class="mb-1.5 text-xs text-ink-4">召集人全量 · 分析监测区</div>
            <div class="grid grid-cols-[1fr_auto_auto] gap-x-3 gap-y-1.5 text-xs">
              <span>市三级</span><span>6 家</span><span>P50 2.3</span>
              <span>县三级</span><span>4 家</span><span class="text-ink-5">抑制</span>
              <span>二级甲等</span><span>9 家</span><span>P50 2.2</span>
              <span>一级</span><span>22 家</span><span>P50 1.5</span>
            </div>
          </template>
          <div v-if="form.internalOnly && org !== 2" class="rounded-lg bg-line-3 p-3.5 text-center text-xs text-ink-4">仅内部指标 · 该身份下不渲染</div>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
