<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { auditApi } from '@/api'
import { HttpError } from '@/api/http'
import type { AuditLog, AuditTrace, Tone } from '@/api/types'
import Chip from '@/components/shared/Chip.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { pageDef } from '@/lib/nav'
import { notifyError } from '@/lib/notify'

/** A14 审计日志：只读；按操作类型筛选（越权尝试红色标注）；输入水印编号溯源到人、时间、文件。 */
const page = pageDef('A14')!
const types = ref<string[]>(['全部'])
const type = ref('全部')
const pageNo = ref(1)
const rows = ref<AuditLog[]>([])
const total = ref(0)
const size = ref(10)
const today = ref({ count: 0, overreach: 0 })
const wm = ref('')
const traced = ref<AuditTrace | null>(null)
const miss = ref(false)

async function load() {
  try {
    const r = await auditApi.logs(type.value, pageNo.value)
    types.value = r.types
    rows.value = r.rows
    total.value = r.total
    size.value = r.size
    today.value = { count: r.todayCount, overreach: r.todayOverreach }
  } catch (e) {
    notifyError(e)
  }
}
onMounted(load)
watch(type, () => {
  pageNo.value = 1
  void load()
})
watch(pageNo, load)

const pages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

async function trace() {
  traced.value = null
  miss.value = false
  if (!wm.value.trim()) return
  try {
    traced.value = await auditApi.trace(wm.value.trim())
  } catch (e) {
    if (e instanceof HttpError && e.status === 404) miss.value = true
    else notifyError(e)
  }
}

const typeTone = (t: string): Tone => (t === '越权尝试' ? 'danger' : t === '导出' || t === '打印' ? 'warning' : t === '授权' ? 'ai' : t === '审批' ? 'primary' : 'muted')
const blocked = (r: string) => r === '已拦截' || r === '已锁定'
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />

    <div class="mt-5 flex flex-wrap items-center gap-3 rounded-[10px] border border-line bg-surface px-[18px] py-3.5">
      <span class="sect-title">水印溯源</span>
      <Input v-model="wm" class="w-[240px] py-1.5 font-mono text-[12px]" placeholder="输入水印编号,如 WM-20261003-5208" aria-label="水印编号" data-testid="wm-input" @keydown.enter="trace" />
      <Button size="sm" data-testid="trace-btn" @click="trace">溯源</Button>
      <span class="text-[11px] text-ink-muted">输入截图或文件上的水印编号</span>
      <div v-if="traced" class="grid basis-full grid-cols-6 gap-2.5 rounded-lg border border-warning-line bg-warning-soft px-3 py-2.5 text-[12px]" data-testid="trace-result">
        <div><div class="text-[11px] text-ink-muted">操作人</div><b>{{ traced.user }}</b></div>
        <div><div class="text-[11px] text-ink-muted">机构</div>{{ traced.org }}</div>
        <div><div class="text-[11px] text-ink-muted">时间</div>{{ traced.at }}</div>
        <div><div class="text-[11px] text-ink-muted">操作</div>{{ traced.type }}</div>
        <div><div class="text-[11px] text-ink-muted">对象</div>{{ traced.object }}</div>
        <div><div class="text-[11px] text-ink-muted">终端</div><span class="font-mono">{{ traced.ip }}</span></div>
      </div>
      <div v-if="miss" class="basis-full text-[12px] text-danger">未找到该水印编号</div>
    </div>

    <div class="mt-3.5 mb-3 flex flex-wrap items-center gap-1.5">
      <span class="mr-1 text-[12px] text-ink-muted">操作类型</span>
      <Chip v-for="t in types" :key="t" :on="t === type" @click="type = t">{{ t }}</Chip>
      <span class="ml-auto text-[11px] text-ink-muted">日志只读 · 保存 3 年</span>
    </div>
    <div class="table-scroll">
      <table class="data-table" data-testid="audit-logs">
        <thead><tr><th>时间 ↓</th><th>用户</th><th>机构</th><th>类型</th><th>对象</th><th>终端 IP</th><th>水印编号</th><th>结果</th></tr></thead>
        <tbody class="text-ink-sub">
          <tr v-if="!rows.length"><td colspan="8" class="py-6 text-center text-ink-faint">暂无记录</td></tr>
          <tr v-for="l in rows" :key="l.id" :class="l.type === '越权尝试' ? '[&>td]:!bg-danger-soft' : ''">
            <td>{{ l.at }}</td>
            <td class="text-ink">{{ l.user }}</td>
            <td>{{ l.org }}</td>
            <td><Tag :tone="typeTone(l.type)">{{ l.type }}</Tag></td>
            <td class="max-w-[360px] truncate" :title="l.object">{{ l.object }}</td>
            <td class="font-mono text-[11px]">{{ l.ip ?? '—' }}</td>
            <td class="font-mono text-[11px]">{{ l.watermarkNo ?? '—' }}</td>
            <td class="font-medium" :class="blocked(l.result) ? 'text-danger' : 'text-ink'">{{ l.result }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="mt-3 flex items-center justify-between text-[12px] text-ink-muted">
      <span>今日 {{ today.count.toLocaleString('zh-CN') }} 条 · 越权尝试 {{ today.overreach }} 条 · 共 {{ total }} 条</span>
      <div class="flex gap-1">
        <button type="button" class="cursor-pointer rounded-md border border-line px-2 py-0.5 disabled:opacity-40" :disabled="pageNo <= 1" @click="pageNo--">‹</button>
        <button
          v-for="p in pages"
          :key="p"
          type="button"
          class="cursor-pointer rounded-md px-2 py-0.5"
          :class="p === pageNo ? 'bg-primary-solid text-white' : 'border border-line'"
          @click="pageNo = p"
        >{{ p }}</button>
        <button type="button" class="cursor-pointer rounded-md border border-line px-2 py-0.5 disabled:opacity-40" :disabled="pageNo >= pages" @click="pageNo++">›</button>
      </div>
    </div>
  </div>
</template>
