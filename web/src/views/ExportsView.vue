<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { exportApi } from '@/api'
import { getToken } from '@/api/http'
import type { ExportItem, Tone } from '@/api/types'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import SegTabs from '@/components/shared/SegTabs.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { confirm } from '@/lib/confirm'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

/** 我的导出:本人的导出申请(状态、有效期、剩余次数、带水印下载);召集人 / 行政管理组另有「待我审批」。 */
const page = pageDef('E1')!
const auth = useAuthStore()
const canDecide = computed(() => ['CONVENER', 'ADMIN_GROUP'].includes(auth.user?.role ?? ''))
const tab = ref<'mine' | 'pending'>('mine')
const mine = ref<ExportItem[]>([])
const pending = ref<ExportItem[]>([])
const opinion = ref('')
const busy = ref(false)

const ST: Record<string, Tone> = { 待审批: 'warning', 已批准: 'success', 已驳回: 'danger', 已失效: 'muted', 已用尽: 'muted' }

async function load() {
  try {
    mine.value = await exportApi.mine()
    pending.value = canDecide.value ? await exportApi.pending() : []
  } catch (e) {
    notifyError(e)
  }
}
onMounted(load)

async function download(it: ExportItem) {
  busy.value = true
  try {
    const r = await fetch(`/api/v1/exports/${it.id}/download`, { headers: { Authorization: `Bearer ${getToken() ?? ''}` } })
    if (!r.ok) throw new Error((await r.json().catch(() => null))?.message ?? '下载失败')
    const url = URL.createObjectURL(await r.blob())
    const a = document.createElement('a')
    a.href = url
    a.download = `${it.watermarkNo}.csv`
    a.click()
    URL.revokeObjectURL(url)
    notify(`已下载 ${it.watermarkNo}(已带实名水印并留痕)`)
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

async function decide(it: ExportItem, ok: boolean) {
  if (!ok && !opinion.value.trim()) return notify('驳回须填写意见')
  if (ok && !(await confirm({ title: '批准导出', body: `批准 ${it.applicant} 导出「${it.content}」,有效期 ${it.validity}、可下载 ${it.times}。`, okText: '批准' }))) return
  busy.value = true
  try {
    await (ok ? exportApi.approve(it.id, opinion.value) : exportApi.reject(it.id, opinion.value))
    notify(ok ? '已批准导出申请' : '已驳回导出申请')
    opinion.value = ''
    await load()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div v-if="canDecide" class="mt-5"><SegTabs v-model="tab" :items="[{ value: 'mine', label: '我的申请', count: mine.length }, { value: 'pending', label: '待我审批', count: pending.length }]" /></div>

    <Panel v-if="tab === 'mine'" class="mt-4" flush>
      <table class="data-table !rounded-none !border-0" data-testid="my-exports">
        <thead><tr><th>水印编号</th><th>导出内容</th><th>用途</th><th>申请时间</th><th>状态</th><th>有效期至</th><th>剩余次数</th><th /></tr></thead>
        <tbody class="text-ink-sub">
          <tr v-if="!mine.length"><td colspan="8" class="py-6 text-center text-ink-faint">暂无导出申请。在可导出的页面点击右上角「导出」发起。</td></tr>
          <tr v-for="r in mine" :key="r.id" :data-export="r.watermarkNo">
            <td class="font-mono text-[11px]">{{ r.watermarkNo }}</td>
            <td class="text-ink">{{ r.content }}</td>
            <td>{{ r.purpose }}</td>
            <td>{{ r.createdAt }}</td>
            <td><Tag :tone="ST[r.status] ?? 'muted'">{{ r.status }}</Tag><div v-if="r.opinion && r.status === '已驳回'" class="mt-1 text-[11px] text-ink-muted">{{ r.decidedBy }}:{{ r.opinion }}</div></td>
            <td>{{ r.expiresAt ?? '—' }}</td>
            <td class="tabular-nums">{{ r.status === '已批准' ? r.remaining : '—' }}</td>
            <td class="text-right"><Button v-if="r.downloadable" size="sm" :disabled="busy" data-testid="download-btn" @click="download(r)">下载</Button></td>
          </tr>
        </tbody>
      </table>
    </Panel>

    <Panel v-else class="mt-4" flush>
      <table class="data-table !rounded-none !border-0" data-testid="pending-exports">
        <thead><tr><th>水印编号</th><th>申请人</th><th>导出内容</th><th>用途</th><th>有效期 / 次数</th><th>申请时间</th><th /></tr></thead>
        <tbody class="text-ink-sub">
          <tr v-if="!pending.length"><td colspan="7" class="py-6 text-center text-ink-faint">没有待审批的导出申请</td></tr>
          <tr v-for="r in pending" :key="r.id" :data-export="r.watermarkNo">
            <td class="font-mono text-[11px]">{{ r.watermarkNo }}</td>
            <td class="text-ink">{{ r.applicant }}<span class="text-ink-muted"> · {{ r.org }}</span></td>
            <td>{{ r.content }}</td>
            <td>{{ r.purpose }}</td>
            <td>{{ r.validity }} · {{ r.times }}</td>
            <td>{{ r.createdAt }}</td>
            <td class="text-right whitespace-nowrap">
              <Button size="sm" :disabled="busy" data-testid="export-approve" @click="decide(r, true)">批准</Button>
              <Button size="sm" variant="outline" class="ml-1.5 border-danger text-danger" :disabled="busy" data-testid="export-reject" @click="decide(r, false)">驳回</Button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="pending.length" class="border-t border-divider px-5 py-3">
        <Textarea v-model="opinion" placeholder="审批意见(驳回必填)" class="h-14 resize-none text-[12px]" data-testid="export-opinion" />
      </div>
    </Panel>
  </div>
</template>
