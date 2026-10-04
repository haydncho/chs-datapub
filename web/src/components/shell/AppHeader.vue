<script setup lang="ts">
import { LogOut, Moon, Sun } from '@lucide/vue'
import { computed } from 'vue'
import type { Tone } from '@/api/types'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useTheme } from '@/composables/useTheme'
import { toneTag } from '@/lib/tone'
import { useAuthStore } from '@/stores/auth'
import BrandLogo from './BrandLogo.vue'

/** 顶栏：品牌、医保专网、区标签；右侧主题切换与当前身份（姓名 · 角色 · 机构 / 数据范围）。全站不出现分享、复制链接、二维码入口。 */
defineProps<{ zone?: { label: string; tone: Tone } }>()
const emit = defineEmits<{ logout: [] }>()
const auth = useAuthStore()
const { resolved, toggle } = useTheme()
const u = computed(() => auth.user)
</script>

<template>
  <header class="shell-glass flex items-center gap-3.5 border-b px-6 py-3 whitespace-nowrap">
    <BrandLogo />
    <div class="text-[17px] leading-[1.15] font-bold tracking-[.04em] text-ink">医保数据公开定向发布平台</div>
    <span class="rounded-full border border-primary-line-strong px-2.5 py-[1px] text-[11px] text-primary">医保专网</span>
    <span v-if="zone" class="rounded-full border px-2.5 py-[1px] text-[11px] font-semibold" :class="toneTag[zone.tone]" data-testid="zone-tag">{{ zone.label }}</span>
    <div class="flex-1" />
    <button
      type="button"
      :aria-label="resolved === 'dark' ? '切换为浅色' : '切换为深色'"
      :title="resolved === 'dark' ? '切换为浅色' : '切换为深色'"
      class="flex size-[30px] cursor-pointer items-center justify-center rounded-full border border-line bg-surface text-ink-sub hover:bg-hover"
      @click="toggle"
    >
      <Moon v-if="resolved === 'dark'" class="size-[15px]" />
      <Sun v-else class="size-[15px]" />
    </button>
    <DropdownMenu v-if="u" :modal="false">
      <DropdownMenuTrigger as-child>
        <button
          type="button"
          class="flex cursor-pointer items-center gap-2.5 rounded-full border border-line bg-surface py-1 pr-4 pl-1 text-left hover:bg-hover"
          data-testid="account"
        >
          <span class="flex size-8 items-center justify-center rounded-full bg-primary-solid text-[13px] font-semibold text-white">{{ u.name[0] }}</span>
          <span class="flex flex-col leading-[1.35]">
            <span class="text-[13px]"><span class="font-semibold text-ink">{{ u.name }}</span><span class="text-ink-muted"> · {{ u.roleLabel }} · {{ u.org }}</span></span>
            <span class="text-[11px] text-ink-faint">数据范围:{{ u.scope }}</span>
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="w-60">
        <DropdownMenuLabel class="text-[12px] font-normal text-ink-muted">本次身份:{{ u.roleLabel }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem class="cursor-pointer text-[13px]" @select="emit('logout')">
          <LogOut class="size-4" />退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </header>
</template>
