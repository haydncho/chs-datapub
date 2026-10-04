<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '@/api'
import type { AccountRow, OrgUnit, PermRole } from '@/api/types'
import Chip from '@/components/shared/Chip.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import SegTabs from '@/components/shared/SegTabs.vue'
import Tag from '@/components/shared/Tag.vue'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/**
 * A12 用户权限管理：组织树（医共体为虚拟组织）；角色权限矩阵、五维配置、账号生命周期。
 * 三员分立：安全管理员可管理授权、不能查看业务数据；授权操作需第二名管理员复核。
 */
const page = pageDef('A12')!
const tab = ref<'matrix' | 'dims' | 'life'>('matrix')
const orgs = ref<OrgUnit[]>([])
const columns = ref<string[]>([])
const roles = ref<PermRole[]>([])
const accounts = ref<AccountRow[]>([])
const org = ref('示例市医保局')
const roleId = ref(0)

onMounted(async () => {
  try {
    const [o, r, a] = await Promise.all([adminApi.orgs(), adminApi.roles(), adminApi.accounts()])
    orgs.value = o
    columns.value = r.columns
    roles.value = r.roles
    accounts.value = a
  } catch (e) {
    notifyError(e)
  }
})

const role = computed(() => roles.value.find((r) => r.id === roleId.value))
const cellCls = (c: string) => (c === '✓' ? 'bg-success-soft text-success-ink' : c === '—' ? 'text-ink-ghost' : 'bg-primary-tint text-primary')

async function operate(target: string, action: string) {
  try {
    notify((await adminApi.operate(target, action)).message)
  } catch (e) {
    notifyError(e)
  }
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div class="mt-5 rounded-[10px] border border-warning-line bg-warning-soft px-4 py-2.5 text-[12px] text-warning-ink" data-testid="three-roles">
      三员分立:系统管理员、安全管理员、审计员互不兼任。当前账号为<b>安全管理员</b>,可管理授权,不能查看业务数据,也不能修改审计日志。
    </div>

    <div class="mt-3.5 grid grid-cols-[240px_minmax(0,1fr)] items-start gap-3.5">
      <div class="rounded-2xl border border-line-soft bg-surface shadow-card px-1.5 py-2.5" data-testid="org-tree">
        <div class="px-2 pb-2 text-[13px] font-semibold text-ink">组织树</div>
        <button
          v-for="o in orgs"
          :key="o.id"
          type="button"
          class="flex w-full cursor-pointer items-center gap-1.5 rounded-lg py-[5px] pr-2 text-left text-[12px]"
          :class="o.name === org ? 'bg-primary-tint font-semibold text-primary' : 'text-ink hover:bg-hover'"
          :style="{ paddingLeft: `${o.level * 14 + 8}px` }"
          @click="org = o.name"
        >
          <span class="flex-1">{{ o.name }}</span>
          <span v-if="o.virtual" class="text-[10px] text-warning">虚拟组织</span>
        </button>
      </div>

      <div>
        <div class="mb-3 flex items-center gap-3">
          <SegTabs
            v-model="tab"
            :items="[
              { value: 'matrix', label: '角色权限矩阵' },
              { value: 'dims', label: '五维配置' },
              { value: 'life', label: '账号生命周期' },
            ]"
          />
          <span class="ml-auto text-[12px] text-ink-muted">当前组织:{{ org }}</span>
        </div>

        <template v-if="tab === 'matrix'">
          <table class="data-table" data-testid="perm-matrix">
            <thead><tr><th>角色 \ 权限</th><th v-for="c in columns" :key="c" class="text-center">{{ c }}</th></tr></thead>
            <tbody>
              <tr v-for="r in roles" :key="r.id" class="cursor-pointer" :data-on="r.id === roleId" @click="(roleId = r.id), (tab = 'dims')">
                <td class="font-medium text-ink">{{ r.name }}</td>
                <td v-for="(c, i) in r.matrix" :key="i" class="text-center">
                  <span class="inline-block min-w-[44px] rounded-md px-2 py-0.5 font-medium" :class="cellCls(c)">{{ c }}</span>
                </td>
              </tr>
            </tbody>
          </table>
          <div class="mt-2.5 text-[11px] text-ink-faint">点击角色行查看五维配置 · “申请”= 单次审批后生效</div>
        </template>

        <div v-else-if="tab === 'dims'" class="rounded-2xl border border-line-soft bg-surface shadow-card px-[18px] py-4">
          <div class="mb-3.5 flex flex-wrap gap-1.5">
            <Chip v-for="r in roles" :key="r.id" :on="r.id === roleId" @click="roleId = r.id">{{ r.name }}</Chip>
          </div>
          <div class="mb-2 text-[14px] font-semibold text-ink">{{ role?.name }} · 五个控制维度</div>
          <div v-for="d in role?.dims ?? []" :key="d.key" class="grid grid-cols-[100px_1fr_60px] items-center gap-2.5 border-b border-divider py-2.5 text-[12px]">
            <span class="text-ink-muted">{{ d.key }}</span><span class="text-ink">{{ d.value }}</span>
            <button type="button" class="cursor-pointer text-right text-primary" @click="operate(`${role?.name} · ${d.key}`, '编辑权限维度')">编辑</button>
          </div>
        </div>

        <template v-else>
          <div class="mb-3 flex flex-wrap items-center gap-1.5 text-[12px] text-ink-sub">
            <span class="rounded-full bg-chip px-2.5 py-[3px]">开户(机构推荐名单)</span>→
            <span class="rounded-full bg-chip px-2.5 py-[3px]">授权</span>→
            <span class="rounded-full bg-chip px-2.5 py-[3px]">季度复核</span>→
            <span class="rounded-full bg-chip px-2.5 py-[3px]">变更回收</span>
            <Tag tone="warning" class="ml-3">临时授权 · 到期自动失效</Tag>
          </div>
          <table class="data-table" data-testid="accounts">
            <thead><tr><th>姓名</th><th>组织</th><th>角色</th><th>阶段</th><th>说明</th><th class="text-right">操作</th></tr></thead>
            <tbody class="text-ink-sub">
              <tr v-for="a in accounts" :key="a.id">
                <td class="font-medium text-ink">{{ a.name }}</td>
                <td>{{ a.org }}</td>
                <td>{{ a.role }}</td>
                <td><Tag :tone="a.tone">{{ a.stage }}</Tag></td>
                <td>{{ a.note }}</td>
                <td class="text-right"><button type="button" class="cursor-pointer text-primary" @click="operate(a.name, a.stage)">处理</button></td>
              </tr>
            </tbody>
          </table>
        </template>
      </div>
    </div>
  </div>
</template>
