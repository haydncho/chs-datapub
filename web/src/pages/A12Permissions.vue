<script setup lang="ts">
import { PageHeader, PageSection } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { usePageData, sendAction } from '@/api/client'
import { say } from '@/app/shell'
import { A12_SEED, type A12Grant } from '@/mock/A12'

const data = usePageData('A12', A12_SEED)

/** matrix cell colours by grant */
const GRANT_CLASS: Record<A12Grant, string> = {
  全: 'bg-brand-soft text-brand',
  本院: 'bg-ok-soft text-ok-ink',
  本县: 'bg-ok-soft text-ok-ink',
  汇总: 'bg-surface-3 text-ink-3',
  分位: 'bg-surface-3 text-ink-3',
  聚合: 'bg-surface-3 text-ink-3',
  脱敏: 'bg-warn-soft text-warn-ink',
  受控: 'bg-violet-soft text-violet',
  '—': 'bg-[#FAFBFC] text-ink-6',
}

const GRID = 'grid grid-cols-[150px_repeat(7,minmax(0,1fr))] gap-1.5 px-5'
const USER_GRID = 'grid grid-cols-[36px_minmax(160px,1fr)_140px_minmax(160px,1fr)_110px_90px] items-center gap-3.5'

function addUser() {
  sendAction('A12', 'requestAddUser', {})
  say('新增用户需双人复核 · 已发送复核申请')
}
</script>

<template>
  <PageSection label="A12 用户权限">
    <PageHeader title="用户与权限" subtitle="角色 × 数据范围 · 最小必要原则 · 变更需双人复核">
      <Button @click="addUser">+ 新增用户</Button>
    </PageHeader>

    <div class="yb-card overflow-hidden">
      <div class="border-b border-line-2 px-card-x py-3.5 text-[15px] font-semibold">权限矩阵</div>
      <div :class="[GRID, 'bg-surface-1 py-2.5 text-xs text-ink-4']">
        <span>角色</span>
        <span v-for="c in data.scopes" :key="c" class="text-center">{{ c }}</span>
      </div>
      <div v-for="r in data.roles" :key="r.name" :class="[GRID, 'items-center border-b border-line-3 py-2']">
        <div>
          <div class="font-semibold">{{ r.name }}</div>
          <div class="text-[11px] text-ink-5">{{ r.users }} 人</div>
        </div>
        <span
          v-for="(g, i) in r.grants"
          :key="i"
          :class="['rounded-md px-1 py-[7px] text-center text-xs font-medium', GRANT_CLASS[g]]"
        >{{ g }}</span>
      </div>
    </div>

    <div class="yb-card overflow-hidden">
      <div class="flex justify-between border-b border-line-2 px-card-x py-3.5">
        <span class="text-[15px] font-semibold">用户</span>
        <span class="text-xs text-ink-4">{{ data.userSummary }}</span>
      </div>
      <div v-for="u in data.users" :key="u.name" :class="[USER_GRID, 'border-b border-line-3 px-card-x py-row']">
        <span class="flex size-[30px] items-center justify-center rounded-full bg-brand-soft font-semibold text-brand">{{ u.name[0] }}</span>
        <div>
          <div class="font-medium">{{ u.name }}</div>
          <div class="text-[11px] text-ink-5">{{ u.org }}</div>
        </div>
        <span class="text-xs">{{ u.role }}</span>
        <span class="text-xs text-ink-3">{{ u.scope }}</span>
        <span class="text-xs text-ink-4">{{ u.lastLogin }}</span>
        <Badge :variant="u.status === 'on' ? 'ok' : 'warn'" class="justify-self-start font-normal">
          {{ u.status === 'on' ? '启用' : '即将停用' }}
        </Badge>
      </div>
    </div>
  </PageSection>
</template>
