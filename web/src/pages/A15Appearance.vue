<script setup lang="ts">
import { computed, reactive } from 'vue'
import { PageHeader, PageSection } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import {
  appearance, publishAppearance, resetAppearance, DEFAULT_APPEARANCE, THEME_COLORS, type Appearance,
} from '@/app/appearance'
import { A15_SEED } from '@/mock/A15'
import Segmented from './A15/Segmented.vue'
import AppearancePreview from './A15/AppearancePreview.vue'

const data = usePageData('A15', A15_SEED)

/** working copy — only published platform-wide on 保存并发布 */
const draft = reactive<Appearance>({ ...appearance })
const color = computed(() => THEME_COLORS[draft.c] ?? THEME_COLORS[0])

function rot(dir: 1 | -1) {
  const { min, max, step } = data.value.rotation
  draft.rot = Math.min(max, Math.max(min, draft.rot + dir * step))
}

function save() {
  const next = { ...draft }
  publishAppearance(next)
  sendAction('A15', 'publishAppearance', next)
  say('外观已发布 · 已应用到全部页面')
}

function reset() {
  resetAppearance()
  Object.assign(draft, DEFAULT_APPEARANCE)
  sendAction('A15', 'resetAppearance', {})
}

const STEP_BTN = 'flex size-7 cursor-pointer items-center justify-center rounded-md border border-line-1 bg-white hover:bg-surface-1'
</script>

<template>
  <PageSection label="A15 外观配置" class="grid! grid-cols-[minmax(0,1fr)_460px] items-start">
    <div class="flex flex-col gap-4">
      <PageHeader title="外观配置" subtitle="平台主题 · 全息图大屏 · 品牌标识 · 对全部用户生效">
        <Button variant="outline" class="px-3.5 font-normal" @click="reset">恢复默认</Button>
        <Button :style="{ background: color }" @click="save">保存并发布</Button>
      </PageHeader>

      <!-- 平台主题 -->
      <div class="yb-card flex flex-col gap-[18px] px-card-x py-card-y">
        <div class="text-[15px] font-semibold">平台主题</div>
        <div class="grid grid-cols-[120px_minmax(0,1fr)] items-center gap-x-[18px] gap-y-3.5">
          <span class="text-ink-4">主题色</span>
          <div class="flex gap-2.5">
            <button
              v-for="(c, i) in THEME_COLORS"
              :key="c"
              type="button"
              class="flex cursor-pointer flex-col items-center gap-1"
              @click="draft.c = i"
            >
              <span
                class="size-[34px] rounded-[10px]"
                :style="{ background: c, boxShadow: i === draft.c ? `0 0 0 2px #fff, 0 0 0 4px ${c}` : 'none' }"
              />
              <span class="text-[11px] whitespace-nowrap text-ink-4">{{ data.themeNames[i] }}</span>
            </button>
          </div>
          <span class="text-ink-4">界面密度</span>
          <Segmented v-model="draft.dens" :options="data.density" />
          <span class="text-ink-4">圆角</span>
          <Segmented v-model="draft.rad" :options="data.radius" />
          <span class="text-ink-4">正文字号</span>
          <Segmented v-model="draft.font" :options="data.fontSize" />
          <span class="text-ink-4">卡片样式</span>
          <Segmented v-model="draft.card" :options="data.cardStyle" />
        </div>
      </div>

      <!-- 全息图大屏 -->
      <div class="yb-card flex flex-col gap-3.5 px-card-x py-card-y">
        <div class="text-[15px] font-semibold">全息图大屏</div>
        <div class="grid grid-cols-3 gap-2.5">
          <button
            v-for="(t, i) in data.palettes"
            :key="t.name"
            type="button"
            class="flex cursor-pointer flex-col gap-2 rounded-[10px] border-[1.5px] bg-white p-2 text-left"
            :style="{ borderColor: i === draft.scr ? color : '#E5E9F0' }"
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
        <div class="grid grid-cols-[120px_minmax(0,1fr)] items-center gap-x-[18px] gap-y-3.5">
          <span class="text-ink-4">动效</span>
          <Segmented v-model="draft.mot" :options="data.motion" />
          <span class="text-ink-4">轮播间隔</span>
          <div class="flex items-center gap-2">
            <button type="button" :class="STEP_BTN" aria-label="缩短轮播间隔" @click="rot(-1)">−</button>
            <span class="yb-num min-w-7 text-center text-lg font-semibold">{{ draft.rot }}</span>
            <button type="button" :class="STEP_BTN" aria-label="延长轮播间隔" @click="rot(1)">+</button>
            <span class="text-xs text-ink-4">秒 · 自动切换病组全景 / 机构矩阵 / 异地流向</span>
          </div>
        </div>
      </div>

      <!-- 品牌与水印 -->
      <div class="yb-card grid grid-cols-[120px_minmax(0,1fr)] items-center gap-x-[18px] gap-y-3.5 px-card-x py-card-y">
        <div class="col-span-full text-[15px] font-semibold">品牌与水印</div>
        <span class="text-ink-4">平台名称</span>
        <Input v-model="draft.name" class="h-9 max-w-[360px] rounded-lg border-line-4 bg-white px-3 text-[13px] shadow-none md:text-[13px]" />
        <span class="text-ink-4">机构标识</span>
        <div class="flex items-center gap-3">
          <span class="flex size-10 items-center justify-center rounded-[10px] text-lg font-bold text-white" :style="{ background: color }">医</span>
          <span class="cursor-pointer text-xs text-brand">上传 SVG / PNG · 建议 128×128</span>
        </div>
        <span class="text-ink-4">水印浓度</span>
        <Segmented v-model="draft.wm" :options="data.watermark" />
      </div>
    </div>

    <AppearancePreview :draft="draft" :palettes="data.palettes" :rows="data.previewRows" />
  </PageSection>
</template>
