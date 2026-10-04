<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { alertApi } from '@/api'
import type { AlertRule, AlertTrigger, Tone } from '@/api/types'
import PageHeader from '@/components/shared/PageHeader.vue'
import Panel from '@/components/shared/Panel.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'

/** A11 预警提醒（流程 5）：规则可启停；点击触发记录预览提醒函并发出；登记机构回执，跟踪整改至销号。 */
const page = pageDef('A11')!
const rules = ref<AlertRule[]>([])
const triggers = ref<AlertTrigger[]>([])
const selId = ref<number | null>(null)
const busy = ref(false)

onMounted(async () => {
  try {
    ;[rules.value, triggers.value] = await Promise.all([alertApi.rules(), alertApi.triggers()])
    selId.value = triggers.value[0]?.id ?? null
  } catch (e) {
    notifyError(e)
  }
})

const tr = computed(() => triggers.value.find((t) => t.id === selId.value))
const ST: Record<AlertTrigger['status'], [string, Tone]> = {
  GEN: ['待生成提醒函', 'muted'], SENT: ['已发函 · 待回执', 'warning'], RCPT: ['已回执', 'primary'], FIX: ['整改中', 'primary'], CLOSED: ['已销号', 'success'],
}

async function toggle(r: AlertRule, v: boolean) {
  try {
    const n = await alertApi.toggle(r.id, v)
    rules.value = rules.value.map((x) => (x.id === n.id ? n : x))
    notify(`规则「${n.name}」已${n.enabled ? '启用' : '停用'}`)
  } catch (e) {
    notifyError(e)
  }
}

async function step(kind: 'send' | 'receipt') {
  if (!tr.value) return
  busy.value = true
  try {
    const t = kind === 'send' ? await alertApi.send(tr.value.id) : await alertApi.receipt(tr.value.id)
    triggers.value = triggers.value.map((x) => (x.id === t.id ? t : x))
    notify(kind === 'send' ? '提醒函已发出,机构门户与移动端同步提醒' : `已收到 ${t.org} 的回执`)
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

    <div class="mt-5 mb-3 border-l-[3px] border-primary pl-[11px] text-[13px] font-semibold text-ink">预警规则</div>
    <div class="table-scroll">
      <table class="data-table" data-testid="rules">
        <thead><tr><th>规则</th><th>适用范围</th><th>触发条件</th><th>频次</th><th>本期命中</th><th class="text-right">启用</th></tr></thead>
        <tbody class="text-ink-sub">
          <tr v-for="r in rules" :key="r.id" :class="r.enabled ? '' : 'opacity-55'">
            <td class="font-medium text-ink">{{ r.name }}</td>
            <td>{{ r.scope }}</td>
            <td>{{ r.condition }}</td>
            <td>{{ r.frequency }}</td>
            <td>{{ r.hits }} 条</td>
            <td class="text-right"><Switch class="data-[state=unchecked]:bg-neutral-2" :model-value="r.enabled" :aria-label="`启用 ${r.name}`" @update:model-value="(v: boolean) => toggle(r, v)" /></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="mt-[26px] grid grid-cols-[minmax(0,1fr)_440px] items-start gap-3.5">
      <div>
        <div class="mb-3 border-l-[3px] border-primary pl-[11px] text-[13px] font-semibold text-ink">触发记录</div>
        <table class="data-table" data-testid="triggers">
          <thead><tr><th>日期</th><th>机构 / 病组</th><th>规则 / 监测值</th><th>状态</th></tr></thead>
          <tbody class="text-ink-sub">
            <tr v-for="t in triggers" :key="t.id" class="cursor-pointer" :data-on="t.id === selId" @click="selId = t.id">
              <td class="text-ink-muted">{{ t.date }}</td>
              <td><div class="font-medium text-ink">{{ t.org }}</div><div class="text-ink-muted">{{ t.group }}</div></td>
              <td><div>{{ t.rule }}</div><div class="text-warning">{{ t.value }}</div></td>
              <td><Tag :tone="ST[t.status][1]">{{ ST[t.status][0] }}</Tag></td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="tr" class="flex flex-col gap-3.5">
        <!-- 公文样式提醒函（公文纸：深色主题下仍白底） -->
        <div data-paper class="rounded-[10px] border border-line bg-white px-[22px] py-5 text-[12px] leading-[1.8] text-ink" data-testid="letter">
          <div class="text-center text-[15px] font-semibold">关于 {{ tr.letter.group }} 相关指标的提醒函</div>
          <div class="mb-2.5 text-center text-ink-muted">{{ tr.letter.no }}</div>
          <div>{{ tr.letter.org }}:</div>
          <div class="indent-[2em]">
            经医保数据工作组监测,贵院 {{ tr.letter.period }} {{ tr.letter.group }} 触发“{{ tr.letter.rule }}”规则,监测值为 {{ tr.letter.value }}。现予提醒,请贵院对照同级水平分析原因,并于 10 个工作日内通过机构门户提交回执。
          </div>
          <div class="indent-[2em]">本提醒函仅用于内部工作沟通,不作为处罚依据。</div>
          <div class="mt-2 text-right">示例市医疗保障局 医保数据工作组</div>
        </div>

        <Panel title="回执与整改跟踪">
          <template #head>
            <Button v-if="tr.status === 'GEN'" size="sm" :disabled="busy" data-testid="send-letter" @click="step('send')">发出提醒函</Button>
            <Button v-else-if="tr.status === 'SENT'" size="sm" variant="outline" :disabled="busy" data-testid="receipt-btn" @click="step('receipt')">登记机构回执</Button>
          </template>
          <div v-for="k in tr.track" :key="k.name" class="flex gap-2.5 py-1 text-[12px]">
            <span class="mt-1 size-2.5 flex-none rounded-full" :class="k.done ? 'bg-success' : 'bg-neutral'" />
            <div><span class="font-medium" :class="k.done ? 'text-ink' : 'text-ink-faint'">{{ k.name }}</span><span class="text-ink-faint"> · {{ k.desc }}</span></div>
          </div>
          <div v-if="tr.receipt" class="mt-2 rounded-lg bg-subtle px-2.5 py-2 text-[12px] text-ink">回执摘要:{{ tr.receipt }}</div>
        </Panel>
      </div>
    </div>
  </div>
</template>
