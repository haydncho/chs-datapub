<script setup lang="ts">
import { ref, watch } from 'vue'
import { ChevronDown } from '@lucide/vue'
import { Sheet, SheetContent, SheetDescription, SheetTitle } from '@/components/ui/sheet'
import { iconOf } from '@/lib/icons'
import { platformName } from '@/app/appearance'
import { isActiveItem, SIDE_NAME, type NavGroup, type NavItem, type Side } from '@/app/nav'

/** 窄屏(< 1024)左侧抽屉:端 → 分组(可折叠)→ 页面;每行 ≥ 44px,点页面后自动关闭。 */
const props = defineProps<{
  open: boolean
  side: Side
  groups: NavGroup[]
  activeId?: string
  code: string
  query?: Record<string, unknown>
}>()
const emit = defineEmits<{ 'update:open': [v: boolean]; go: [item: NavItem] }>()

// 展开状态:默认只展开当前分组;每次打开抽屉重置为当前分组
const expanded = ref<Set<string>>(new Set())
watch(() => props.open, o => { if (o) expanded.value = new Set(props.activeId ? [props.activeId] : []) }, { immediate: true })
function toggle(id: string) {
  const s = new Set(expanded.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  expanded.value = s
}
function pick(it: NavItem) {
  emit('update:open', false)
  emit('go', it)
}
function onGroup(g: NavGroup) {
  if (g.items.length === 1) pick(g.items[0]!)
  else toggle(g.id)
}
</script>

<template>
  <Sheet :open="open" @update:open="v => emit('update:open', v)">
    <SheetContent side="left" class="yb-drawer w-[min(86vw,320px)] gap-0 p-0 sm:max-w-none" data-testid="nav-drawer">
      <div class="flex shrink-0 items-center gap-2.5 border-b border-line-1 px-4 py-3.5 pr-12">
        <div class="flex size-[30px] items-center justify-center rounded-lg bg-brand text-[15px] font-bold text-white">医</div>
        <div class="min-w-0 leading-tight">
          <SheetTitle class="text-[15px]">{{ platformName.main }}</SheetTitle>
          <SheetDescription class="text-[11px] text-ink-4">{{ platformName.sub }}</SheetDescription>
        </div>
      </div>
      <div class="px-4 pt-3 pb-1">
        <span class="yb-side" :data-side="side">{{ SIDE_NAME[side] }}</span>
      </div>
      <nav class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-2 pt-1 pb-6" aria-label="全部菜单">
        <div v-for="g in groups" :key="g.id" class="mb-0.5">
          <button
            type="button"
            class="yb-dr-group"
            :data-active="g.id === activeId || undefined"
            :aria-expanded="g.items.length > 1 ? expanded.has(g.id) : undefined"
            @click="onGroup(g)"
          >
            <span v-if="g.n" class="yb-l1-no yb-num">{{ g.n }}</span>
            <component :is="iconOf(g.icon)" class="yb-dr-ico" aria-hidden="true" />
            <span class="flex-1 text-left">{{ g.name }}</span>
            <ChevronDown v-if="g.items.length > 1" class="yb-dr-caret" :data-open="expanded.has(g.id) || undefined" aria-hidden="true" />
          </button>
          <div v-if="g.items.length > 1 && expanded.has(g.id)" class="mt-0.5 mb-1 ml-5 border-l border-line-1 pl-2">
            <button
              v-for="it in g.items"
              :key="it.code + (it.query?.who ?? '')"
              type="button"
              class="yb-dr-item"
              :data-current="isActiveItem(it, code, query) || undefined"
              :data-testid="`drawer-${it.code}`"
              @click="pick(it)"
            >
              <component :is="iconOf(it.icon)" class="yb-dr-ico" aria-hidden="true" />
              <span class="flex-1 text-left">{{ it.name }}</span>
            </button>
          </div>
        </div>
      </nav>
    </SheetContent>
  </Sheet>
</template>
