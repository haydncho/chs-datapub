<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { opinionApi } from '@/api'
import type { Ticket, Tone } from '@/api/types'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import SegTabs from '@/components/shared/SegTabs.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'
import { toneText } from '@/lib/tone'

/** A10 意见与申诉管理：承办人在时限内答复；核对期异议须在发布前答复。答复后显示机构评价，可设为典型问答。 */
const page = pageDef('A10')!
const TABS = ['全部', '核对期异议', '待答复', '已答复'] as const
const tab = ref<string>('全部')
const counts = ref<Record<string, number>>({})
const rows = ref<Ticket[]>([])
const selNo = ref<string | null>(null)
const reply = ref('')
const busy = ref(false)

async function load() {
  const r = await opinionApi.list(tab.value)
  counts.value = r.counts
  rows.value = r.rows
  if (!rows.value.some((x) => x.no === selNo.value)) selNo.value = rows.value[0]?.no ?? null
}
onMounted(() => load().catch(notifyError))
watch(tab, () => load().catch(notifyError))
watch(selNo, () => (reply.value = ''))

const tk = computed(() => rows.value.find((r) => r.no === selNo.value))
const tabItems = computed(() => TABS.map((t) => ({ value: t as string, label: t, count: counts.value[t] ?? 0 })))
const ST: Record<Ticket['status'], [string, Tone]> = { WAIT: ['待答复', 'warning'], DOING: ['处理中', 'primary'], DONE: ['已答复', 'success'] }
const isCheck = (t: Ticket) => t.category === '核对期异议'

function patch(t: Ticket) {
  rows.value = rows.value.map((r) => (r.no === t.no ? t : r))
}

async function run(fn: () => Promise<Ticket>, msg: (t: Ticket) => string) {
  busy.value = true
  try {
    const t = await fn()
    patch(t)
    notify(msg(t))
    const r = await opinionApi.list(tab.value)
    counts.value = r.counts
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

const answer = () => tk.value && run(() => opinionApi.reply(tk.value!.no, reply.value), () => '已答复,机构将收到通知并可评价')
const transfer = () => tk.value && run(() => opinionApi.transfer(tk.value!.no), (t) => `已转办至${t.owner}`)
const typical = () =>
  tk.value && run(() => opinionApi.typical(tk.value!.no, !tk.value!.typical), (t) => (t.typical ? '已设为典型问答,将进入下期发布包“常见问答”' : '已取消典型问答'))
const stars = (n: number) => '★'.repeat(n) + '☆'.repeat(5 - n)
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" />
    <div class="mt-5 grid grid-cols-[minmax(0,1fr)_320px] items-start gap-3.5">
      <div>
        <SegTabs v-model="tab" :items="tabItems" class="mb-3" data-testid="opinion-tabs" />
        <div class="table-scroll">
          <table class="data-table" data-testid="tickets">
            <thead>
              <tr><th>工单号</th><th>机构</th><th>关联指标 / 报告段落</th><th>类别</th><th>承办人</th><th>剩余时限</th><th>状态</th></tr>
            </thead>
            <tbody class="text-ink-sub">
              <tr v-if="!rows.length"><td colspan="7" class="py-6 text-center text-ink-faint">暂无工单</td></tr>
              <tr v-for="r in rows" :key="r.no" class="cursor-pointer" :data-on="r.no === selNo" :data-ticket="r.no" @click="selNo = r.no">
                <td class="font-mono text-[11px]">{{ r.no }}</td>
                <td class="text-ink">{{ r.org }}</td>
                <td>{{ r.ref }}</td>
                <td><Tag :tone="isCheck(r) ? 'ai' : 'muted'">{{ r.category }}</Tag></td>
                <td>{{ r.owner }}</td>
                <td :class="toneText[r.dueTone]">{{ r.dueLabel }}</td>
                <td><Tag :tone="ST[r.status][1]">{{ ST[r.status][0] }}</Tag></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <Panel v-if="tk" data-testid="ticket-detail">
        <div class="font-mono text-[11px] text-ink-muted">{{ tk.no }}</div>
        <div class="text-[14px] font-semibold text-ink">{{ tk.org }}</div>
        <div v-if="isCheck(tk)" class="mt-3 rounded-lg border border-ai-line bg-ai-soft px-2.5 py-1.5 text-[12px] text-ai-ink">核对期异议:须在发布前答复,影响本期发布包定稿</div>
        <div class="mt-3 grid grid-cols-[64px_1fr] gap-1.5 text-[12px]">
          <span class="text-ink-muted">关联</span><span class="text-primary">{{ tk.ref }}</span>
          <span class="text-ink-muted">类别</span><span>{{ tk.category }}</span>
          <span class="text-ink-muted">承办人</span><span>{{ tk.owner }}</span>
        </div>
        <div class="mt-3 rounded-lg bg-subtle px-3 py-2.5 text-[12px] leading-[1.7] text-ink">{{ tk.content }}</div>

        <template v-if="tk.status !== 'DONE'">
          <Textarea v-model="reply" placeholder="填写答复意见" class="mt-3 h-24 resize-none text-[12px]" data-testid="reply-input" />
          <div class="mt-2.5 flex gap-2">
            <Button size="sm" :disabled="busy || !reply.trim()" data-testid="reply-btn" @click="answer">答复</Button>
            <Button size="sm" variant="outline" :disabled="busy" @click="transfer">转办</Button>
          </div>
        </template>
        <template v-else>
          <div class="mt-3 border-l-2 border-success pl-2.5 text-[12px]">
            <div class="text-ink-muted">医保局答复</div>
            <div class="leading-[1.7] text-ink">{{ tk.reply }}</div>
          </div>
          <div v-if="tk.rating" class="mt-2.5 flex justify-between rounded-lg border border-success-line bg-success-soft px-2.5 py-2 text-[12px]">
            <span>机构评价</span><span class="text-success-ink">{{ tk.rating >= 4 ? '满意' : tk.rating >= 3 ? '基本满意' : '不满意' }} {{ stars(tk.rating) }}</span>
          </div>
          <div v-else class="mt-2.5 rounded-lg bg-subtle px-2.5 py-2 text-[12px] text-ink-muted">等待机构评价</div>
        </template>

        <button type="button" class="mt-3 flex w-full cursor-pointer items-center gap-2 border-t border-divider pt-2.5 text-left text-[12px] text-ink" :disabled="busy" @click="typical">
          <span class="flex size-3.5 items-center justify-center rounded-[3px] border-[1.5px] border-primary text-[10px] text-primary">{{ tk.typical ? '✓' : '' }}</span>
          设为典型问答(进入下期“常见问答”)
        </button>
      </Panel>
    </div>
  </div>
</template>
