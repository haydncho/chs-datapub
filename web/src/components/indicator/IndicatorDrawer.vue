<script setup lang="ts">
import { ref, watch } from 'vue'
import { indicatorApi, type IndCard } from '@/api/indicator'
import Tag from '@/components/shared/Tag.vue'
import { notifyError } from '@/lib/notify'
import { statusTone, tagTone } from './tones'

/** 指标卡抽屉（右侧 440px）：主题域、来源、档位、可见范围、责任人、公式、取数规则版本、血缘、变更记录。遮罩点击关闭。 */
const props = defineProps<{ id: number | null }>()
const emit = defineEmits<{ close: [] }>()
const card = ref<IndCard | null>(null)

watch(
  () => props.id,
  async (id) => {
    card.value = null
    if (id == null) return
    try {
      card.value = await indicatorApi.card(id)
    } catch (e) {
      notifyError(e)
      emit('close')
    }
  },
  { immediate: true },
)

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') emit('close')
}
</script>

<template>
  <Teleport to="body">
    <div v-if="id != null" class="fixed inset-0 z-[70]" @keydown="onKey">
      <div class="absolute inset-0 bg-[var(--c-overlay-light)]" data-testid="drawer-mask" @click="emit('close')" />
      <aside
        class="absolute top-0 right-0 bottom-0 flex w-[440px] flex-col border-l border-line bg-surface shadow-[-8px_0_30px_rgba(0,0,0,.12)]"
        role="dialog"
        aria-label="指标卡"
        data-testid="ind-drawer"
      >
        <div class="flex items-center justify-between border-b border-divider px-[18px] py-3.5">
          <div>
            <div class="text-[11px] text-ink-faint">指标卡</div>
            <div class="text-[16px] font-semibold text-ink">
              {{ card?.row.name ?? '…' }}
              <span v-if="card" class="ml-1 font-mono text-[12px] font-normal text-primary">{{ card.row.version }}</span>
            </div>
          </div>
          <button type="button" class="cursor-pointer rounded-md px-1.5 text-[18px] leading-none text-ink-muted hover:bg-hover" aria-label="关闭" data-testid="drawer-close" @click="emit('close')">×</button>
        </div>
        <div v-if="card" class="flex flex-1 flex-col gap-4 overflow-y-auto px-[18px] py-4 text-[12px]">
          <div class="flex flex-wrap items-center gap-1.5">
            <Tag :tone="tagTone(card.row.tag)">{{ card.row.tagLabel }}</Tag>
            <Tag :tone="statusTone(card.row.status)" :title="card.row.statusTip">{{ card.row.status }}</Tag>
            <span class="font-mono text-[11px] text-ink-faint">{{ card.row.code }}</span>
          </div>
          <div class="grid grid-cols-[84px_1fr] gap-y-2">
            <span class="text-ink-faint">主题域</span><span>{{ card.row.domain }}</span>
            <span class="text-ink-faint">数据来源</span><span>{{ card.row.source }} · {{ card.row.freq }}更新</span>
            <span class="text-ink-faint">对标档位</span>
            <span>{{ card.row.tierLabel }}<span v-if="card.row.tierPending" class="ml-1.5 text-warning">审批中:→ {{ card.row.tierPending }}</span></span>
            <span class="text-ink-faint">可见范围</span><span>{{ card.row.scope }}</span>
            <span class="text-ink-faint">责任人</span><span>{{ card.owner }}</span>
          </div>
          <div v-if="card.row.statusTip" class="rounded-lg border px-2.5 py-2" :class="card.row.status === '本期暂缓' ? 'border-warning-line bg-warning-soft text-warning-ink' : 'border-primary-line bg-primary-tint text-primary-ink'">
            {{ card.row.status }}:{{ card.row.statusTip }}
          </div>
          <div>
            <div class="mb-1 font-semibold text-ink">公式</div>
            <div class="rounded-lg bg-th px-2.5 py-2.5 font-mono leading-[1.7] break-all text-ink-body" data-testid="drawer-formula">{{ card.formula }}</div>
          </div>
          <div>
            <div class="mb-1 font-semibold text-ink">取数规则版本</div>
            <div class="text-ink-body">{{ card.ruleVersion }}</div>
          </div>
          <div>
            <div class="mb-1.5 font-semibold text-ink">血缘</div>
            <div class="flex flex-wrap items-center gap-1.5 text-[11px]" data-testid="drawer-lineage">
              <template v-for="(l, i) in card.lineage" :key="l">
                <span v-if="i === 2" class="text-ink-ghost">→</span>
                <span class="rounded px-2 py-[3px]" :class="i === 0 ? 'bg-primary-tint text-primary' : 'bg-chip text-ink-sub'" :style="i >= 2 ? 'font-family: ui-monospace, Menlo, monospace' : ''">{{ l }}</span>
                <span v-if="i === 0" class="text-ink-ghost">→</span>
              </template>
            </div>
          </div>
          <div>
            <div class="mb-1.5 font-semibold text-ink">变更记录</div>
            <div class="flex flex-col gap-2.5 border-l-2 border-line pl-3" data-testid="drawer-changes">
              <div v-for="c in card.changes" :key="c.label + c.date">
                <div class="text-ink">{{ c.label }} · {{ c.date }} · {{ c.author }}</div>
                <div class="text-ink-faint">{{ c.note }}</div>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="flex-1 px-[18px] py-10 text-center text-[12px] text-ink-faint">加载中…</div>
      </aside>
    </div>
  </Teleport>
</template>
