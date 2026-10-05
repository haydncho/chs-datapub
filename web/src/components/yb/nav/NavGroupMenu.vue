<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { ChevronDown } from '@lucide/vue'
import { iconOf } from '@/lib/icons'
import { isActiveItem, type NavGroup, type NavItem } from '@/app/nav'

/** 一级菜单:悬停 / 聚焦展开下拉,列出该分组的全部二级页面;支持 Tab、Enter、Esc、方向键。 */
const props = defineProps<{
  group: NavGroup
  active: boolean
  code: string
  query?: Record<string, unknown>
  uid: string
}>()
const emit = defineEmits<{ go: [item: NavItem] }>()

const open = ref(false)
const root = ref<HTMLElement>()
const trigger = ref<HTMLButtonElement>()
const hasMenu = computed(() => props.group.items.length > 1)
const GroupIcon = computed(() => iconOf(props.group.icon))
const menuId = computed(() => `yb-nav-menu-${props.uid}`)
let closeT: ReturnType<typeof setTimeout> | undefined

function show() {
  clearTimeout(closeT)
  if (hasMenu.value) open.value = true
}
function hideSoon() {
  clearTimeout(closeT)
  closeT = setTimeout(() => (open.value = false), 120)
}
function hideNow() {
  clearTimeout(closeT)
  open.value = false
}
function onFocusOut(e: FocusEvent) {
  const next = e.relatedTarget as Node | null
  if (!next || !root.value?.contains(next)) hideNow()
}
function items(): HTMLElement[] {
  return Array.from(root.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [])
}
async function focusItem(i: number) {
  open.value = true
  await nextTick()
  const list = items()
  list[(i + list.length) % list.length]?.focus()
}
function onTriggerKey(e: KeyboardEvent) {
  if (e.key === 'ArrowDown' && hasMenu.value) { e.preventDefault(); focusItem(0) }
  else if (e.key === 'Escape') hideNow()
}
function onItemKey(e: KeyboardEvent) {
  const list = items()
  const at = list.indexOf(e.target as HTMLElement)
  if (e.key === 'ArrowDown') { e.preventDefault(); list[(at + 1) % list.length]?.focus() }
  else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (at <= 0) trigger.value?.focus()
    else list[at - 1]?.focus()
  } else if (e.key === 'Home') { e.preventDefault(); list[0]?.focus() }
  else if (e.key === 'End') { e.preventDefault(); list[list.length - 1]?.focus() }
  else if (e.key === 'Escape') { e.preventDefault(); trigger.value?.focus(); hideNow() }
}
function pick(item: NavItem) {
  hideNow()
  emit('go', item)
}
</script>

<template>
  <div
    ref="root"
    class="yb-l1"
    :data-active="active || undefined"
    @mouseenter="show"
    @mouseleave="hideSoon"
    @focusin="show"
    @focusout="onFocusOut"
  >
    <button
      ref="trigger"
      type="button"
      class="yb-l1-btn"
      :aria-haspopup="hasMenu ? 'menu' : undefined"
      :aria-expanded="hasMenu ? open : undefined"
      :aria-controls="hasMenu ? menuId : undefined"
      :aria-current="active ? 'true' : undefined"
      @click="pick(group.items.find(i => isActiveItem(i, code, query)) ?? group.items[0]!)"
      @keydown="onTriggerKey"
    >
      <span v-if="group.n" class="yb-l1-no yb-num">{{ group.n }}</span>
      <component :is="GroupIcon" class="yb-l1-ico" aria-hidden="true" />
      <span>{{ group.name }}</span>
      <ChevronDown v-if="hasMenu" class="yb-l1-caret" :data-open="open || undefined" aria-hidden="true" />
    </button>
    <div v-if="hasMenu && open" :id="menuId" class="yb-l1-panel" role="menu" :aria-label="`${group.name} 菜单`" @keydown="onItemKey">
      <div class="yb-l1-card">
        <div class="yb-l1-cap">{{ group.n ? `环节 ${group.n} · ` : '' }}{{ group.name }}</div>
        <button
          v-for="it in group.items"
          :key="it.code + (it.query?.who ?? '')"
          type="button"
          role="menuitem"
          class="yb-l1-item"
          :data-current="isActiveItem(it, code, query) || undefined"
          @click="pick(it)"
        >
          <component :is="iconOf(it.icon)" class="yb-l1-item-ico" aria-hidden="true" />
          <span class="flex-1 text-left">{{ it.name }}</span>
          <span v-if="isActiveItem(it, code, query)" class="yb-l1-dot" aria-hidden="true" />
        </button>
      </div>
    </div>
  </div>
</template>
