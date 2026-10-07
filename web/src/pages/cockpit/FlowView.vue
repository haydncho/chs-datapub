<script setup lang="ts">
import { useCockpit } from './store'

const { I, flows, data } = useCockpit()
</script>

<template>
  <div class="absolute top-0 left-0 h-[470px] w-[896px]">
    <svg viewBox="0 0 896 470" class="absolute inset-0 h-[470px] w-[896px]">
      <template v-for="f in flows" :key="f.nm">
        <path :d="f.d" fill="none" :stroke="f.c" stroke-opacity="0.45" :stroke-width="f.w" stroke-linecap="round" />
        <path
          :d="f.d"
          fill="none"
          stroke="#fff"
          stroke-opacity="0.55"
          stroke-width="2"
          stroke-dasharray="4 14"
          stroke-linecap="round"
          class="[animation:cockDash_1.4s_linear_infinite]"
        />
      </template>
    </svg>
    <div
      class="absolute top-[191px] left-[30px] flex h-[88px] w-[170px] flex-col items-center justify-center rounded-[10px] bg-brand shadow-[0_0_48px_rgb(var(--ck-acc)/.45)]"
    >
      <span class="text-[22px] font-semibold">{{ data.flowOrigin.name }}</span>
      <span class="text-sm text-[#CFE0FF]">{{ data.flowOrigin.sub }}</span>
    </div>
    <div
      v-for="f in flows"
      :key="'l' + f.nm"
      class="absolute left-[560px] flex w-[330px] items-center gap-3 whitespace-nowrap"
      :style="{ top: f.top }"
    >
      <span class="h-11 w-2 rounded-[3px]" :style="{ background: f.c }" />
      <div>
        <div class="text-lg font-semibold">{{ f.nm }} <span class="text-[13px] font-normal text-[#6F84A6]">{{ f.t }}</span></div>
        <div class="text-[15px] text-[#AEBBD0]">
          <span class="yb-num text-[21px] font-semibold" :style="{ color: f.c }">{{ f.sh }}%</span> · {{ f.amt }}
        </div>
      </div>
    </div>
    <div class="absolute bottom-0 left-[30px] text-sm text-[#6F84A6]">{{ I.flowNote }}</div>
  </div>
</template>
