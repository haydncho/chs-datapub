<script setup lang="ts">
import type { A1Data } from '@/mock/A1'

defineProps<{ data: A1Data }>()
</script>

<template>
  <div class="relative flex flex-col overflow-hidden bg-[#071330] px-14 py-12 text-white max-xl:px-12 max-lg:px-5 max-lg:py-4">
    <div
      class="absolute inset-0"
      style="background: radial-gradient(ellipse 70% 55% at 20% 0%, color-mix(in srgb, var(--brand) 55%, transparent), transparent 70%), radial-gradient(ellipse 50% 40% at 100% 100%, rgba(58,160,255,.25), transparent 70%)"
    />
    <div
      class="absolute inset-0 bg-[size:44px_44px]"
      style="background-image: linear-gradient(rgba(127,195,255,.05) 1px, transparent 1px), linear-gradient(90deg, rgba(127,195,255,.05) 1px, transparent 1px)"
    />

    <div class="relative flex items-center gap-3">
      <span class="flex size-10 items-center justify-center rounded-[10px] bg-brand text-[19px] font-bold">医</span>
      <div class="leading-[1.3]">
        <div class="text-[17px] font-semibold">{{ data.org.name }}</div>
        <div class="text-xs text-[#9FB2D1]">{{ data.org.sub }}</div>
      </div>
      <!-- 窄屏:品牌区缩成一条,标语同行 -->
      <div class="ml-auto min-w-0 truncate text-sm font-medium text-[#C9D8F0] lg:hidden">{{ data.slogan[0] }}{{ data.slogan[1] }}</div>
    </div>

    <!-- 竖屏 Pad(768–1023):品牌条下方补一行三项价值点,不只是横屏的压缩 -->
    <div class="relative mt-4 hidden grid-cols-3 gap-5 border-t border-[rgba(127,195,255,.18)] pt-4 md:max-lg:grid" data-testid="brand-values-compact">
      <div v-for="x in data.values" :key="x.title" class="flex min-w-0 items-start gap-2.5">
        <span class="flex size-8 shrink-0 items-center justify-center rounded-lg border border-[rgba(127,195,255,.25)] bg-[rgba(127,195,255,.12)]">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="#7FC3FF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="x.icon" /></svg>
        </span>
        <div class="min-w-0">
          <div class="text-sm font-semibold">{{ x.title }}</div>
          <div class="text-xs leading-[1.5] text-pretty text-[#9FB2D1]">{{ x.desc }}</div>
        </div>
      </div>
    </div>

    <div class="relative flex max-w-[560px] flex-1 max-lg:hidden flex-col justify-center gap-[26px] py-8">
      <div class="text-[clamp(28px,2.6vw,40px)] leading-[1.3] font-semibold tracking-[.5px] text-pretty">
        {{ data.slogan[0] }}<br>{{ data.slogan[1] }}
      </div>
      <div class="flex flex-col gap-4">
        <div v-for="x in data.values" :key="x.title" class="flex items-start gap-3.5">
          <span class="flex size-9 shrink-0 items-center justify-center rounded-[10px] border border-[rgba(127,195,255,.25)] bg-[rgba(127,195,255,.12)]">
            <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="#7FC3FF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="x.icon" /></svg>
          </span>
          <div>
            <div class="text-[15px] font-semibold">{{ x.title }}</div>
            <div class="text-[13px] text-[#9FB2D1]">{{ x.desc }}</div>
          </div>
        </div>
      </div>
      <div class="grid grid-cols-3 border-t border-[rgba(127,195,255,.18)] pt-5">
        <div v-for="k in data.stats" :key="k.label">
          <div class="yb-num text-[30px] font-semibold">
            {{ k.value }}<span class="ml-[3px] font-sans text-sm text-[#9FB2D1]">{{ k.unit }}</span>
          </div>
          <div class="text-xs text-[#9FB2D1]">{{ k.label }}</div>
        </div>
      </div>
    </div>

    <div class="relative flex gap-4 text-[11px] text-[#6F84A6] max-xl:text-xs max-lg:hidden">
      <span v-for="f in data.footer" :key="f">{{ f }}</span>
    </div>
  </div>
</template>
