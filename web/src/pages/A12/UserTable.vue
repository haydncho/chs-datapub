<script setup lang="ts">
import { Search } from '@lucide/vue'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import type { A12Role, A12User, A12UserStatus } from '@/mock/A12'
import { SIDE_NAME, STATUS_NAME, type Filters } from './useUsers'

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
}>()
const emit = defineEmits<{
  disable: [u: A12User]
  enable: [u: A12User]
  review: [u: A12User, approve: boolean]
  reset: []
}>()

const GRID = 'grid grid-cols-[34px_minmax(120px,1fr)_minmax(150px,1.3fr)_minmax(140px,1.2fr)_76px_104px_88px_132px] items-center gap-3 px-card-x'

const STATUS_VARIANT: Record<A12UserStatus, 'ok' | 'warn' | 'muted' | 'violet'> = { on: 'ok', expiring: 'warn', off: 'muted', pending: 'violet' }
const SIDE_TABS = [['all', '全部'], ['bureau', '医保局端'], ['org', '机构端']] as const
</script>

<template>
  <div class="yb-card">
    <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2.5 border-b border-line-2 px-card-x py-3.5">
      <div class="flex items-baseline gap-2.5">
        <span class="text-[15px] font-semibold">用户</span>
        <span class="text-xs text-ink-4">{{ summary }}</span>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <div class="flex rounded-lg bg-surface-2 p-0.5" role="tablist" aria-label="按端筛选">
          <button
            v-for="[k, label] in SIDE_TABS"
            :key="k"
            type="button"
            role="tab"
            :aria-selected="filters.side === k"
            :class="cn('h-7 cursor-pointer rounded-md px-3 text-xs whitespace-nowrap transition-colors', filters.side === k ? 'bg-white font-semibold text-ink-1 shadow-xs' : 'text-ink-3 hover:text-ink-1')"
            @click="filters.side = k"
          >{{ label }}<span class="yb-num ml-1 text-ink-5">{{ sideCount[k] }}</span></button>
        </div>
        <Select v-model="filters.role">
          <SelectTrigger size="sm" class="h-8 w-[132px] text-xs" data-testid="filter-role"><SelectValue placeholder="全部角色" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部角色</SelectItem>
            <SelectItem v-for="r in roles" :key="r.code" :value="r.code">{{ r.name }}</SelectItem>
          </SelectContent>
        </Select>
        <Select v-model="filters.status">
          <SelectTrigger size="sm" class="h-8 w-[118px] text-xs" data-testid="filter-status"><SelectValue placeholder="全部状态" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部状态</SelectItem>
            <SelectItem v-for="(label, k) in STATUS_NAME" :key="k" :value="k">{{ label }}</SelectItem>
          </SelectContent>
        </Select>
        <div class="relative">
          <Search class="pointer-events-none absolute top-1/2 left-2.5 size-3.5 -translate-y-1/2 text-ink-5" />
          <Input v-model="filters.q" placeholder="搜索姓名 / 登录名 / 机构" class="h-8 w-[200px] pl-8 text-xs" data-testid="filter-q" />
        </div>
      </div>
    </div>

    <div :class="[GRID, 'sticky top-(--sticky-top) z-[6] bg-surface-1 py-2.5 text-xs text-ink-4']">
      <span /><span>姓名 / 登录名</span><span>角色 / 持有身份</span><span>所属机构</span><span>端</span><span>最近登录</span><span>状态</span><span class="text-right">操作</span>
    </div>

    <div
      v-for="u in rows"
      :key="u.login + u.status"
      :data-login="u.login"
      :class="[GRID, 'border-b border-line-3 py-row [&:nth-child(even)]:bg-(--zebra-bg)', u.status === 'off' ? 'text-ink-4' : '']"
    >
      <span :class="cn('flex size-[30px] items-center justify-center rounded-full font-semibold', u.status === 'off' ? 'bg-surface-3 text-ink-4' : 'bg-brand-soft text-brand')">{{ u.name[0] }}</span>
      <div class="min-w-0">
        <div class="truncate font-medium">{{ u.name }}</div>
        <div class="yb-num truncate text-[11px] text-ink-5">{{ u.login }}</div>
      </div>
      <div class="min-w-0">
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
      <span class="truncate text-xs text-ink-3" :title="u.org">{{ u.org }}</span>
      <Badge :variant="u.side === 'bureau' ? 'brand' : 'ok'" class="justify-self-start font-normal">{{ SIDE_NAME[u.side] }}</Badge>
      <span class="text-xs text-ink-4">{{ u.lastLogin }}</span>
      <Badge :variant="STATUS_VARIANT[u.status]" class="justify-self-start font-normal">{{ STATUS_NAME[u.status] }}</Badge>
      <div class="flex justify-end gap-1.5">
        <template v-if="u.status === 'pending'">
          <template v-if="canReview && u.requestedBy !== meName">
            <Button size="sm" class="h-7 px-2.5" :disabled="busy" @click="emit('review', u, true)">通过</Button>
            <Button size="sm" variant="outline" class="h-7 px-2.5 font-normal" :disabled="busy" @click="emit('review', u, false)">驳回</Button>
          </template>
          <span v-else class="text-[11px] text-ink-5">等待另一人复核</span>
        </template>
        <Button
          v-else-if="u.status === 'off'"
          size="sm" variant="soft" class="h-7 px-3" :disabled="busy || !isConvener"
          :title="isConvener ? '' : '仅召集人可操作'"
          @click="emit('enable', u)"
        >启用</Button>
        <Button
          v-else
          size="sm" variant="outline" class="h-7 px-3 font-normal text-bad-ink!" :disabled="busy || !isConvener"
          :title="isConvener ? '' : '仅召集人可操作'"
          @click="emit('disable', u)"
        >停用</Button>
      </div>
    </div>

    <div v-if="rows.length === 0" class="flex flex-col items-center gap-2 px-card-x py-10 text-[13px] text-ink-4">
      没有符合条件的用户
      <Button size="sm" variant="outline" class="font-normal" @click="emit('reset')">清除筛选</Button>
    </div>
  </div>
</template>
