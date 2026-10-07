<script setup lang="ts">
import { cn } from '@/lib/utils'
import AnimatedNumber from './AnimatedNumber.vue'
import Spark from './Spark.vue'
import { useCockpit, type Period } from './store'

const { s, I, ribbon, clock, data, restart, motion } = useCockpit()
const PERIODS: Period[] = ['月', '季', '年']
</script>

<template>
  <!-- 主标题装饰(2.5D):透视平台 + 前沿高光 + 两翼导线 + 指向中心的箭头 + 流光 -->
  <svg class="pointer-events-none absolute top-0 left-0" width="1920" height="84" viewBox="0 0 1920 84" aria-hidden="true">
    <defs>
      <linearGradient id="hdWingL" x1="0" x2="1"><stop offset="0" stop-color="#3AA0FF" stop-opacity="0" /><stop offset="1" stop-color="#7FC3FF" stop-opacity=".9" /></linearGradient>
      <linearGradient id="hdWingR" x1="1" x2="0"><stop offset="0" stop-color="#3AA0FF" stop-opacity="0" /><stop offset="1" stop-color="#7FC3FF" stop-opacity=".9" /></linearGradient>
      <linearGradient id="hdFace" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3AA0FF" stop-opacity=".04" /><stop offset="1" stop-color="#3AA0FF" stop-opacity=".34" /></linearGradient>
      <linearGradient id="hdFront" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7FC3FF" stop-opacity=".6" /><stop offset="1" stop-color="#3AA0FF" stop-opacity="0" /></linearGradient>
      <linearGradient id="hdEdge" x1="0" x2="1">
        <stop offset="0" stop-color="#7FC3FF" stop-opacity=".2" /><stop offset=".5" stop-color="#E6F3FF" /><stop offset="1" stop-color="#7FC3FF" stop-opacity=".2" />
      </linearGradient>
      <radialGradient id="hdGlow" cx=".5" cy="1" r=".62"><stop offset="0" stop-color="#3AA0FF" stop-opacity=".38" /><stop offset="1" stop-color="#3AA0FF" stop-opacity="0" /></radialGradient>
      <filter id="hdBlur" x="-10%" y="-200%" width="120%" height="500%"><feGaussianBlur stdDeviation="3" /></filter>
    </defs>
    <!-- 背光 -->
    <ellipse cx="960" cy="74" rx="380" ry="46" fill="url(#hdGlow)" />
    <!-- 平台:顶面(近大远小的透视梯形)+ 正面厚度 -->
    <polygon points="704,54 1216,54 1262,74 658,74" fill="url(#hdFace)" stroke="#7FC3FF" stroke-opacity=".3" />
    <polygon points="658,74 1262,74 1250,82 670,82" fill="url(#hdFront)" />
    <!-- 前沿高光 -->
    <line x1="658" y1="74" x2="1262" y2="74" stroke="url(#hdEdge)" stroke-width="4" filter="url(#hdBlur)" />
    <line x1="658" y1="74" x2="1262" y2="74" stroke="url(#hdEdge)" stroke-width="1.5" />
    <!-- 两翼导线 -->
    <path d="M28 79 H594 L612 72 H652" fill="none" stroke="url(#hdWingL)" stroke-width="1.5" />
    <path d="M1892 79 H1326 L1308 72 H1268" fill="none" stroke="url(#hdWingR)" stroke-width="1.5" />
    <!-- 指向中心的箭头 -->
    <g fill="#7FC3FF">
      <polygon v-for="i in 3" :key="'l' + i" :class="motion && 'hd-chev'" :style="{ animationDelay: i * 0.25 + 's' }" :opacity="0.25 + i * 0.22" :points="`${598 + i * 14},58 ${606 + i * 14},58 ${613 + i * 14},64 ${606 + i * 14},70 ${598 + i * 14},70 ${605 + i * 14},64`" />
      <polygon v-for="i in 3" :key="'r' + i" :class="motion && 'hd-chev'" :style="{ animationDelay: i * 0.25 + 's' }" :opacity="0.25 + i * 0.22" :points="`${1322 - i * 14},58 ${1314 - i * 14},58 ${1307 - i * 14},64 ${1314 - i * 14},70 ${1322 - i * 14},70 ${1315 - i * 14},64`" />
    </g>
    <!-- 流光:沿两翼流向中心(动效关闭时不显示) -->
    <template v-if="motion">
      <path class="hd-flow" d="M28 79 H594 L612 72 H652" fill="none" stroke="#E6F3FF" stroke-width="2" stroke-linecap="round" />
      <path class="hd-flow" d="M1892 79 H1326 L1308 72 H1268" fill="none" stroke="#E6F3FF" stroke-width="2" stroke-linecap="round" />
    </template>
    <!-- 平台上的英文副标 -->
    <text x="960" y="69" text-anchor="middle" font-size="14" letter-spacing="5" fill="#7FC3FF" fill-opacity=".75" font-family="'DIN Alternate','Noto Sans SC',sans-serif">HOLOGRAPHIC DATA VIEW</text>
  </svg>

  <!-- title bar -->
  <div class="absolute top-0 right-7 left-7 flex h-[72px] items-center">
    <div class="flex w-[600px] items-center gap-3.5">
      <div class="flex size-9 items-center justify-center rounded-lg bg-brand text-lg font-bold">医</div>
      <div class="leading-[1.3]">
        <div class="text-[17px] font-semibold">{{ I.org }}</div>
        <div class="text-[13px] text-[#6F84A6]">医保数据工作组 · {{ I.zone }}</div>
      </div>
    </div>
    <div class="-mt-4 flex-1 text-center">
      <div class="hd-title inline-block pl-[12px] text-[36px] leading-none font-bold tracking-[12px]">{{ I.title }}</div>
    </div>
    <div class="flex w-[600px] items-center justify-end gap-4">
      <div class="flex overflow-hidden rounded-md border border-[rgba(90,150,255,.3)]">
        <button type="button"
          v-for="p in PERIODS"
          :key="p"
          :class="cn(
            'cursor-pointer px-4 py-1 text-[15px] font-semibold',
            p === s.period ? 'bg-[#3AA0FF] text-[#04101F]' : 'text-[#9FB2D1]',
          )"
          @click="s.period = p; restart()"
        >{{ p }}</button>
      </div>
      <div class="text-right leading-[1.2]">
        <div class="yb-num text-[28px] font-medium tracking-[1px]">{{ clock.time }}</div>
        <div class="text-[13px] text-[#6F84A6]">{{ clock.date }} · 数据截至 {{ data.dataAsOf }}</div>
      </div>
    </div>
  </div>

  <!-- KPI ribbon:数字滚动 + 走势折线 + 状态色 -->
  <div
    class="absolute top-[84px] right-7 left-7 grid h-[100px] grid-cols-6 rounded-[10px] border border-[rgba(90,150,255,.18)] bg-[linear-gradient(180deg,rgba(18,40,82,.55),rgba(10,22,46,.35))]"
    data-testid="cockpit-kpis"
  >
    <div
      v-for="k in ribbon"
      :key="k.k"
      :class="cn(
        'relative flex flex-col justify-between border-l px-5 py-3',
        k.first ? 'border-transparent' : 'border-[rgba(90,150,255,.14)]',
      )"
    >
      <span class="absolute inset-x-5 top-0 h-[3px] rounded-b-[2px] opacity-80" :style="{ background: k.dc }" />
      <!-- 2.5D 底座光:卡片底部的透视光带 -->
      <span class="kpi-base pointer-events-none absolute inset-x-6 -bottom-px h-[14px]" :style="{ '--c': k.dc }" />
      <div class="flex items-center justify-between gap-2">
        <span class="truncate text-base text-[#9FB2D1]">{{ k.k }}</span>
        <span
          class="yb-num shrink-0 rounded-md px-2 py-px text-sm font-semibold whitespace-nowrap"
          :style="{ color: k.dc, background: k.dc + '22' }"
          :title="k.st ? `较上期 ${k.d} · ${k.st}` : `较上期 ${k.d}`"
        >{{ k.d }}</span>
      </div>
      <div class="flex items-end justify-between gap-2">
        <div class="flex min-w-0 items-baseline gap-1">
          <AnimatedNumber :value="k.v" :replay="s.pulse" class="yb-num text-[40px] leading-none font-semibold text-[#F4F8FF]" />
          <span class="text-[13px] text-[#9FB2D1]">{{ k.u }}</span>
        </div>
        <Spark :trend="k.trend" :color="k.dc" :replay="s.pulse" :w="104" :h="36" />
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 主标题:白 → 浅蓝渐变字 + 外发光 */
.hd-title {
  background: linear-gradient(180deg, #FFFFFF 35%, #A9D3FF 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 14px rgba(58, 160, 255, .55)) drop-shadow(0 2px 0 rgba(4, 16, 31, .9));
}
.hd-chev { animation: hdChev 1.6s ease-in-out infinite; }
@keyframes hdChev { 0%, 100% { opacity: .2 } 50% { opacity: 1 } }
.hd-flow {
  stroke-dasharray: 80 1400;
  animation: hdFlow 4.2s linear infinite;
  filter: drop-shadow(0 0 4px #7FC3FF);
}
@keyframes hdFlow { from { stroke-dashoffset: 80 } to { stroke-dashoffset: -680 } }
/* 指标卡 2.5D 底座光:透视的椭圆光带 */
.kpi-base {
  background: radial-gradient(ellipse 50% 100% at 50% 100%, color-mix(in srgb, var(--c) 45%, transparent), transparent 70%);
  transform: perspective(120px) rotateX(55deg);
  transform-origin: 50% 100%;
}
</style>
