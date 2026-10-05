<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { Palette, SlidersHorizontal, MonitorPlay, Stamp, Undo2, RotateCcw, Save, CircleDot, CircleCheck } from '@lucide/vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Switch } from '@/components/ui/switch'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription,
  AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import {
  appearance, applyAppearance, publishAppearance, resetAppearance, normalizeAppearance, brandOf,
  DEFAULT_APPEARANCE, type Appearance,
} from '@/app/appearance'
import { A15_SEED, type A15Preset } from '@/mock/A15'
import Segmented from './A15/Segmented.vue'
import SettingRow from './A15/SettingRow.vue'
import SettingSection from './A15/SettingSection.vue'
import ThemeColorField from './A15/ThemeColorField.vue'
import PresetCards from './A15/PresetCards.vue'
import AppearancePreview from './A15/AppearancePreview.vue'

const data = usePageData('A15', A15_SEED)

/** 工作副本——只有"保存并发布"才会对全平台生效 */
const draft = reactive<Appearance>(normalizeAppearance(appearance))
const brand = computed(() => brandOf(draft))
const keyOf = (a: Appearance) => JSON.stringify(normalizeAppearance(a))
const dirty = computed(() => keyOf(draft) !== keyOf(appearance))

// 服务端同步 / 其它标签页更新了已发布外观:没有未保存更改时,草稿跟随
watch(() => keyOf(appearance), (_n, old) => {
  if (keyOf(draft) === old) Object.assign(draft, normalizeAppearance(appearance))
})

/** 自定义色输入不合格(格式错误 / 对比度不足)→ 禁止保存 */
const colorInvalid = ref(false)
const canSave = computed(() => dirty.value && !colorInvalid.value)

/* ───────── 分区导航 ───────── */
const SECTIONS = [
  { id: 'sec-theme', label: '平台主题', icon: Palette },
  { id: 'sec-ui', label: '界面与密度', icon: SlidersHorizontal },
  { id: 'sec-screen', label: '全息图大屏', icon: MonitorPlay },
  { id: 'sec-brand', label: '品牌与水印', icon: Stamp },
]
const active = ref(SECTIONS[0]!.id)
let lock = 0

function go(id: string) {
  active.value = id
  lock = Date.now() + 700
  const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches || document.documentElement.dataset.motion === '0'
  document.getElementById(id)?.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' })
}
function onScroll() {
  if (Date.now() < lock) return
  const bottom = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4
  if (bottom) { active.value = SECTIONS[SECTIONS.length - 1]!.id; return }
  let cur = SECTIONS[0]!.id
  for (const s of SECTIONS) {
    const el = document.getElementById(s.id)
    if (el && el.getBoundingClientRect().top <= 150) cur = s.id
  }
  active.value = cur
}

/* ───────── 预设方案 ───────── */
function applyPreset(p: A15Preset) {
  Object.assign(draft, p.patch, { custom: '' })
  say(`已套用预设「${p.name}」· 点击"保存并发布"后生效`)
}
function isActive(p: A15Preset) {
  const d = draft as unknown as Record<string, unknown>
  return !draft.custom && Object.entries(p.patch).every(([k, v]) => d[k] === v)
}

/* ───────── 在真实页面上试用 ───────── */
const tryLive = ref(false)
watch([tryLive, () => keyOf(draft)], () => {
  applyAppearance(tryLive.value ? normalizeAppearance(draft) : appearance)
})

/* ───────── 保存 / 放弃 / 恢复默认 ───────── */
function save() {
  if (!canSave.value) return
  const next = normalizeAppearance(draft)
  publishAppearance(next)
  sendAction('A15', 'publishAppearance', next)
  say('外观已发布 · 已应用到全部页面')
}
function discard() {
  Object.assign(draft, normalizeAppearance(appearance))
  say('已放弃未保存的更改')
}
const confirmReset = ref(false)
function doReset() {
  resetAppearance()
  Object.assign(draft, DEFAULT_APPEARANCE)
  sendAction('A15', 'resetAppearance', {})
  say('已恢复默认外观')
}

/* ───────── 离开保护 ───────── */
const leaveAsk = ref(false)
let leaveDone: ((ok: boolean) => void) | null = null
onBeforeRouteLeave(() => {
  if (!dirty.value) return true
  leaveAsk.value = true
  return new Promise<boolean>(r => { leaveDone = r })
})
function answerLeave(ok: boolean) {
  leaveAsk.value = false
  leaveDone?.(ok)
  leaveDone = null
}
/** Esc 等方式关闭 = 继续编辑;延后一拍,让"放弃更改并离开"按钮的点击先处理 */
function onLeaveOpen(v: boolean) {
  if (!v) window.setTimeout(() => { if (leaveDone) answerLeave(false) })
}
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) { e.preventDefault(); e.returnValue = '' }
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('beforeunload', beforeUnload)
  onScroll()
})
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('beforeunload', beforeUnload)
  applyAppearance(appearance) // 试用中未保存 → 回滚到已发布外观
})

function rot(dir: 1 | -1) {
  const { min, max, step } = data.value.rotation
  draft.rot = Math.min(max, Math.max(min, draft.rot + dir * step))
}
const STEP_BTN = 'flex size-7 cursor-pointer items-center justify-center rounded-md border border-line-1 bg-white hover:bg-surface-1'

const custom = computed({ get: () => draft.custom ?? '', set: v => { draft.custom = v } })
const menu = computed({ get: () => draft.menu ?? 0, set: v => { draft.menu = v } })
const zebra = computed({ get: () => draft.zebra ?? 0, set: v => { draft.zebra = v } })

function logoUpload() {
  say('演示环境暂不支持上传,沿用默认机构标识')
}
</script>

<template>
  <PageSection label="A15 外观配置">
    <PageHeader title="外观配置" subtitle="平台主题 · 界面密度 · 全息图大屏 · 品牌标识 · 保存并发布后对全部用户生效" />

    <!-- 吸顶操作条 -->
    <div
      class="sticky top-(--sticky-top) z-20 -mx-2 flex items-center gap-3 rounded-[10px] border border-line-1 bg-white/95 px-4 py-2.5 shadow-sm backdrop-blur"
      role="region"
      aria-label="外观操作条"
    >
      <div class="flex min-w-0 flex-1 items-center gap-2 text-[13px]" role="status" aria-live="polite">
        <template v-if="colorInvalid">
          <CircleDot class="size-4 shrink-0 text-bad" />
          <span class="truncate font-medium text-bad-ink">自定义主题色不可用,无法保存</span>
        </template>
        <template v-else-if="dirty">
          <CircleDot class="size-4 shrink-0 text-warn" />
          <span class="truncate font-medium text-warn-ink">有未保存的更改</span>
        </template>
        <template v-else>
          <CircleCheck class="size-4 shrink-0 text-ok" />
          <span class="truncate text-ink-4">已是当前发布的外观</span>
        </template>
      </div>
      <Button variant="outline" class="gap-1.5 px-3.5 font-normal" @click="confirmReset = true"><RotateCcw class="size-3.5" />恢复默认</Button>
      <Button variant="outline" class="gap-1.5 px-3.5 font-normal" :disabled="!dirty" @click="discard"><Undo2 class="size-3.5" />放弃更改</Button>
      <Button class="gap-1.5" :disabled="!canSave" @click="save"><Save class="size-3.5" />保存并发布</Button>
    </div>

    <div class="grid grid-cols-[140px_minmax(0,1fr)_460px] items-start gap-5">
      <!-- 分区导航 -->
      <nav aria-label="设置分区" class="sticky top-[136px] flex flex-col gap-0.5">
        <button
          v-for="s in SECTIONS"
          :key="s.id"
          type="button"
          class="flex cursor-pointer items-center gap-2 rounded-lg px-2.5 py-2 text-left text-[13px]"
          :class="active === s.id ? 'bg-brand-soft font-semibold text-brand' : 'text-ink-3 hover:bg-surface-3'"
          :aria-current="active === s.id ? 'true' : undefined"
          @click="go(s.id)"
        >
          <component :is="s.icon" class="size-4 shrink-0" />{{ s.label }}
        </button>
      </nav>

      <!-- 设置分区 -->
      <div class="flex min-w-0 flex-col gap-4">
        <SettingSection id="sec-theme" title="平台主题" desc="先选一套预设方案快速起步,再微调主题色。">
          <SettingRow title="预设方案" desc="一键套用主题色 / 密度 / 圆角 / 卡片样式 / 大屏配色" stack>
            <PresetCards :presets="data.presets" :palettes="data.palettes" :draft="draft" :is-active="isActive" @apply="applyPreset" />
          </SettingRow>
          <SettingRow title="主题色" desc="预设色或自定义;主色上叠白色文字,对比度须 ≥ 4.5:1" stack>
            <ThemeColorField v-model:c="draft.c" v-model:custom="custom" v-model:invalid="colorInvalid" :names="data.themeNames" />
          </SettingRow>
        </SettingSection>

        <SettingSection id="sec-ui" title="界面与密度" desc="影响全平台的间距、圆角、字号、卡片与表格观感。">
          <SettingRow title="界面密度" desc="卡片内边距、页面间距、表格行高">
            <Segmented v-model="draft.dens" :options="data.density" label="界面密度" />
          </SettingRow>
          <SettingRow title="圆角" desc="卡片、按钮、输入框的圆角大小">
            <Segmented v-model="draft.rad" :options="data.radius" label="圆角" />
          </SettingRow>
          <SettingRow title="正文字号" desc="整体界面的基础字号">
            <Segmented v-model="draft.font" :options="data.fontSize" label="正文字号" />
          </SettingRow>
          <SettingRow title="卡片样式" desc="描边:清晰边线;渐变:柔和底色;投影:悬浮感">
            <Segmented v-model="draft.card" :options="data.cardStyle" label="卡片样式" />
          </SettingRow>
          <SettingRow title="菜单风格" desc="标准或紧凑的菜单项间距与字号">
            <Segmented v-model="menu" :options="data.menuStyle" label="菜单风格" />
          </SettingRow>
          <SettingRow title="表格斑马纹" desc="数据表格隔行着色,便于横向阅读">
            <Segmented v-model="zebra" :options="data.zebra" label="表格斑马纹" />
          </SettingRow>
          <SettingRow title="动效强弱" desc="关闭后所有过渡与动画停止(含大屏)">
            <Segmented v-model="draft.mot" :options="data.motion" label="动效强弱" />
          </SettingRow>
        </SettingSection>

        <SettingSection id="sec-screen" title="全息图大屏" desc="大屏的配色与自动轮播节奏。">
          <SettingRow title="大屏配色" stack>
            <div class="grid grid-cols-3 gap-2.5" role="group" aria-label="大屏配色">
              <button
                v-for="(t, i) in data.palettes"
                :key="t.name"
                type="button"
                class="flex cursor-pointer flex-col gap-2 rounded-[10px] border-[1.5px] bg-white p-2 text-left"
                :class="i === draft.scr ? 'border-brand' : 'border-line-1'"
                :aria-pressed="i === draft.scr"
                :aria-label="`大屏配色 ${t.name}`"
                @click="draft.scr = i"
              >
                <div class="relative h-16 w-full overflow-hidden rounded-md" :style="{ background: t.bg }">
                  <span class="absolute top-2 right-2 left-2 h-2 rounded-[2px] opacity-35" :style="{ background: t.accent }" />
                  <span class="absolute top-[22px] bottom-2 left-2 w-[28%] rounded-[3px]" :style="{ background: t.panel }" />
                  <span class="absolute top-[22px] right-2 bottom-2 left-[38%] rounded-[3px]" :style="{ background: t.panel }" />
                  <span class="absolute top-[34px] left-[58%] size-3 rounded-full" :style="{ background: t.accent, boxShadow: `0 0 10px ${t.accent}` }" />
                </div>
                <span class="text-xs font-medium whitespace-nowrap">{{ t.name }}</span>
              </button>
            </div>
          </SettingRow>
          <SettingRow title="轮播间隔" desc="自动切换病组全景 / 机构矩阵 / 异地流向">
            <div class="flex items-center gap-2" role="group" aria-label="轮播间隔(秒)">
              <button type="button" :class="STEP_BTN" aria-label="缩短轮播间隔" @click="rot(-1)">−</button>
              <span class="yb-num min-w-7 text-center text-lg font-semibold" aria-live="polite">{{ draft.rot }}</span>
              <button type="button" :class="STEP_BTN" aria-label="延长轮播间隔" @click="rot(1)">+</button>
              <span class="text-xs text-ink-4">秒</span>
            </div>
          </SettingRow>
        </SettingSection>

        <SettingSection id="sec-brand" title="品牌与水印" desc="平台名称、机构标识与实名水印浓度。">
          <SettingRow title="平台名称" desc="显示在顶栏与浏览器标题旁">
            <Input v-model="draft.name" maxlength="40" aria-label="平台名称" class="h-9 w-[240px] rounded-lg border-line-4 bg-white px-3 text-[13px] shadow-none md:text-[13px]" />
          </SettingRow>
          <SettingRow title="机构标识" desc="建议 128×128 的 SVG / PNG">
            <div class="flex items-center gap-3">
              <span class="flex size-10 items-center justify-center rounded-[10px] text-lg font-bold text-white" :style="{ background: brand }">医</span>
              <button type="button" class="cursor-pointer rounded text-xs text-brand hover:underline" @click="logoUpload">上传标识</button>
            </div>
          </SettingRow>
          <SettingRow title="水印浓度" desc="页面实名水印的深浅">
            <Segmented v-model="draft.wm" :options="data.watermark" label="水印浓度" />
          </SettingRow>
        </SettingSection>
      </div>

      <!-- 实时预览 -->
      <aside class="sticky top-[136px] flex flex-col gap-3" aria-label="实时预览">
        <div class="flex items-center gap-2">
          <span class="text-xs font-semibold text-ink-4">实时预览</span>
          <span class="text-[11px] text-ink-5">· 随未保存的草稿变化</span>
          <div class="flex-1" />
          <label class="flex cursor-pointer items-center gap-2 text-xs text-ink-3">
            在真实页面上试用
            <Switch v-model="tryLive" size="lg" aria-label="在真实页面上试用草稿" />
          </label>
        </div>
        <AppearancePreview :draft="draft" :palettes="data.palettes" :rows="data.previewRows" />
        <p v-if="tryLive" class="text-[11px] text-warn-ink">试用中:草稿已临时应用到整个平台,离开本页未保存将自动回滚。</p>
      </aside>
    </div>

    <AlertDialog v-model:open="confirmReset">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>恢复默认外观?</AlertDialogTitle>
          <AlertDialogDescription>将清除已发布的自定义外观(含主题色、密度、大屏配色等),全平台恢复为默认设置,且不可撤销。</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction @click="doReset">确认恢复默认</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>

    <AlertDialog :open="leaveAsk" @update:open="onLeaveOpen">
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>有未保存的更改</AlertDialogTitle>
          <AlertDialogDescription>离开后这些更改将丢失。要先回去保存并发布吗?</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel @click="answerLeave(false)">继续编辑</AlertDialogCancel>
          <AlertDialogAction @click="answerLeave(true)">放弃更改并离开</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </PageSection>
</template>
