<script setup lang="ts">
import { computed, ref } from 'vue'
import StrokeIcon from '@/components/ui/stroke-icon/StrokeIcon.vue'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { iconPaths } from '@/lib/icons'
import { navGroups, pagePath } from '@/lib/nav'
import { useAuthStore } from '@/stores/auth'

/** 分组侧栏（按身份裁剪）：201px ↔ 65px，可收起；收起时悬停显示名称。 */
const auth = useAuthStore()
const groups = computed(() => navGroups(auth.user?.pages ?? []))
const collapsed = ref(false)
const tipClass = 'rounded-[7px] bg-tip px-[11px] py-[7px] text-[12px] leading-none font-medium text-tip-fg'
</script>

<template>
  <nav
    class="sticky top-(--shell-top) z-[15] flex h-[calc(100dvh-var(--shell-top,0px))] flex-none flex-col self-start border-r border-line bg-surface pt-4 pb-4 transition-[width] duration-[240ms]"
    :class="collapsed ? 'w-[68px]' : 'w-[212px]'"
    data-testid="side-nav"
  >
    <div class="side-scroll min-h-0 flex-1 pb-3">
      <div v-for="g in groups" :key="g.label" class="mb-3.5 px-2.5">
        <div v-if="!collapsed" class="px-3 pt-1 pb-2 text-[11px] font-medium tracking-[.08em] text-ink-faint">{{ g.label }}</div>
        <div v-else class="mx-2 mb-2 h-px bg-divider" />
        <RouterLink v-for="item in g.items" :key="item.key" v-slot="{ href, navigate, isActive }" :to="pagePath(item)" custom>
          <Tooltip :disabled="!collapsed">
            <TooltipTrigger as-child>
              <a
                :href="href"
                class="relative mb-0.5 flex cursor-pointer items-center gap-2.5 rounded-lg py-2 text-[13px] no-underline transition-colors hover:no-underline"
                :class="[
                  collapsed ? 'justify-center px-0' : 'justify-start px-3',
                  isActive ? 'bg-primary-tint font-semibold text-primary' : 'bg-transparent text-ink-sub hover:bg-hover',
                ]"
                :data-nav="item.key"
                @click="navigate"
              >
                                <StrokeIcon :d="iconPaths[item.icon]" :size="17" class="flex-none opacity-90" />
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
    <div
      class="mx-2.5 mt-2 flex flex-none cursor-pointer items-center gap-2 overflow-hidden rounded-md border border-line bg-subtle px-2.5 py-2 text-[12px] whitespace-nowrap text-ink-muted hover:border-ring hover:bg-primary-tint hover:text-primary"
      :class="collapsed ? 'justify-center' : 'justify-start'"
      role="button"
      tabindex="0"
      @click="collapsed = !collapsed"
      @keydown.enter.prevent="collapsed = !collapsed"
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="flex-none">
        <rect x="3" y="4" width="18" height="16" rx="2" />
        <path d="M9 4v16" />
        <path :d="collapsed ? 'M13 10l2 2-2 2' : 'M16 10l-2 2 2 2'" />
      </svg>
      <span v-if="!collapsed" class="nav-fade">收起菜单</span>
    </div>
  </nav>
</template>
