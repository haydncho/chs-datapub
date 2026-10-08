<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Menu } from '@lucide/vue'
import { useRoute } from 'vue-router'
import { Toaster } from 'vue-sonner'
import 'vue-sonner/style.css'
import '@/styles/nav.css'
import { cn } from '@/lib/utils'
import { stamp } from '@/lib/format'
import { iconOf } from '@/lib/icons'
import {
  SIDE_ICON, SIDE_NAME, VIEWERS, ZONE_STYLE, breadcrumbOf, isActiveItem, locate, navFor,
  type NavItem, type PageCode, type Side,
} from '@/app/nav'
import { goPage } from '@/app/router'
import { shell } from '@/app/shell'
import { platformName } from '@/app/appearance'
import { session, sessionViewer } from '@/app/session'
import { logout } from '@/api/auth'
import type { Layout } from '@/app/pages'
import NavGroupMenu from './nav/NavGroupMenu.vue'
import NavBreadcrumb from './nav/NavBreadcrumb.vue'
import UserMenu from './nav/UserMenu.vue'
import NavDrawer from './nav/NavDrawer.vue'

const route = useRoute()
const code = computed(() => (route.meta.code as PageCode | undefined) ?? 'cockpit')
const layout = computed(() => (route.meta.layout as Layout | undefined) ?? 'workbench')
const query = computed(() => route.query as Record<string, unknown>)
// 当前端:会话身份的 side;无会话回退到医保局端。无会话的演示模式下,页面属于哪一端就显示哪一端的菜单。
const sessionSide = computed<Side>(() => {
  const s = (session.current?.identity as { side?: string } | undefined)?.side
  return s === 'org' ? 'org' : 'bureau'
})
const side = computed<Side>(() => (session.current ? sessionSide.value : (locate(code.value, query.value, sessionSide.value)?.side ?? 'bureau')))
// 菜单只显示当前身份可访问的页面(演示模式下为全部)
const groups = computed(() => navFor(side.value, session.current?.pages ?? null))
const activeGroup = computed(() => groups.value.find(g => g.items.some(i => isActiveItem(i, code.value, query.value))))
const crumb = computed(() => breadcrumbOf(side.value, code.value, query.value))
const GroupIcon = computed(() => iconOf(activeGroup.value?.icon ?? 'layout-dashboard'))
const SideIcon = computed(() => iconOf(SIDE_ICON[side.value]))
// identity: page override (全景图 身份切换) › logged-in session › per-page demo identity
const viewer = computed(() => shell.viewerOverride ?? sessionViewer.value ?? VIEWERS[code.value])
const canSwitch = computed(() => (session.current?.identities.length ?? 0) > 1)
function go(item: NavItem) {
  goPage(item.code, item.query)
}
async function signOut() {
  await logout()
  goPage('A1')
}

// 窄屏(< 1024)抽屉菜单;换页时自动关闭
const drawer = ref(false)
watch(code, () => (drawer.value = false))

// 二级标签行:窄屏可横向滚动,选中项自动滚入视野
const l2Scroll = ref<HTMLElement>()
async function revealActiveTab() {
  await nextTick()
  const box = l2Scroll.value
  const cur = box?.querySelector<HTMLElement>('[aria-current="page"]')
  if (!box || !cur) return
  const target = cur.offsetLeft - (box.clientWidth - cur.offsetWidth) / 2
  box.scrollTo({ left: Math.max(0, target), behavior: 'auto' })
}
onMounted(revealActiveTab)
watch([code, query, layout], revealActiveTab)

// Watermark refreshes every minute so the timestamp stays current.
const now = ref(new Date())
const timer = setInterval(() => (now.value = new Date()), 60_000)
onBeforeUnmount(() => clearInterval(timer))
const wmText = computed(() => `${viewer.value.name} ${viewer.value.org} ${stamp(now.value)}`)

// ~0.4s shimmer placeholder when switching between workbench pages.
const loading = ref(false)
let skT: ReturnType<typeof setTimeout> | undefined
watch(code, (next, prev) => {
  if (!prev || layout.value !== 'workbench') return
  loading.value = true
  clearTimeout(skT)
  skT = setTimeout(() => (loading.value = false), 420)
})
</script>

<template>
  <div
    :class="cn(
      'flex min-h-screen min-w-0 flex-col',
      layout === 'cockpit' ? 'overflow-x-hidden bg-[#03070F]' : 'bg-background',
    )"
  >
    <template v-if="layout !== 'bare'">
      <!-- security strip -->
      <div class="flex h-[26px] min-w-0 items-center gap-4 overflow-hidden bg-chrome px-5 text-xs whitespace-nowrap text-chrome-ink max-sm:px-3">
        <span class="flex min-w-0 items-center gap-1.5 font-medium text-[#FFD08A]">
          <span class="size-1.5 shrink-0 rounded-full bg-[#FFB547]" /><span class="truncate">本页数据仅限内部工作使用,禁止截图外传</span>
        </span>
        <span class="max-sm:hidden">全程留痕审计</span>
        <div class="flex-1 max-sm:hidden" />
        <span class="max-md:hidden">医保专网 · 政务云 · 10.86.12.47</span>
      </div>

      <!-- header: 端徽标 › 一级菜单(分组) -->
      <header class="sticky top-0 z-40 flex h-[60px] items-center gap-4 border-b border-line-1 bg-white px-5 text-ink-1 max-xl:gap-2.5 max-lg:gap-3 max-lg:px-3 min-[1024px]:max-[1099px]:gap-1.5 min-[1024px]:max-[1099px]:px-3">
        <button
          type="button"
          class="yb-hamburger"
          aria-label="打开菜单"
          data-testid="nav-toggle"
          @click="drawer = true"
        ><Menu class="size-5" aria-hidden="true" /></button>
        <div class="flex shrink-0 items-center gap-2.5 max-sm:min-w-0 max-sm:shrink" :title="platformName.full">
          <div class="flex size-[30px] shrink-0 items-center justify-center rounded-lg bg-brand text-[15px] font-bold text-white">医</div>
          <div class="min-w-0 leading-tight">
            <div class="text-[15px] font-semibold max-sm:truncate">{{ platformName.main }}</div>
            <div v-if="platformName.sub" class="text-[11px] text-ink-4 max-sm:hidden">{{ platformName.sub }}</div>
          </div>
        </div>
        <span class="yb-side shrink-0" :data-side="side" data-testid="side-badge" :title="`当前端:${SIDE_NAME[side]}`" :aria-label="`当前端:${SIDE_NAME[side]}`">
          <component :is="SideIcon" class="size-3.5" aria-hidden="true" /><span class="max-sm:sr-only">{{ SIDE_NAME[side] }}</span>
        </span>
        <span class="h-5 w-px shrink-0 bg-line-1 max-lg:hidden" />
        <nav class="flex min-w-0 flex-1 items-center justify-center gap-0.5 max-lg:hidden min-[1024px]:max-[1099px]:gap-0" aria-label="一级菜单">
          <template v-for="g in groups" :key="g.id">
            <span v-if="g.n && g.n === '01'" class="mx-1.5 h-5 w-px bg-line-1 max-xl:mx-0.5" />
            <span v-if="g.id === 'gov' && side === 'bureau'" class="mx-1.5 h-5 w-px bg-line-1 max-xl:mx-0.5" />
            <NavGroupMenu
              :group="g"
              :uid="g.id"
              :active="g.id === activeGroup?.id"
              :code="code"
              :query="query"
              @go="go"
            />
            <span v-if="g.n && g.n !== '05'" class="text-xs text-ink-6" aria-hidden="true">›</span>
          </template>
        </nav>
        <div class="flex-1 lg:hidden" />
        <UserMenu :viewer="viewer" :logged-in="!!session.current" :can-switch="canSwitch" @switch="goPage('A1')" @logout="signOut" />
      </header>

      <!-- 二级标签行:左端为所选分组(同色),其后是该分组的页面;右侧面包屑 + 分区标签 -->
      <div v-if="layout === 'workbench'" class="yb-l2">
        <div ref="l2Scroll" class="yb-l2-scroll" data-testid="l2-scroll">
        <span v-if="activeGroup" class="yb-l2-group" data-testid="l2-group">
          <component :is="GroupIcon" aria-hidden="true" />
          <span v-if="activeGroup.n" class="yb-num">{{ activeGroup.n }}</span>{{ activeGroup.name }}
        </span>
        <nav class="flex items-center gap-1" aria-label="二级菜单">
          <button
            v-for="it in activeGroup?.items ?? []"
            :key="it.code + (it.query?.who ?? '')"
            type="button"
            class="yb-l2-tab"
            :aria-current="isActiveItem(it, code, query) ? 'page' : undefined"
            @click="go(it)"
          >
            <component :is="iconOf(it.icon)" aria-hidden="true" />{{ it.name }}
          </button>
        </nav>
        </div>
        <NavBreadcrumb :crumb="crumb" class="mr-4 max-xl:hidden" />
        <span
          v-if="viewer.zone"
          :class="cn('shrink-0 rounded-full px-2.5 py-[3px] text-xs font-medium whitespace-nowrap', ZONE_STYLE[viewer.zone.tone])"
        >{{ viewer.zone.label }}</span>
      </div>
    </template>

    <NavDrawer
      v-if="layout !== 'bare'"
      v-model:open="drawer"
      :side="side"
      :groups="groups"
      :active-id="activeGroup?.id"
      :code="code"
      :query="query"
      @go="go"
    />

    <slot />

    <!-- page-switch skeleton -->
    <div
      v-if="loading"
      class="pointer-events-none fixed inset-x-0 top-[132px] bottom-0 z-[35] overflow-hidden bg-background px-4 py-6 lg:px-6 xl:px-8"
    >
      <div class="mx-auto flex max-w-[1600px] flex-col gap-4">
        <div class="flex items-end justify-between">
          <div class="flex flex-col gap-2">
            <div class="yb-skeleton h-[26px] w-[220px] max-w-full rounded-xl" />
            <div class="yb-skeleton h-3.5 w-[380px] max-w-full rounded-xl" />
          </div>
          <div class="yb-skeleton h-9 w-[140px] rounded-xl" />
        </div>
        <div class="grid grid-cols-2 gap-3 lg:grid-cols-4">
          <div v-for="i in 4" :key="i" class="yb-skeleton h-24 rounded-xl" />
        </div>
        <div class="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_360px]">
          <div class="yb-skeleton h-[420px] rounded-xl" />
          <div class="yb-skeleton h-[420px] rounded-xl" />
        </div>
      </div>
    </div>

    <!-- real-name dynamic watermark -->
    <div
      v-if="layout !== 'bare'"
      data-watermark
      class="pointer-events-none fixed inset-0 z-[90] grid auto-rows-[150px] grid-cols-3 overflow-hidden md:grid-cols-4 lg:grid-cols-6"
      :style="{ opacity: 'var(--wm-opacity)' }"
      aria-hidden="true"
    >
      <div v-for="i in 48" :key="i" class="flex items-center justify-center">
        <span class="-rotate-[24deg] text-[13px] whitespace-nowrap text-[#6B7A93]">{{ wmText }}</span>
      </div>
    </div>

    <!-- one-line dark toast centred at the bottom; wide toaster so long messages don't wrap -->
    <Toaster
      position="bottom-center"
      :style="{ '--width': 'min(760px, calc(100vw - 24px))' }"
      :toast-options="{
        unstyled: true,
        class: '!inset-x-0 mx-auto !w-fit max-w-[calc(100vw-24px)] whitespace-nowrap max-sm:whitespace-normal rounded-[10px] bg-chrome px-[18px] py-2.5 text-[13px] text-white shadow-[0_8px_24px_rgba(11,21,38,.25)]',
      }"
    />
  </div>
</template>
