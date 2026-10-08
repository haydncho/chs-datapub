<script setup lang="ts">
import { computed } from 'vue'
import { splitUnit } from '@/lib/format'

/**
 * KPI 读数:全站统一的数值字号 + 更小的单位。
 * 单位取 `unit`,未给时从 `value` 末尾拆出("52 机构" → 52 + 机构);
 * 纯文字读数("进行中""已截止")自动降一档,不和数字抢视觉。
 * size:lg 24px(Pad 22)主指标卡 · md 20px 紧凑卡 / 流水线 · sm 18px 卡内小读数
 * 行距:块级、固定行高(lg 30/28 · md 26 · sm 24),与上方标签、下方说明各隔 4px(.yb-kpi-v,style.css);横排场景传 class="mt-0"。
 */
export type KpiSize = 'lg' | 'md' | 'sm'
const props = withDefaults(defineProps<{ value: string | number; unit?: string; size?: KpiSize }>(), { size: 'lg' })

/* 读数行固定行高:数字和纯文字读数占同样高度,同一排卡片的说明行对齐、卡片等高 */
const LINE: Record<KpiSize, string> = { lg: 'leading-[30px] max-xl:leading-[28px]', md: 'leading-[26px]', sm: 'leading-6' }
const NUM: Record<KpiSize, string> = { lg: 'text-2xl max-xl:text-[22px]', md: 'text-xl', sm: 'text-lg' }
const WORD: Record<KpiSize, string> = { lg: 'text-lg', md: 'text-base', sm: 'text-[15px]' }
const UNIT: Record<KpiSize, string> = { lg: 'text-[13px]', md: 'text-xs', sm: 'text-xs' }

const parts = computed(() => (props.unit != null ? { vn: String(props.value), vu: props.unit } : splitUnit(props.value)))
const isWord = computed(() => !/\d/.test(parts.value.vn))
</script>

<template>
  <span :class="['yb-kpi-v yb-num block font-semibold whitespace-nowrap', LINE[size], isWord ? WORD[size] : NUM[size]]">{{ parts.vn }}<span v-if="parts.vu" :class="['ml-[3px] font-medium text-ink-4', UNIT[size]]">{{ parts.vu }}</span></span>
</template>
