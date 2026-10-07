<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Gauge, Database, Send, BellRing, Settings } from '@lucide/vue'
import { themeVars, type Appearance } from '@/app/appearance'
import type { A15Palette, A15PreviewRow } from '@/mock/A15'

/**
 * 右侧实时预览:缩小的"真实壳层"(顶栏 / 一级菜单 / 二级标签 / KPI / 表格 / 按钮徽标 / 大屏缩略)。
 * 草稿样式只作用在本容器的局部 CSS 变量上,保存前不会污染真实页面。
 */
const props = defineProps<{ draft: Appearance; palettes: A15Palette[]; rows: A15PreviewRow[] }>()

const DESIGN_W = 580
const box = ref<HTMLElement>()
const scale = ref(0.78)
let ro: ResizeObserver | undefined
onMounted(() => {
  if (!box.value) return
  ro = new ResizeObserver(([e]) => { if (e) scale.value = Math.min(1, e.contentRect.width / DESIGN_W) })
  ro.observe(box.value)
})
onBeforeUnmount(() => ro?.disconnect())

const MENUS = [
  { label: '驾驶舱', icon: Gauge },
  { label: '归集', icon: Database },
  { label: '发布', icon: Send },
  { label: '预警', icon: BellRing },
  { label: '设置', icon: Settings },
]
const TABS = ['外观配置', '发布策略', '审计留痕']

const vars = computed(() => {
  const d = props.draft
  const compact = d.menu === 1
  return {
    ...themeVars(d),
    '--radius-card': ['4px', '12px', '18px'][d.rad],
    '--radius': ['0.25rem', '0.5rem', '0.625rem'][d.rad],
    '--card-px': ['16px', '20px', '24px'][d.dens],
    '--card-py': ['12px', '16px', '20px'][d.dens],
    '--row-py': ['5px', '9px', '12px'][d.dens],
    '--page-gap': ['10px', '14px', '18px'][d.dens],
    '--menu-py': compact ? '4px' : '7px',
    '--menu-px': compact ? '8px' : '11px',
    '--menu-font': compact ? '11px' : '12.5px',
    fontSize: ['12px', '13px', '14px'][d.font],
    width: `${DESIGN_W}px`,
    zoom: String(scale.value),
  }
})

const cardStyle = computed(() => {
  const d = props.draft
  return {
    borderRadius: 'var(--radius-card)',
    padding: 'var(--card-py) var(--card-px)',
    borderColor: d.card === 0 ? '#DCE1E9' : d.card === 1 ? 'var(--brand-line)' : '#EEF1F5',
    background: d.card === 1 ? 'linear-gradient(135deg, var(--brand-soft) 0%, #fff 62%)' : '#fff',
    boxShadow: d.card === 2 ? '0 4px 14px rgba(15,23,42,.08)' : 'none',
  }
})
const scr = computed(() => props.palettes[props.draft.scr] ?? props.palettes[0]!)
</script>

<template>
  <div ref="box" class="w-full">
    <div class="overflow-hidden rounded-[10px] border border-line-1 bg-background">
      <div :style="vars" class="bg-background text-foreground" aria-hidden="true">
        <!-- 顶栏 -->
        <div class="flex h-[46px] items-center gap-3 border-b border-line-1 bg-white px-3.5">
          <span class="flex size-6 shrink-0 items-center justify-center text-xs font-bold text-white" :style="{ background: 'var(--brand)', borderRadius: 'var(--radius)' }">医</span>
          <span class="max-w-[120px] truncate text-[13px] font-semibold">{{ draft.name }}</span>
          <div class="ml-1 flex items-center" :style="{ gap: draft.menu === 1 ? '2px' : '4px' }">
            <span
              v-for="(m, i) in MENUS"
              :key="m.label"
              class="flex items-center gap-1 font-medium whitespace-nowrap"
              :class="i === 4 ? 'bg-brand-soft text-brand' : 'text-ink-3'"
              :style="{ padding: 'var(--menu-py) var(--menu-px)', fontSize: 'var(--menu-font)', borderRadius: 'var(--radius)' }"
            ><component :is="m.icon" :class="draft.menu === 1 ? 'size-3' : 'size-3.5'" />{{ m.label }}</span>
          </div>
          <div class="flex-1" />
          <span class="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-soft text-[11px] font-semibold text-brand">召</span>
        </div>
        <!-- 二级标签 -->
        <div class="flex gap-4 border-b border-line-1 bg-white px-4 text-xs">
          <span
            v-for="(t, i) in TABS"
            :key="t"
            class="border-b-2 py-2 whitespace-nowrap"
            :class="i === 0 ? 'border-brand font-semibold text-brand' : 'border-transparent text-ink-4'"
          >{{ t }}</span>
        </div>
        <div class="flex flex-col p-3.5" :style="{ gap: 'var(--page-gap)' }">
          <!-- KPI -->
          <div class="grid grid-cols-2" :style="{ gap: 'var(--page-gap)' }">
            <div class="border" :style="cardStyle">
              <div class="text-[11px] text-ink-4">例均基金差额</div>
              <div class="yb-num text-2xl font-semibold text-bad">+486</div>
              <div class="relative mt-1.5 h-1.5 rounded-[3px] bg-line-2">
                <span class="absolute top-[-3px] left-[62%] h-3 w-1 rounded-[2px] bg-brand" />
              </div>
            </div>
            <div class="border" :style="cardStyle">
              <div class="text-[11px] text-ink-4">CMI</div>
              <div class="yb-num text-2xl font-semibold">1.12</div>
              <div class="text-[11px] text-brand">同级 P68</div>
            </div>
          </div>
          <!-- 表格 -->
          <div class="overflow-hidden border bg-white" :style="{ borderRadius: 'var(--radius-card)', borderColor: cardStyle.borderColor, boxShadow: cardStyle.boxShadow }">
            <div class="flex justify-between border-b border-line-2 bg-surface-1 px-3 py-1.5 text-[11px] text-ink-4"><span>病组</span><span>例均差额</span></div>
            <div
              v-for="(r, i) in rows"
              :key="r.name"
              class="flex justify-between border-b border-line-3 px-3 last:border-b-0"
              :style="{ paddingTop: 'var(--row-py)', paddingBottom: 'var(--row-py)', background: draft.zebra === 1 && i % 2 === 1 ? 'var(--surface-1)' : '#fff' }"
            >
              <span>{{ r.name }}</span>
              <span :class="['yb-num font-semibold', r.tone === 'bad' ? 'text-bad' : 'text-ok-ink']">{{ r.value }}</span>
            </div>
          </div>
          <!-- 按钮与徽标 -->
          <div class="flex flex-wrap items-center gap-2">
            <span class="flex h-8 items-center px-3.5 text-xs font-semibold whitespace-nowrap text-white" :style="{ borderRadius: 'var(--radius)', background: 'var(--brand)' }">主按钮</span>
            <span class="flex h-8 items-center border border-line-1 bg-white px-3.5 text-xs whitespace-nowrap" :style="{ borderRadius: 'var(--radius)' }">次按钮</span>
            <span class="flex h-8 items-center bg-brand-soft px-3.5 text-xs font-semibold whitespace-nowrap text-brand" :style="{ borderRadius: 'var(--radius)' }">浅色按钮</span>
            <span class="ml-1 rounded-full bg-brand-soft px-2 py-0.5 text-[11px] font-semibold text-brand">已发布</span>
            <span class="rounded-full bg-ok-soft px-2 py-0.5 text-[11px] font-semibold text-ok-ink">正常</span>
            <span class="rounded-full bg-warn-soft px-2 py-0.5 text-[11px] font-semibold text-warn-ink">待复核</span>
          </div>
        </div>
        <!-- 全景图大屏缩略 -->
        <div class="relative mx-3.5 mb-3.5 h-[128px] overflow-hidden" :style="{ background: scr.bg, borderRadius: 'var(--radius-card)' }">
          <span class="absolute top-2.5 left-3.5 text-xs font-semibold tracking-[3px] text-[#F4F8FF]">医保数据公开全景图</span>
          <span class="absolute top-8 right-3.5 left-3.5 h-[18px] rounded" :style="{ background: scr.panel }" />
          <span class="absolute top-[58px] bottom-3 left-3.5 w-[30%] rounded" :style="{ background: scr.panel }" />
          <span class="absolute top-[58px] right-3.5 bottom-3 left-[36%] rounded" :style="{ background: scr.panel }" />
          <span class="absolute top-[72px] left-[56%] size-4 rounded-full" :style="{ background: scr.accent, boxShadow: `0 0 14px ${scr.accent}` }" />
          <span class="absolute top-[88px] left-[74%] size-2.5 rounded-full bg-[#3FD1A0]" />
          <span class="absolute top-[68px] left-[66%] size-2 rounded-full bg-[#FF6B5E]" />
        </div>
      </div>
    </div>
  </div>
</template>
