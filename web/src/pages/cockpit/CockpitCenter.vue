<script setup lang="ts">
import { computed } from 'vue'
import { cn } from '@/lib/utils'
import BubbleView from './BubbleView.vue'
import DeptView from './DeptView.vue'
import FlowView from './FlowView.vue'
import InstView from './InstView.vue'
import PeerView from './PeerView.vue'
import PubMatrix from './PubMatrix.vue'
import { LENSES, VIEW_NAME, useCockpit } from './store'

const { s, I, view, lens, rotN, det, go2 } = useCockpit()

const tabs = computed(() =>
  I.value.views.map(v => {
    const on = v === view.value
    return {
      v,
      label: VIEW_NAME[v],
      on,
      pw: on && s.rot ? (Math.min(1, s.rotT / rotN.value) * 100).toFixed(1) + '%' : '0%',
    }
  }),
)
</script>

<template>
  <div
    class="absolute top-[216px] left-[492px] flex h-[684px] w-[936px] flex-col rounded-[10px] border border-[rgba(90,150,255,.22)] bg-[rgba(10,22,46,.5)] px-5 py-4"
  >
    <div class="flex items-center gap-1.5">
      <button type="button"
        v-for="t in tabs"
        :key="t.v"
        :class="cn(
          'relative cursor-pointer overflow-hidden rounded-lg px-[18px] py-[7px] text-[17px] font-semibold',
          t.on ? 'bg-[rgba(58,160,255,.2)] text-[#F4F8FF]' : 'text-[#9FB2D1]',
        )"
        @click="go2({ view: t.v, sel: null, rotT: 0 })"
      >{{ t.label }}<span
        class="absolute bottom-0 left-0 h-[3px] bg-[#3AA0FF] transition-[width] duration-1000 ease-linear"
        :style="{ width: t.pw }"
      /></button>
      <div class="flex-1" />
      <div v-if="view === 'bub'" class="flex gap-1 rounded-lg bg-[rgba(255,255,255,.04)] p-[3px]">
        <button type="button"
          v-for="[id, l] in LENSES"
          :key="id"
          :class="cn(
            'cursor-pointer rounded-md px-3 py-1 text-sm font-medium',
            id === lens ? 'bg-[#3AA0FF] text-[#04101F]' : 'text-[#9FB2D1]',
          )"
          @click="go2({ lens: id })"
        >{{ l }}</button>
      </div>
    </div>

    <div class="relative mt-3 flex-1">
      <BubbleView v-if="view === 'bub'" />
      <InstView v-else-if="view === 'inst'" />
      <FlowView v-else-if="view === 'flow'" />
      <DeptView v-else-if="view === 'dept'" />
      <PeerView v-else-if="view === 'peer'" />
      <PubMatrix v-else-if="view === 'pub'" />
    </div>

    <!-- selected object -->
    <div class="mt-3 flex h-24 items-center gap-6 rounded-lg border border-[rgba(90,150,255,.2)] bg-[rgba(4,10,22,.7)] px-[18px] py-3">
      <div class="w-[220px] min-w-0 shrink-0">
        <div class="text-[13px] text-[#6F84A6]">选中对象</div>
        <div class="truncate text-[19px] font-semibold" :title="det.t">{{ det.t }}</div>
        <div class="text-[13px] text-[#9FB2D1]">{{ det.sub }}</div>
      </div>
      <div
        v-for="r in det.rows"
        :key="r.k"
        class="min-w-0 flex-1 border-l border-[rgba(90,150,255,.16)] pl-[18px] whitespace-nowrap"
      >
        <div class="text-sm text-[#9FB2D1]">{{ r.k }}</div>
        <div class="yb-num truncate text-2xl font-semibold" :style="{ color: r.c }">{{ r.v }}</div>
      </div>
    </div>
  </div>
</template>
