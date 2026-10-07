<script setup lang="ts">
import { useId } from 'vue'
import { useCockpit } from './store'

/**
 * 大屏主标题(主屏与双屏副屏共用):2.5D 透视平台(顶面 + 正面厚度 + 前沿高光 + 背光)、
 * 白 → 浅蓝渐变发光字、平台上的英文副标、两翼导线、指向中心的箭头与流光(随外观「动效」开关)。
 */
defineProps<{ title: string; caption: string }>()
const { motion } = useCockpit()
/* 双屏时两块屏同时渲染,SVG 渐变 / 滤镜 id 需各自唯一 */
const uid = useId()
const id = (n: string) => `${uid}-${n}`
const link = (n: string) => `url(#${id(n)})`
</script>

<template>
  <svg class="pointer-events-none absolute top-0 left-0" width="1920" height="84" viewBox="0 0 1920 84" aria-hidden="true">
    <defs>
      <linearGradient :id="id('wl')" x1="0" x2="1"><stop offset="0" stop-color="#3AA0FF" stop-opacity="0" /><stop offset="1" stop-color="#7FC3FF" stop-opacity=".9" /></linearGradient>
      <linearGradient :id="id('wr')" x1="1" x2="0"><stop offset="0" stop-color="#3AA0FF" stop-opacity="0" /><stop offset="1" stop-color="#7FC3FF" stop-opacity=".9" /></linearGradient>
      <linearGradient :id="id('face')" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#3AA0FF" stop-opacity=".04" /><stop offset="1" stop-color="#3AA0FF" stop-opacity=".34" /></linearGradient>
      <linearGradient :id="id('front')" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#7FC3FF" stop-opacity=".6" /><stop offset="1" stop-color="#3AA0FF" stop-opacity="0" /></linearGradient>
      <linearGradient :id="id('edge')" x1="0" x2="1">
        <stop offset="0" stop-color="#7FC3FF" stop-opacity=".2" /><stop offset=".5" stop-color="#E6F3FF" /><stop offset="1" stop-color="#7FC3FF" stop-opacity=".2" />
      </linearGradient>
      <radialGradient :id="id('glow')" cx=".5" cy="1" r=".62"><stop offset="0" stop-color="#3AA0FF" stop-opacity=".38" /><stop offset="1" stop-color="#3AA0FF" stop-opacity="0" /></radialGradient>
      <filter :id="id('blur')" x="-10%" y="-200%" width="120%" height="500%"><feGaussianBlur stdDeviation="3" /></filter>
    </defs>
    <!-- 背光 -->
    <ellipse cx="960" cy="74" rx="380" ry="46" :fill="link('glow')" />
    <!-- 平台:顶面(近大远小的透视梯形)+ 正面厚度 -->
    <polygon points="704,54 1216,54 1262,74 658,74" :fill="link('face')" stroke="#7FC3FF" stroke-opacity=".3" />
    <polygon points="658,74 1262,74 1250,82 670,82" :fill="link('front')" />
    <!-- 前沿高光 -->
    <line x1="658" y1="74" x2="1262" y2="74" :stroke="link('edge')" stroke-width="4" :filter="link('blur')" />
    <line x1="658" y1="74" x2="1262" y2="74" :stroke="link('edge')" stroke-width="1.5" />
    <!-- 两翼导线 -->
    <path d="M28 79 H594 L612 72 H652" fill="none" :stroke="link('wl')" stroke-width="1.5" />
    <path d="M1892 79 H1326 L1308 72 H1268" fill="none" :stroke="link('wr')" stroke-width="1.5" />
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
    <text x="960" y="69" text-anchor="middle" font-size="14" letter-spacing="5" fill="#7FC3FF" fill-opacity=".75" font-family="'DIN Alternate','Noto Sans SC',sans-serif">{{ caption }}</text>
  </svg>
  <div class="pointer-events-none absolute top-[15px] left-[660px] w-[600px] text-center">
    <div class="hd-title inline-block pl-[10px] text-[30px] leading-none font-bold tracking-[10px] whitespace-nowrap">{{ title }}</div>
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
</style>
