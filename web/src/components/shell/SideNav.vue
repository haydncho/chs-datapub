<script setup lang="ts">
import { computed, ref } from 'vue'
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { iconPaths } from '@/lib/icons'
import { navGroups, pagePath } from '@/lib/nav'
import { useAuthStore } from '@/stores/auth'

/**
 * 业务路径导轨(按身份裁剪):四个业务阶段按 01 → 04 编号并用竖线串联,
 * 驾驶舱、系统管理、导出等非阶段分组以小标题区分;可收起为 68px,收起时悬停显示名称。
 */
const auth = useAuthStore()
const groups = computed(() => navGroups(auth.user?.pages ?? []))
const STAGES = ['数据底座', '指标与算法', '发布管理', '发布后响应']
const stageNo = (label: string) => STAGES.indexOf(label) + 1
const collapsed = ref(false)
const tipClass = 'rounded-[3px] bg-tip px-[11px] py-[7px] text-[12px] leading-none font-medium text-tip-fg'
</script>

<template>
  <nav
    class="sticky top-(--shell-top) z-[15] flex h-[calc(100dvh-var(--shell-top,0px))] flex-none flex-col self-start border-r border-line-strong bg-surface pt-4 pb-4 transition-[width] duration-[240ms]"
    :class="collapsed ? 'w-[68px]' : 'w-[216px]'"
    data-testid="side-nav"
  >
    <div class="side-scroll min-h-0 flex-1 pb-3">
      <div v-for="g in groups" :key="g.label" class="relative mb-4 px-3">
        <!-- 阶段标题 -->
        <div v-if="!collapsed" class="flex items-center gap-2.5 px-1 pb-1.5">
          <span
            v-if="stageNo(g.label)"
            class="stage-no flex size-[22px] flex-none items-center justify-center border border-ink font-display text-[12px] font-bold text-ink"
          >{{ stageNo(g.label) }}</span>
          <span v-else class="size-[22px] flex-none text-center text-[10px] leading-[22px] text-ink-faint">◆</span>
          <span class="font-display text-[13px] font-bold tracking-[.12em] text-ink">{{ g.label }}</span>
        </div>
        <div v-else class="mx-2 mb-2 h-px bg-line-strong" />
        <div class="relative" :class="!collapsed && stageNo(g.label) ? 'ml-[11px] border-l border-line-strong pl-3' : !collapsed ? 'ml-[11px] pl-3' : ''">
          <RouterLink v-for="item in g.items" :key="item.key" v-slot="{ href, navigate, isActive }" :to="pagePath(item)" custom>
            <Tooltip :disabled="!collapsed">
              <TooltipTrigger as-child>
                <a
                  :href="href"
                  class="relative flex cursor-pointer items-center gap-2.5 py-[7px] text-[13px] no-underline transition-colors hover:no-underline"
                  :class="[
                    collapsed ? 'justify-center px-0' : 'justify-start pr-2',
                    isActive ? 'font-bold text-ink' : 'text-ink-sub hover:text-ink',
                  ]"
                  :data-nav="item.key"
                  @click="navigate"
                >
                  <span v-if="isActive && !collapsed" class="absolute top-1/2 -left-[15px] size-[7px] -translate-y-1/2 rotate-45 bg-[#C2371F]" />
                  <span v-if="isActive" class="absolute inset-y-0.5 -right-1 -left-2 -z-10 bg-hover" />
                  <StrokeIcon :d="iconPaths[item.icon]" :size="16" class="flex-none" :class="isActive ? 'text-[#C2371F]' : 'opacity-70'" />
                  <template v-if="!collapsed">
                    <span class="nav-fade whitespace-nowrap">{{ item.label }}</span>
                  </template>
                </a>
              </TooltipTrigger>
              <TooltipContent side="right" :side-offset="10" :class="tipClass">{{ item.label }}</TooltipContent>
            </Tooltip>
          </RouterLink>
        </div>
      </div>
    </div>
    <div
      class="mx-3 mt-2 flex flex-none cursor-pointer items-center gap-2 overflow-hidden border border-line-strong bg-surface px-2.5 py-2 text-[12px] whitespace-nowrap text-ink-muted hover:bg-hover hover:text-ink"
      :class="collapsed ? 'justify-center' : 'justify-start'"
      role="button"
      tabindex="0"
      @click="collapsed = !collapsed"
      @keydown.enter.prevent="collapsed = !collapsed"
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="flex-none">
        <rect x="3" y="4" width="18" height="16" rx="1" />
        <path d="M9 4v16" />
        <path :d="collapsed ? 'M13 10l2 2-2 2' : 'M16 10l-2 2 2 2'" />
      </svg>
      <span v-if="!collapsed" class="nav-fade">收起导轨</span>
    </div>
  </nav>
</template>
