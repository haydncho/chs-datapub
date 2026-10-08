<script setup lang="ts">
import { ref } from 'vue'
import { MoveHorizontal, Search } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import type { A12Role, A12User, A12UserStatus } from '@/mock/A12'
import { SIDE_NAME, STATUS_NAME, type Filters } from './useUsers'
import { useScrollHint } from './useScrollHint'

defineProps<{
  rows: A12User[]
  roles: A12Role[]
  filters: Filters
  sideCount: { all: number; bureau: number; org: number }
  summary: string
  busy: boolean
  isConvener: boolean
  canReview: boolean
  meName: string | null
  /** 当前登录账号:自己那一行不提供停用 */
  meLogin: string | null
}>()
const emit = defineEmits<{
  disable: [u: A12User]
  enable: [u: A12User]
  review: [u: A12User, approve: boolean]
  reset: []
}>()

/**
 * 桌面:姓名 | 角色 | 机构 | 端 | 最近登录 | 状态 | 操作(头像并入姓名列,便于冻结)。
 * Pad(< xl):列宽收紧,1024 横屏一屏放下;竖屏横滚时冻结姓名列与操作列,状态列前移紧跟姓名。
 */
const GRID = 'grid grid-cols-[minmax(166px,1fr)_minmax(150px,1.3fr)_minmax(140px,1.2fr)_76px_104px_88px_132px] max-xl:grid-cols-[minmax(160px,1fr)_76px_minmax(140px,1.3fr)_minmax(128px,1.1fr)_72px_88px_124px] items-center gap-3 max-xl:gap-2.5 px-card-x'
/** 竖屏横滚时的冻结列:姓名列(左)、操作列(右);未滚到头时显示分隔阴影 */
const STICKY_L = 'max-xl:sticky max-xl:left-0 max-xl:z-[1] max-xl:-ml-card-x max-xl:self-stretch max-xl:flex max-xl:items-center'
const STICKY_R = 'max-xl:sticky max-xl:right-0 max-xl:z-[1] max-xl:-mr-card-x max-xl:pr-card-x max-xl:pl-2.5 max-xl:-ml-2.5 max-xl:self-stretch max-xl:flex max-xl:items-center'
const SHADOW_L = 'max-xl:shadow-[6px_0_8px_-6px_rgba(15,23,42,.18)]'
const SHADOW_R = 'max-xl:shadow-[-6px_0_8px_-6px_rgba(15,23,42,.18)]'

const scroller = ref<HTMLElement | null>(null)
const hint = useScrollHint(scroller)

const STATUS_VARIANT: Record<A12UserStatus, 'ok' | 'warn' | 'muted' | 'violet'> = { on: 'ok', expiring: 'warn', off: 'muted', pending: 'violet' }
const SIDE_TABS = [['all', '全部'], ['bureau', '医保局端'], ['org', '机构端']] as const
</script>

<template>
  <div class="yb-card">
    <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2.5 border-b border-line-2 px-card-x py-3.5">
      <div class="flex min-w-0 flex-wrap items-baseline gap-x-2.5 gap-y-1">
        <span class="text-[15px] font-semibold">用户</span>
        <span class="text-xs text-ink-4">{{ summary }}</span>
        <span v-if="hint.overflow.value" class="flex items-center gap-1 self-center text-xs text-ink-5 xl:hidden" data-testid="a12-users-scroll-hint"><MoveHorizontal class="size-3.5" />左右滑动查看更多列</span>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <div class="flex rounded-lg bg-surface-2 p-0.5" role="tablist" aria-label="按端筛选" title="计数含待复核的新增申请">
          <button
            v-for="[k, label] in SIDE_TABS"
            :key="k"
            type="button"
            role="tab"
            :aria-selected="filters.side === k"
            :class="cn('h-7 max-xl:h-10 cursor-pointer rounded-md px-3 max-xl:px-4 text-xs whitespace-nowrap transition-colors', filters.side === k ? 'bg-white font-semibold text-ink-1 shadow-xs' : 'text-ink-3 hover:text-ink-1')"
            @click="filters.side = k"
          >{{ label }}<span class="yb-num ml-1 text-ink-5">{{ sideCount[k] }}</span></button>
        </div>
        <Select v-model="filters.role">
          <SelectTrigger size="sm" class="h-8 w-[132px] text-xs max-xl:h-10" data-testid="filter-role"><SelectValue placeholder="全部角色" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部角色</SelectItem>
            <SelectItem v-for="r in roles" :key="r.code" :value="r.code">{{ r.name }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="filters.status">
          <SelectTrigger size="sm" class="h-8 w-[118px] text-xs max-xl:h-10" data-testid="filter-status"><SelectValue placeholder="全部状态" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部状态</SelectItem>
            <SelectItem v-for="(label, k) in STATUS_NAME" :key="k" :value="k">{{ label }}</SelectItem>
          </SelectContent>
        </Select>
        <div class="relative max-xl:min-w-[200px] max-xl:flex-1">
          <Search class="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-ink-5" />
          <Input v-model="filters.q" placeholder="搜索姓名 / 登录名 / 机构" class="h-8 w-[200px] pl-8 text-xs max-xl:h-10 max-xl:w-full" data-testid="filter-q" />
        </div>
      </div>
    </div>

    <div ref="scroller" class="max-xl:overflow-x-auto">
    <div class="max-xl:min-w-[880px]">
    <div :class="[GRID, 'max-xl:static sticky top-(--sticky-top) z-[6] bg-surface-1 py-2.5 text-xs text-ink-4']">
      <span :class="[STICKY_L, 'pl-[46px] max-xl:-my-2.5 max-xl:py-2.5 max-xl:pl-[calc(var(--card-px)+46px)] max-xl:bg-surface-1', hint.atStart.value ? '' : SHADOW_L]">姓名 / 登录名</span>
      <span class="max-xl:order-1">角色 / 持有身份</span><span class="max-xl:order-1">所属机构</span><span class="max-xl:order-1">端</span><span class="max-xl:order-1">最近登录</span>
      <span>状态</span>
      <span :class="[STICKY_R, 'max-xl:order-2 justify-end text-right max-xl:-my-2.5 max-xl:py-2.5 max-xl:bg-surface-1', hint.atEnd.value ? '' : SHADOW_R]">操作</span>
    </div>

    <div
      v-for="u in rows"
      :key="u.login + u.status"
      :data-login="u.login"
      :class="[GRID, 'yb-tr border-b border-line-3 py-row', u.status === 'off' ? 'text-ink-4' : '']"
    >
      <div :class="[STICKY_L, 'flex min-w-0 items-center gap-4 max-xl:-my-row max-xl:py-row max-xl:pl-card-x max-xl:bg-white', hint.atStart.value ? '' : SHADOW_L]">
        <span :class="cn('flex size-[30px] shrink-0 items-center justify-center rounded-full font-semibold', u.status === 'off' ? 'bg-surface-3 text-ink-4' : 'bg-brand-soft text-brand')">{{ u.name[0] }}</span>
        <div class="min-w-0 flex-1">
          <div class="truncate font-medium">{{ u.name }}</div>
          <div class="yb-num truncate text-[11px] text-ink-5">{{ u.login }}</div>
        </div>
      </div>
      <div class="min-w-0 max-xl:order-1">
        <div class="flex items-center gap-1.5 text-xs">
          <span class="truncate">{{ u.role }}</span>
          <span v-if="u.title" class="shrink-0 text-ink-4">· {{ u.title }}</span>
        </div>
        <div
          v-if="u.identities.length > 1"
          class="truncate text-[11px] text-ink-5"
          :title="u.identities.map(i => `${i.role} · ${i.org}`).join('\n')"
        >持有 {{ u.identities.length }} 个身份:{{ u.identities.map(i => i.role).join(' / ') }}</div>
        <div v-else-if="u.status === 'pending'" class="truncate text-[11px] text-ink-5">申请人 {{ u.requestedBy }} · {{ u.requestedAt }}</div>
        <div v-else class="truncate text-[11px] text-ink-5">{{ u.scope }}</div>
      </div>
      <span class="truncate text-xs text-ink-3 max-xl:order-1" :title="u.org">{{ u.org }}</span>
      <Badge :variant="u.side === 'bureau' ? 'brand' : 'ok'" class="justify-self-start font-normal max-xl:order-1">{{ SIDE_NAME[u.side] }}</Badge>
      <span class="yb-num text-xs whitespace-nowrap text-ink-4 max-xl:order-1">{{ u.lastLogin }}</span>
      <Badge :variant="STATUS_VARIANT[u.status]" class="justify-self-start font-normal">{{ STATUS_NAME[u.status] }}</Badge>
      <div :class="[STICKY_R, 'flex max-xl:order-2 justify-end gap-1.5 max-xl:gap-2 max-xl:-my-row max-xl:py-row max-xl:bg-white', hint.atEnd.value ? '' : SHADOW_R]">
        <template v-if="u.status === 'pending'">
          <template v-if="canReview && u.requestedBy !== meName">
            <Button size="sm" class="h-7 px-2.5 max-xl:h-10" :disabled="busy" @click="emit('review', u, true)">通过</Button>
            <Button size="sm" variant="outline" class="h-7 px-2.5 font-normal max-xl:h-10" :disabled="busy" @click="emit('review', u, false)">驳回</Button>
          </template>
          <span v-else class="text-[11px] text-ink-5">等待另一人复核</span>
        </template>
        <span v-else-if="meLogin && u.login === meLogin" class="text-[11px] text-ink-5">当前登录账号</span>
        <Button
          v-else-if="u.status === 'off'"
          size="sm" variant="soft" class="h-7 px-3 max-xl:h-10" :disabled="busy || !isConvener"
          :title="isConvener ? '' : '仅召集人可操作'"
          @click="emit('enable', u)"
        >启用</Button>
        <Button
          v-else
          size="sm" variant="outline" class="h-7 px-3 font-normal text-bad-ink! max-xl:h-10" :disabled="busy || !isConvener"
          :title="isConvener ? '' : '仅召集人可操作'"
          @click="emit('disable', u)"
        >停用</Button>
      </div>
    </div>
    </div>
    </div>

    <div v-if="rows.length === 0" class="flex flex-col items-center gap-2 px-card-x py-10 text-[13px] text-ink-4">
      没有符合条件的用户
      <Button size="sm" variant="outline" class="font-normal" @click="emit('reset')">清除筛选</Button>
    </div>
  </div>
</template>
