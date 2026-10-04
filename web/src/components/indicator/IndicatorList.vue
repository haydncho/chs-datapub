<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { indicatorApi, type IndPage, type IndQuery, type IndRow } from '@/api/indicator'
import Chip from '@/components/shared/Chip.vue'
import Tag from '@/components/shared/Tag.vue'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { notify, notifyError } from '@/lib/notify'
import { grpClass, statusTone, tagTone } from './tones'

/**
 * A4 指标列表：分组芯片 / 主题域 / 标签 / 搜索 + 分页；「仅内部」整行置灰、发布包操作禁用；
 * 「本期暂缓」悬停说明依赖数据源未到达。点击行打开指标卡抽屉。
 */
const emit = defineEmits<{ open: [id: number]; create: [] }>()

const TAG_LABEL: Record<string, string> = { 必选: '国家底稿必选', 增选: '地方增选', 仅内部: '仅内部' }
const q = ref<IndQuery>({ grp: '全部', domain: '', tag: '', q: '', page: 1, size: 13 })
const kw = ref('')
const data = ref<IndPage | null>(null)
const error = ref('')

async function load() {
  try {
    data.value = await indicatorApi.list(q.value)
    error.value = ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}
onMounted(load)
defineExpose({ reload: load })

watch(() => [q.value.grp, q.value.domain, q.value.tag, q.value.q, q.value.sort, q.value.dir], () => {
  q.value.page = 1
  void load()
})
let timer: ReturnType<typeof setTimeout> | undefined
watch(kw, (v) => {
  clearTimeout(timer)
  timer = setTimeout(() => (q.value.q = v.trim()), 300)
})
onBeforeUnmount(() => clearTimeout(timer))

function go(p: number) {
  if (!data.value || p < 1 || p > data.value.pages || p === data.value.page) return
  q.value.page = p
  void load()
}

/** 表头排序：升序 → 降序 → 取消。 */
function sortBy(f: 'tier' | 'status') {
  if (q.value.sort !== f) Object.assign(q.value, { sort: f, dir: 'asc' })
  else if (q.value.dir === 'asc') q.value.dir = 'desc'
  else Object.assign(q.value, { sort: undefined, dir: undefined })
}
const arrow = (f: string) => (q.value.sort !== f ? '↕' : q.value.dir === 'asc' ? '↑' : '↓')

async function togglePkg(r: IndRow) {
  if (r.internal) return notify('仅内部指标不可加入发布包')
  try {
    const res = r.inPackage ? await indicatorApi.removeFromPackage(r.id) : await indicatorApi.addToPackage(r.id)
    notify(res.message)
    if (data.value) data.value.rows = data.value.rows.map((x) => (x.id === r.id ? res.row : x))
  } catch (e) {
    notifyError(e)
  }
}
</script>

<template>
  <section class="rounded-2xl border border-line-soft bg-surface shadow-card">
    <div class="flex flex-wrap items-center gap-2.5 border-b border-divider px-[18px] py-3">
      <span class="text-[12px] text-ink-muted">分组</span>
      <div class="flex gap-1.5" data-testid="grp-chips">
        <Chip v-for="g in ['全部', '钱', '效', '错']" :key="g" :on="q.grp === g" @click="q.grp = g">{{ g }}</Chip>
      </div>
      <span class="h-[18px] w-px bg-line" />
      <span class="text-[12px] text-ink-muted">主题域</span>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <button type="button" class="cursor-pointer rounded-lg border border-line px-2.5 py-[3px] text-[12px] text-ink-sub hover:bg-hover" :class="q.domain && 'border-primary-line-strong bg-primary-tint text-primary'" data-testid="domain-filter">
            {{ q.domain || `全部 ${data?.domains.length ?? ''} 个` }} ▾
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" class="min-w-[160px]">
          <DropdownMenuRadioGroup v-model="q.domain">
            <DropdownMenuRadioItem value="" class="text-[12px]">全部主题域</DropdownMenuRadioItem>
            <DropdownMenuRadioItem v-for="d in data?.domains ?? []" :key="d" :value="d" class="text-[12px]">{{ d }}</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      <DropdownMenu>
        <DropdownMenuTrigger as-child>
          <button type="button" class="cursor-pointer rounded-lg border border-line px-2.5 py-[3px] text-[12px] text-ink-sub hover:bg-hover" :class="q.tag && 'border-primary-line-strong bg-primary-tint text-primary'" data-testid="tag-filter">
            {{ q.tag ? TAG_LABEL[q.tag] : '标签' }} ▾
          </button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" class="min-w-[140px]">
          <DropdownMenuRadioGroup v-model="q.tag">
            <DropdownMenuRadioItem value="" class="text-[12px]">全部标签</DropdownMenuRadioItem>
            <DropdownMenuRadioItem v-for="t in data?.tags ?? []" :key="t" :value="t" class="text-[12px]">{{ TAG_LABEL[t] }}</DropdownMenuRadioItem>
          </DropdownMenuRadioGroup>
        </DropdownMenuContent>
      </DropdownMenu>
      <div class="flex-1" />
      <input
        v-model="kw"
        type="search"
        placeholder="搜索指标名称 / 编码"
        class="h-[30px] w-[200px] rounded-lg border border-line bg-surface px-2.5 text-[12px] text-ink outline-none placeholder:text-ink-faint focus:border-ring"
        data-testid="ind-search"
      >
      <Button size="sm" data-testid="new-ind" @click="emit('create')">+ 新建指标</Button>
    </div>

    <div v-if="error" class="px-[18px] py-10 text-center text-[12px] text-danger">{{ error }}</div>
    <table v-else class="ind-table w-full text-[12px]" data-testid="ind-table">
      <colgroup>
        <col class="w-[24%]"><col class="w-[48px]"><col><col><col class="w-[44px]"><col class="w-[112px]"><col><col class="w-[52px]"><col class="w-[84px]"><col class="w-[120px]">
      </colgroup>
      <thead>
        <tr>
          <th>指标名称</th><th>分组</th><th>主题域</th><th>来源</th><th>频次</th>
          <th><button type="button" class="cursor-pointer hover:text-primary" :class="q.sort === 'tier' && 'text-primary'" data-testid="sort-tier" @click="sortBy('tier')">对标档位 {{ arrow('tier') }}</button></th>
          <th>可见范围</th><th>版本</th>
          <th><button type="button" class="cursor-pointer hover:text-primary" :class="q.sort === 'status' && 'text-primary'" data-testid="sort-status" @click="sortBy('status')">状态 {{ arrow('status') }}</button></th>
          <th>发布包</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="r in data?.rows ?? []"
          :key="r.id"
          class="cursor-pointer"
          :class="r.internal && 'is-internal'"
          :data-ind="r.name"
          @click="emit('open', r.id)"
        >
          <td>
            <div class="flex min-w-0 items-center gap-2 dim">
              <span class="truncate font-medium text-ink">{{ r.name }}</span>
              <Tag :tone="tagTone(r.tag)" class="!px-1.5 !text-[10px]">{{ r.tagLabel }}</Tag>
            </div>
          </td>
          <td><span class="dim font-semibold" :class="grpClass(r.grp)">{{ r.grp }}</span></td>
          <td class="dim">{{ r.domain }}</td>
          <td class="dim text-ink-muted">{{ r.source }}</td>
          <td class="dim">{{ r.freq }}</td>
          <td class="dim">
            {{ r.tierLabel }}
            <div v-if="r.tierPending" class="text-[10px] leading-tight text-warning">审批中 → {{ r.tierPending }}</div>
          </td>
          <td class="dim text-ink-muted">{{ r.scope }}</td>
          <td class="dim font-mono">{{ r.version }}</td>
          <td>
            <Tooltip v-if="r.statusTip">
              <TooltipTrigger as-child>
                <span class="inline-flex" :data-status-tip="r.statusTip" @click.stop><Tag :tone="statusTone(r.status)" class="cursor-help">{{ r.status }}</Tag></span>
              </TooltipTrigger>
              <TooltipContent side="top" data-testid="status-tip">{{ r.statusTip }}</TooltipContent>
            </Tooltip>
            <Tag v-else :tone="statusTone(r.status)">{{ r.status }}</Tag>
          </td>
          <td>
            <button
              v-if="r.internal"
              type="button"
              class="cursor-not-allowed text-ink-ghost"
              aria-disabled="true"
              data-pkg="disabled"
              @click.stop="togglePkg(r)"
            >不可加入</button>
            <span v-else-if="r.inPackage" class="whitespace-nowrap">
              <span class="text-success">✓ 已加入</span>
              <button type="button" class="ml-1.5 cursor-pointer text-ink-faint hover:text-danger" data-pkg="remove" @click.stop="togglePkg(r)">移出</button>
            </span>
            <button v-else type="button" class="cursor-pointer text-primary hover:underline" data-pkg="add" @click.stop="togglePkg(r)">加入发布包</button>
          </td>
        </tr>
        <tr v-if="data && !data.rows.length"><td colspan="10" class="!py-10 text-center text-ink-faint">没有符合条件的指标</td></tr>
      </tbody>
    </table>

    <div v-if="data" class="flex items-center justify-between px-[18px] py-2.5 text-[12px] text-ink-muted">
      <span data-testid="ind-count">本页 {{ data.rows.length }} 条 · 共 {{ data.total }} 条 · 「本期暂缓」= 依赖数据源本期未按时到达</span>
      <div class="flex items-center gap-1" data-testid="pager">
        <button type="button" class="pg" :disabled="data.page <= 1" aria-label="上一页" @click="go(data.page - 1)">‹</button>
        <button v-for="p in data.pages" :key="p" type="button" class="pg" :class="p === data.page && 'on'" :aria-current="p === data.page" @click="go(p)">{{ p }}</button>
        <button type="button" class="pg" :disabled="data.page >= data.pages" aria-label="下一页" @click="go(data.page + 1)">›</button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.ind-table {
  table-layout: fixed;
  border-collapse: collapse;
}
.ind-table thead th {
  background: var(--c-th-bg);
  color: var(--c-text-sub);
  font-weight: 600;
  text-align: left;
  padding: 9px 8px;
  border-bottom: 1px solid var(--c-border);
  white-space: nowrap;
}
.ind-table td {
  padding: 9px 8px;
  border-top: 1px solid var(--c-divider);
  vertical-align: middle;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ind-table th:first-child,
.ind-table td:first-child { padding-left: 18px; }
.ind-table th:last-child,
.ind-table td:last-child { padding-right: 18px; }
.ind-table tbody tr:hover td { background: var(--c-row-hover); }
/* 仅内部：整行置灰（0.55），发布包禁用 */
.ind-table tr.is-internal td { background: var(--c-subtle); }
.ind-table tr.is-internal .dim { opacity: 0.55; }
.pg {
  min-width: 26px;
  padding: 1px 8px;
  border: 1px solid var(--c-border);
  border-radius: 6px;
  color: var(--c-text-sub);
  cursor: pointer;
}
.pg:hover:not(:disabled) { background: var(--c-hover); }
.pg:disabled { opacity: 0.4; cursor: default; }
.pg.on {
  background: var(--c-primary-solid);
  border-color: var(--c-primary-solid);
  color: white;
}
</style>
