<script setup lang="ts">
import { cn } from '@/lib/utils'
import type { A7Source, A7Version } from '@/mock/A7'

export type A7Tab = '批注' | '数据核查' | '版本'
export interface CommentView {
  k: number
  sec: string
  section: number
  who: string
  role: string
  text: string
  time: string
  resolved: boolean
  /** pushed to 意见与申诉 (A10) as this item */
  pushedAs?: string
}

defineProps<{
  comments: CommentView[]
  active: number
  activeName: string
  checks: A7Source[]
  checkNote: string
  versions: A7Version[]
  /** not handled and not yet pushed */
  pending: number
  /** not handled */
  unresolved: number
}>()
const tab = defineModel<A7Tab>('tab', { required: true })
defineEmits<{ resolve: [k: number]; export: [] }>()

const TABS: A7Tab[] = ['批注', '数据核查', '版本']
</script>

<template>
  <aside class="sticky top-[calc(var(--sticky-top)+136px)] flex flex-col gap-3 py-5 pr-6 max-xl:static max-xl:px-4 max-xl:pt-0 max-xl:pb-10">
    <div class="yb-card rounded-xl">
      <div class="flex gap-5 border-b border-line-2 px-4" role="tablist">
        <button
          v-for="t in TABS"
          :key="t"
          type="button"
          role="tab"
          :aria-selected="t === tab"
          :class="cn(
            'cursor-pointer border-b-2 py-[11px] max-xl:min-h-11',
            t === tab ? 'border-ink-1 font-semibold text-ink-1' : 'border-transparent font-normal text-ink-4',
          )"
          @click="tab = t"
        >{{ t }}</button>
      </div>
      <div class="flex max-h-[calc(100vh-300px)] flex-col max-xl:max-h-none gap-2.5 overflow-y-auto px-4 py-3.5">
        <template v-if="tab === '批注'">
          <button
            type="button"
            class="h-8 shrink-0 cursor-pointer max-xl:h-11 rounded-lg border border-dashed border-brand-line bg-brand-tint text-xs font-medium whitespace-nowrap text-brand hover:brightness-[.98]"
            @click="$emit('export')"
          >导出为意见单 · {{ unresolved }} 条未处理{{ pending < unresolved ? '(' + (unresolved - pending) + ' 条已推送)' : '' }}</button>
          <div v-if="comments.length === 0" class="rounded-[10px] bg-surface-1 px-3 py-4 text-center text-xs text-ink-4">暂无批注 · 提交核对后机构与专家组的意见会出现在这里</div>
          <div
            v-for="c in comments"
            :key="c.k"
            :class="cn(
              'rounded-[10px] border border-line-2 p-3 transition-opacity',
              c.section === active ? 'bg-brand-tint' : 'bg-white',
              c.resolved && 'opacity-50',
            )"
          >
            <div class="flex items-center gap-2">
              <span class="flex size-6 items-center justify-center rounded-full bg-violet-soft text-[11px] font-semibold text-violet">{{ c.who[0] }}</span>
              <div class="flex-1 leading-[1.3]">
                <div class="text-xs font-medium">{{ c.who }}</div>
                <div class="text-[11px] text-ink-5">{{ c.role }} · {{ c.time }}</div>
              </div>
            </div>
            <div class="mt-2 text-[11px] text-brand">§ {{ c.sec }}</div>
            <div class="mt-0.5 text-xs leading-[1.6] text-ink-2">{{ c.text }}</div>
            <span v-if="c.pushedAs && !c.resolved" class="mt-1.5 mr-3 inline-block text-xs text-violet">已推送至意见与申诉 · {{ c.pushedAs }}</span>
            <span v-if="c.resolved" class="mt-1.5 inline-block text-xs text-ok-ink">✓ 已处理</span>
            <span
              v-else
              role="button"
              tabindex="0"
              class="mt-1.5 inline-block cursor-pointer text-xs text-brand hover:underline max-xl:-mb-1.5 max-xl:py-3"
              @click="$emit('resolve', c.k)"
              @keydown.enter="$emit('resolve', c.k)"
            >标记已处理</span>
          </div>
        </template>

        <template v-else-if="tab === '数据核查'">
          <div class="text-xs text-ink-4">§ {{ activeName }} · 每个数字可追溯</div>
          <div
            v-for="c in checks"
            :key="c.value + c.source"
            class="flex justify-between gap-2.5 rounded-[10px] bg-surface-1 px-3 py-2.5 text-xs"
          >
            <span class="yb-num font-semibold whitespace-nowrap text-brand">{{ c.value }}</span>
            <span class="text-right text-ink-3">{{ c.source }}</span>
          </div>
          <div v-if="checks.length === 0" class="rounded-[10px] bg-surface-1 px-3 py-4 text-center text-xs text-ink-4">本段没有绑定数据源的数字(文字建议段或初稿待生成)</div>
          <div class="text-[11px] text-ink-5">{{ checkNote }}</div>
        </template>

        <template v-else>
          <div class="flex flex-col gap-2.5 border-l-2 border-line-2 pl-3 text-xs">
            <div v-for="v in versions" :key="v.title">
              <div :class="v.current ? 'font-semibold' : 'font-medium'">{{ v.title }}</div>
              <div class="text-ink-4">{{ v.meta }}</div>
            </div>
          </div>
        </template>
      </div>
    </div>
  </aside>
</template>
