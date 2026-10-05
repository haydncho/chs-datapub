<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown, LogOut, Repeat } from '@lucide/vue'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import type { Viewer } from '@/app/nav'

/** 用户区:姓名 · 角色 · 范围;头像旁菜单提供 切换身份 / 退出登录(仅登录后)。 */
const props = defineProps<{ viewer: Viewer; loggedIn: boolean; canSwitch: boolean }>()
const emit = defineEmits<{ switch: []; logout: [] }>()
const initial = computed(() => props.viewer.name[0] ?? '')
</script>

<template>
  <div class="flex shrink-0 items-center gap-2.5">
    <div class="min-w-0 text-right leading-[1.3]">
      <div class="text-[13px] font-medium whitespace-nowrap">
        {{ viewer.name }} <span class="font-normal text-ink-4">· {{ viewer.role }}</span>
      </div>
      <div class="max-w-[200px] truncate text-[11px] text-ink-4" :title="viewer.scope">{{ viewer.scope }}</div>
    </div>
    <DropdownMenu v-if="loggedIn">
      <DropdownMenuTrigger as-child>
        <button type="button" class="yb-user-btn" aria-label="账号菜单" data-testid="user-menu">
          <span class="yb-user-av">{{ initial }}</span>
          <ChevronDown class="size-3.5 text-ink-4" aria-hidden="true" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="min-w-[200px]">
        <DropdownMenuLabel class="font-normal">
          <div class="text-[13px] font-medium text-ink-1">{{ viewer.name }}</div>
          <div class="text-xs text-ink-4">{{ viewer.role }}</div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem v-if="canSwitch" class="cursor-pointer" @select="emit('switch')">
          <Repeat class="size-4" aria-hidden="true" />切换身份
        </DropdownMenuItem>
        <DropdownMenuItem class="cursor-pointer text-bad focus:text-bad" data-testid="logout" @select="emit('logout')">
          <LogOut class="size-4" aria-hidden="true" />退出登录 / 改选端
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
    <span v-else class="yb-user-av">{{ initial }}</span>
  </div>
</template>
