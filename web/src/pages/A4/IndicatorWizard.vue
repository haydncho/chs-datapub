<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { cn } from '@/lib/utils'
import type { A4Data, A4Tier } from '@/mock/A4'
import { vPress } from '@/lib/a11y'

const props = defineProps<{ wizard: A4Data['wizard'] }>()
const open = defineModel<boolean>('open', { required: true })

const step = ref(1)
const tier = ref<A4Tier>('pct')
const internalOnly = ref(false)
const org = ref(0)
const done = ref(false)

// every (re)open starts at step 1, like the prototype's openWiz
watch(open, v => {
  if (v) {
    step.value = 1
    done.value = false
  }
})

const STEPS = ['定义指标', '设规则', '绑呈现', '预览提交']
const steps = computed(() =>
  STEPS.map((l, i) => {
    const n = i + 1
    return { n, l, on: n === step.value, done: n < step.value }
  }),
)

function submit() {
  done.value = true
  sendAction('A4', 'submitIndicator', {
    name: props.wizard.name,
    tier: tier.value,
    internalOnly: internalOnly.value,
    approvalNo: props.wizard.approvalNo,
  })
  say('已提交上线审批 · ' + props.wizard.approvalNo)
}

const chipCls = (on: boolean) =>
  on ? 'border-brand-line bg-brand-soft text-brand' : 'border-line-1 bg-white text-ink-3'
</script>

<template>
  <Dialog v-model:open="open">
    <DialogContent
      overlay-class="z-[80] bg-[rgba(11,21,38,.4)]"
      :show-close="false"
      class="z-[81] h-[min(720px,calc(100%-64px))] w-[min(1180px,calc(100%-64px))] grid-cols-[220px_minmax(0,1fr)_380px] gap-0 overflow-hidden max-xl:h-[min(720px,calc(100%-32px))] max-xl:w-[calc(100%-32px)] max-xl:grid-cols-1 max-xl:content-start max-xl:overflow-y-auto rounded-2xl border-0 bg-white p-0 text-[13px] text-ink-1 shadow-[0_24px_64px_rgba(11,21,38,.3)]"
    >
      <!-- steps -->
      <div class="flex flex-col gap-1 border-r border-line-1 bg-surface-1 px-[18px] py-[22px] max-xl:grid max-xl:grid-cols-4 max-xl:gap-x-2 max-xl:border-r-0 max-xl:border-b">
        <DialogTitle class="mb-1 max-xl:col-span-4 text-[15px] font-semibold">新建指标</DialogTitle>
        <DialogDescription class="mb-4 max-xl:col-span-4 max-xl:mb-2 text-xs text-ink-4">{{ wizard.name }} · 草稿 {{ wizard.draftVersion }}</DialogDescription>
        <div v-press v-for="st in steps" :key="st.n" class="flex cursor-pointer items-center gap-2.5 px-1.5 py-2 max-xl:min-h-11 max-xl:flex-col max-xl:justify-center max-xl:gap-1 max-xl:text-xs" @click="step = st.n">
          <span
            :class="cn(
              'yb-num flex size-6 items-center justify-center rounded-full border-[1.5px] text-xs font-semibold',
              st.on ? 'border-brand bg-brand text-white' : st.done ? 'border-ok bg-ok text-white' : 'border-line-4 bg-white text-ink-5',
            )"
          >{{ st.done ? '✓' : st.n }}</span>
          <span :class="st.on ? 'font-semibold text-ink-1' : 'text-ink-4'">{{ st.l }}</span>
        </div>
        <div class="flex-1 max-xl:hidden" />
        <button type="button" class="cursor-pointer text-xs text-ink-4 max-xl:col-span-4 max-xl:min-h-11 max-xl:text-left" @click="open = false">关闭 · 草稿已自动保存</button>
      </div>

      <!-- step body -->
      <div class="flex flex-col gap-[18px] overflow-y-auto px-7 py-6 max-xl:overflow-visible">
        <template v-if="step === 1">
          <div>
            <div class="mb-2 text-xs text-ink-4">分子 / 分母</div>
            <div class="flex flex-col items-center gap-2 rounded-xl bg-surface-1 p-[18px]">
              <span class="rounded-lg border border-brand-line bg-white px-3.5 py-1.5 text-brand">Σ 术前住院天数</span>
              <span class="h-[1.5px] w-3/5 bg-ink-1" />
              <span class="rounded-lg border border-brand-line bg-white px-3.5 py-1.5 text-brand">手术出院人次</span>
            </div>
          </div>
          <div>
            <div class="mb-2 text-xs text-ink-4">过滤条件</div>
            <div class="flex flex-wrap gap-1.5 text-xs">
              <span class="rounded-lg bg-surface-3 px-2.5 py-[5px]">手术标志 = 1</span>
              <span class="rounded-lg bg-surface-3 px-2.5 py-[5px]">离院方式 ≠ 死亡</span>
              <span class="rounded-lg border border-dashed border-ink-6 px-2.5 py-[5px] text-ink-4">+ 条件</span>
            </div>
          </div>
          <div>
            <div class="mb-2 text-xs text-ink-4">维度</div>
            <div class="flex flex-wrap gap-1.5 text-xs">
              <span v-for="d in ['机构', '等级', '病组', '时间']" :key="d" class="rounded-lg bg-brand-soft px-3 py-[5px] text-brand">{{ d }}</span>
              <span v-for="d in ['县区', '险种']" :key="d" class="rounded-lg border border-line-1 px-3 py-[5px]">{{ d }}</span>
            </div>
          </div>
          <div class="rounded-[10px] bg-ok-soft px-3.5 py-2.5 text-xs text-ok-ink">✓ 口径校验通过 · 结算清单 v2026.1 · 预计取数 18,420 例</div>
        </template>

        <template v-if="step === 2">
          <div>
            <div class="mb-2 text-xs text-ink-4">对标档位</div>
            <div class="flex flex-col gap-2">
              <div v-press
                v-for="t in wizard.tiers"
                :key="t.id"
                :class="cn(
                  'cursor-pointer rounded-[10px] border-[1.5px] px-3.5 py-3',
                  t.id === tier ? 'border-brand bg-brand-soft' : 'border-line-1 bg-white',
                )"
                @click="tier = t.id"
              >
                <div class="flex justify-between"><span class="font-semibold">{{ t.name }}</span><span class="text-[11px] text-ink-4">{{ t.tag }}</span></div>
                <div class="text-xs text-ink-3">{{ t.desc }}</div>
              </div>
            </div>
            <div v-if="tier !== 'pct'" class="mt-2 rounded-[10px] bg-warn-soft px-3 py-2.5 text-xs text-warn-ink">非默认档位需召集人审批,通过前按匿名分位发布。</div>
          </div>
          <div class="flex items-center justify-between rounded-[10px] border border-line-1 p-3.5">
            <div>
              <div class="font-semibold">仅内部</div>
              <div class="text-xs text-ink-4">病例级、个人级指标须开启;开启后不可发布</div>
            </div>
            <Switch v-model="internalOnly" size="lg" aria-label="仅内部" />
          </div>
          <div class="text-xs text-ink-3">小样本抑制:同级 &lt; 5 家 或 病例 &lt; 30 例不输出分位 · 预警:&gt; P75 且环比 &gt; 10%</div>
        </template>

        <template v-if="step === 3">
          <div>
            <div class="mb-2 text-xs text-ink-4">图表模板</div>
            <div class="grid grid-cols-4 gap-2 text-xs">
              <span class="rounded-[10px] border-[1.5px] border-brand bg-brand-soft p-3 text-center font-medium text-brand">分位条</span>
              <span v-for="c in ['趋势线', '分组柱', '散点']" :key="c" class="rounded-[10px] border border-line-1 p-3 text-center">{{ c }}</span>
            </div>
          </div>
          <div>
            <div class="mb-2 text-xs text-ink-4">解读文字</div>
            <div class="rounded-[10px] border border-line-1 p-3 text-[13px] leading-[1.7]">术前等待时间越短,床位周转与费用控制通常越好。本指标为同级比较,不作为考核指标。</div>
          </div>
          <div>
            <div class="mb-2 text-xs text-ink-4">粒度上限</div>
            <div class="flex w-max overflow-hidden rounded-lg border border-line-1 text-xs">
              <span class="px-3.5 py-1.5">统筹区</span>
              <span class="px-3.5 py-1.5">县区</span>
              <span class="px-3.5 py-1.5">机构</span>
              <span class="bg-ink-1 px-3.5 py-1.5 text-white">病组</span>
              <span class="px-3.5 py-1.5 text-ink-6">诊疗行为</span>
            </div>
          </div>
        </template>

        <template v-if="step === 4">
          <div class="text-sm font-semibold">提交前确认</div>
          <div class="grid grid-cols-[100px_1fr] gap-2.5 text-[13px]">
            <span class="text-ink-4">名称</span><span>{{ wizard.name }}</span>
            <span class="text-ink-4">分组</span><span>{{ wizard.group }}</span>
            <span class="text-ink-4">审批人</span><span>{{ wizard.approver }}</span>
          </div>
          <div v-if="done" class="rounded-[10px] bg-ok-soft px-3.5 py-3 text-xs text-ok-ink">
            ✓ 已提交上线审批 {{ wizard.approvalNo }} · 批准后出现在全息图“效”与机构门户核心指标
          </div>
        </template>

        <div class="flex-1" />
        <div class="flex justify-end gap-2">
          <Button variant="outline" class="font-normal max-xl:h-10" @click="step = Math.max(1, step - 1)">上一步</Button>
          <Button v-if="step < 4" class="px-[18px] max-xl:h-10" @click="step = Math.min(4, step + 1)">下一步</Button>
          <Button v-else-if="!done" class="px-[18px] max-xl:h-10" @click="submit">提交上线审批</Button>
        </div>
      </div>

      <!-- live preview -->
      <div class="flex flex-col gap-3 border-l border-line-1 bg-surface-1 px-5 py-[22px] max-xl:border-t max-xl:border-l-0">
        <div class="text-xs font-semibold text-ink-4">实时预览 · 以某身份查看</div>
        <div class="flex flex-col gap-1.5">
          <button type="button"
            v-for="(o, i) in wizard.previewOrgs"
            :key="o"
            :class="cn('cursor-pointer rounded-lg border px-2.5 py-1.5 text-xs whitespace-nowrap max-xl:min-h-10', chipCls(i === org))"
            @click="org = i"
          >{{ o }}</button>
        </div>
        <div class="rounded-xl border border-line-1 bg-white p-4">
          <template v-if="org === 0 && !internalOnly">
            <div class="text-xs text-ink-4">本院术前平均住院日</div>
            <div class="yb-num text-[34px] font-semibold">2.8<span class="text-sm text-ink-4"> 天</span></div>
            <div class="relative mt-2.5 h-3 rounded-[3px] bg-line-2">
              <div class="absolute inset-y-0 left-1/4 w-1/2 bg-[color-mix(in_srgb,var(--brand)_14%,white)]" />
              <div class="absolute -top-0.5 -bottom-0.5 left-1/2 border-l-[1.5px] border-ink-4" />
              <div class="absolute -top-1 left-[71%] -ml-0.5 h-5 w-1 rounded-[2px] bg-warn" />
            </div>
            <div class="mt-1 flex justify-between text-[10px] text-ink-5"><span>P0</span><span>P25</span><span>P50</span><span>P75</span><span>P100</span></div>
            <div class="mt-2 text-xs text-ink-3">同级 P71 · 不显示他院名称与数值</div>
          </template>
          <template v-if="org === 1 && !internalOnly">
            <div class="text-xs text-ink-4">本院术前平均住院日</div>
            <div class="yb-num text-[34px] font-semibold">2.1<span class="text-sm text-ink-4"> 天</span></div>
            <div class="mt-2.5 rounded-lg bg-warn-soft p-2.5 text-xs text-warn-ink">同级仅 4 家,低于阈值:不输出分位,仅显示全市均值 2.4 天</div>
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
          <div v-if="internalOnly && org !== 2" class="rounded-lg bg-line-3 p-3.5 text-center text-xs text-ink-4">仅内部指标 · 该身份下不渲染</div>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>
