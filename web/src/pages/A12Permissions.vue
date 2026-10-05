<script setup lang="ts">
import { computed, ref } from 'vue'
import { PageHeader, PageSection, StatCard } from '@/components/yb'
import { Button } from '@/components/ui/button'
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from '@/components/ui/alert-dialog'
import { usePageData } from '@/api/client'
import { A12_SEED, type A12User } from '@/mock/A12'
import AddUserDialog from './A12/AddUserDialog.vue'
import RoleMatrix from './A12/RoleMatrix.vue'
import UserTable from './A12/UserTable.vue'
import { useUsers } from './A12/useUsers'

const data = usePageData('A12', A12_SEED)
const s = useUsers(data)

const addOpen = ref(false)
const target = ref<A12User | null>(null)
const confirmOpen = ref(false)

function askDisable(u: A12User) {
  target.value = u
  confirmOpen.value = true
}

async function confirmDisable() {
  const u = target.value
  confirmOpen.value = false
  if (u) await s.setEnabled(u, false)
}

const ICON = {
  users: 'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  bureau: 'M3 21h18M5 21V7l8-4v18M19 21V11l-6-4M9 9v.01M9 13v.01M9 17v.01',
  org: 'M3 21h18M6 21V10l6-5 6 5v11M10 21v-6h4v6',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  review: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M9 15l2 2 4-4',
}
const sm = computed(() => data.value.summary)
</script>

<template>
  <PageSection label="A12 用户权限">
    <PageHeader class="max-xl:flex-wrap" title="用户与权限" subtitle="以系统真实的用户、角色与访问矩阵为准 · 最小必要原则 · 新增用户需双人复核">
      <Button :disabled="!s.isConvener" :title="s.isConvener ? '' : '仅召集人可操作'" @click="addOpen = true">+ 新增用户申请</Button>
    </PageHeader>

    <div class="grid grid-cols-2 gap-3.5 md:grid-cols-3 xl:grid-cols-5" data-testid="a12-stats">
      <StatCard label="用户总数" :value="sm.total" unit="人" :sub="`含已停用 ${sm.disabled} 人`" tone="info" :icon="ICON.users" />
      <StatCard label="医保局端" :value="sm.bureau" unit="人" sub="召集人 · 行政 · 分析 · 审计" tone="info" :icon="ICON.bureau" />
      <StatCard label="机构端" :value="sm.org" unit="人" sub="医院 · 县区 · 社会监督" tone="ok" :icon="ICON.org" />
      <StatCard label="即将停用" :value="sm.expiring" unit="人" sub="超过 30 天未登录" :tone="sm.expiring ? 'warn' : 'ok'" :icon="ICON.clock" />
      <StatCard label="待复核" :value="sm.pending" unit="项" sub="新增用户申请" :tone="sm.pending ? 'bad' : 'ok'" :icon="ICON.review" />
    </div>

    <RoleMatrix :data="data" />

    <UserTable
      :rows="s.rows.value"
      :roles="data.roles"
      :filters="s.filters"
      :side-count="s.sideCount.value"
      :summary="data.userSummary"
      :busy="s.busy.value"
      :is-convener="s.isConvener.value"
      :can-review="s.canReview.value"
      :me-name="s.meName.value"
      @disable="askDisable"
      @enable="u => s.setEnabled(u, true)"
      @review="(u, ok) => s.review(u, ok)"
      @reset="s.resetFilters()"
    />

    <AddUserDialog v-model:open="addOpen" :roles="data.roles" :busy="s.busy.value" :submit="s.requestAdd" />

    <AlertDialog v-model:open="confirmOpen">
      <AlertDialogContent class="z-[96]">
        <AlertDialogHeader>
          <AlertDialogTitle>停用账号 {{ target?.name }}({{ target?.login }})?</AlertDialogTitle>
          <AlertDialogDescription>停用后该账号不能再登录,已登录的会话立即作废;可随时重新启用。此操作将写入审计日志。</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel>取消</AlertDialogCancel>
          <AlertDialogAction @click="confirmDisable">确认停用</AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  </PageSection>
</template>
