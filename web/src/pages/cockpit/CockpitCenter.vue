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

const { s, views, view, lens, rotN, det, go2 } = useCockpit()

const tabs = computed(() =>
  views.value.map(v => {
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
    class="absolute top-[200px] left-7 flex h-[564px] w-[1208px] flex-col rounded-[10px] border border-[rgb(var(--ck-line)/.22)] bg-[rgb(var(--ck-panel)/.5)] px-5 py-4"
  >
    <div class="flex items-center gap-1.5">
      <button type="button"
        v-for="t in tabs"
        :key="t.v"
        :class="cn(
          'relative cursor-pointer overflow-hidden rounded-lg px-[18px] py-[7px] text-[17px] font-semibold',
          t.on ? 'bg-[rgb(var(--ck-acc)/.2)] text-[#F4F8FF]' : 'text-[#9FB2D1]',
        )"
        @click="go2({ view: t.v, sel: null, rotT: 0 })"
      >{{ t.label }}<span
        class="absolute bottom-0 left-0 h-[3px] bg-[rgb(var(--ck-acc))] transition-[width] duration-1000 ease-linear"
        :style="{ width: t.pw }"
      /></button>
      <div class="flex-1" />
      <div v-if="view === 'bub'" class="flex gap-1 rounded-lg bg-[rgba(255,255,255,.04)] p-[3px]">
        <button type="button"
          v-for="[id, l] in LENSES"
          :key="id"
          :class="cn(
            'cursor-pointer rounded-md px-3 py-1 text-sm font-medium',
            id === lens ? 'bg-[rgb(var(--ck-acc))] text-[#04101F]' : 'text-[#9FB2D1]',
          )"
          @click="go2({ lens: id })"
        >{{ l }}</button>
      </div>
    </div>

    <!-- 主视图 + 右侧选中对象卡(气泡图在最左侧,选中对象竖排在其右) -->
    <div class="mt-3 flex min-h-0 flex-1 gap-4">
      <div class="relative min-w-0 flex-1">
        <BubbleView v-if="view === 'bub'" />
        <InstView v-else-if="view === 'inst'" />
        <FlowView v-else-if="view === 'flow'" />
        <DeptView v-else-if="view === 'dept'" />
        <PeerView v-else-if="view === 'peer'" />
        <PubMatrix v-else-if="view === 'pub'" />
      </div>

      <!-- selected object -->
      <div class="flex w-[236px] shrink-0 flex-col rounded-lg border border-[rgb(var(--ck-line)/.2)] bg-[rgb(var(--ck-deep)/.7)] px-[18px] py-3.5">
        <div class="text-sm text-[#6F84A6]">选中对象</div>
        <div class="mt-0.5 line-clamp-2 text-[19px] leading-snug font-semibold" :title="det.t">{{ det.t }}</div>
        <div class="truncate text-sm text-[#9FB2D1]" :title="det.sub">{{ det.sub }}</div>
        <div class="mt-3 flex flex-1 flex-col justify-around">
          <div
            v-for="r in det.rows"
            :key="r.k"
            class="min-w-0 border-t border-[rgb(var(--ck-line)/.16)] pt-2.5 whitespace-nowrap"
          >
            <div class="text-sm text-[#9FB2D1]">{{ r.k }}</div>
            <div class="yb-num truncate text-2xl font-semibold" :style="{ color: r.c }" :title="r.v">{{ r.v }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
