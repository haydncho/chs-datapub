<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Toaster } from 'vue-sonner'
import 'vue-sonner/style.css'
import { cn } from '@/lib/utils'
import { stamp } from '@/lib/format'
import { VIEWERS, ZONE_STYLE, stageOf, visibleStages, type PageCode } from '@/app/nav'
import { goPage } from '@/app/router'
import { shell } from '@/app/shell'
import { session, sessionViewer } from '@/app/session'
import { logout } from '@/api/auth'
import type { Layout } from '@/app/pages'

const route = useRoute()
const code = computed(() => (route.meta.code as PageCode | undefined) ?? 'cockpit')
const layout = computed(() => (route.meta.layout as Layout | undefined) ?? 'workbench')
const stage = computed(() => stageOf(code.value))
// nav shows only what the logged-in identity may open (everything in demo mode)
const stages = computed(() => visibleStages(session.current?.pages ?? null))
const stagePages = computed(() => stages.value.find(g => g.id === stage.value.id)?.pages ?? stage.value.pages)
// identity: page override (全息图 身份切换) › logged-in session › per-page demo identity
const viewer = computed(() => shell.viewerOverride ?? sessionViewer.value ?? VIEWERS[code.value])
const canSwitch = computed(() => (session.current?.identities.length ?? 0) > 1)
async function signOut() {
  await logout()
  goPage('A1')
}
const stageLabel = computed(() => (stage.value.n ? `环节 ${stage.value.n} · ${stage.value.name}` : stage.value.name))

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
      'flex min-h-screen min-w-[1280px] flex-col',
      layout === 'cockpit' ? 'overflow-x-hidden bg-[#03070F]' : 'bg-background',
    )"
  >
    <template v-if="layout !== 'bare'">
      <!-- security strip -->
      <div class="flex h-[26px] items-center gap-4 bg-chrome px-5 text-xs text-chrome-ink">
        <span class="flex items-center gap-1.5 font-medium text-[#FFD08A]">
          <span class="size-1.5 rounded-full bg-[#FFB547]" />本页数据仅限内部工作使用,禁止截图外传
        </span>
        <span>全程留痕审计</span>
        <div class="flex-1" />
        <span>医保专网 · 政务云 · 10.86.12.47</span>
      </div>

      <!-- header with the five-stage path -->
      <header class="sticky top-0 z-40 flex h-[60px] items-center gap-5 border-b border-line-1 bg-white px-5 text-ink-1">
        <div class="flex shrink-0 items-center gap-2.5">
          <div class="flex size-[30px] items-center justify-center rounded-lg bg-brand text-[15px] font-bold text-white">医</div>
          <div class="leading-tight">
            <div class="text-[15px] font-semibold">医保数据公开</div>
            <div class="text-[11px] text-ink-4">定向发布平台</div>
          </div>
        </div>
        <nav class="flex min-w-0 flex-1 items-center [justify-content:safe_center] overflow-x-auto [scrollbar-width:none]">
          <div v-for="g in stages" :key="g.id" class="flex items-center gap-0.5">
            <span v-if="g.id === 's1' || g.id === 'gov'" class="mx-1.5 h-5 w-px bg-line-1" />
            <button
              type="button"
              :class="cn(
                'flex h-9 cursor-pointer items-center gap-[7px] rounded-lg px-[9px] whitespace-nowrap hover:bg-surface-3',
                g.id === stage.id ? 'bg-brand-soft font-semibold text-brand' : 'font-medium text-ink-2',
              )"
              @click="goPage(g.pages[0]![0])"
            >
              <span
                v-if="g.n"
                :class="cn(
                  'yb-num flex size-[22px] items-center justify-center rounded-md text-xs font-semibold',
                  g.id === stage.id ? 'bg-brand text-white' : 'bg-surface-3 text-ink-4',
                )"
              >{{ g.n }}</span>
              <span>{{ g.name }}</span>
            </button>
            <span v-if="g.n && g.id !== 's5'" class="text-xs text-ink-6">›</span>
          </div>
        </nav>
        <div class="flex shrink-0 items-center gap-2.5">
          <div class="text-right leading-[1.3]">
            <div class="text-[13px] font-medium">
              {{ viewer.name }} <span class="font-normal text-ink-4">· {{ viewer.role }}</span>
            </div>
            <div class="text-[11px] text-ink-4">{{ viewer.scope }}</div>
          </div>
          <div class="flex size-8 items-center justify-center rounded-full bg-brand-soft font-semibold text-brand">
            {{ viewer.name[0] }}
          </div>
          <template v-if="session.current">
            <span class="h-5 w-px bg-line-1" />
            <div class="flex flex-col items-start leading-[1.3]">
              <button
                v-if="canSwitch"
                type="button"
                class="cursor-pointer text-[11px] whitespace-nowrap text-ink-4 hover:text-brand"
                title="切换本次身份"
                @click="goPage('A1')"
              >切换身份</button>
              <button
                type="button"
                class="cursor-pointer text-xs whitespace-nowrap text-ink-3 hover:text-bad"
                data-testid="logout"
                @click="signOut"
              >退出</button>
            </div>
          </template>
        </div>
      </header>

      <!-- sub-tabs of the current stage -->
      <div v-if="layout === 'workbench'" class="flex h-[46px] items-center gap-1 border-b border-line-1 bg-white px-6">
        <span class="mr-2.5 text-xs text-ink-5">{{ stageLabel }}</span>
        <button
          v-for="[id, name] in stagePages"
          :key="id"
          type="button"
          :class="cn(
            'flex h-[46px] cursor-pointer items-center border-b-2 px-3 whitespace-nowrap',
            id === code ? 'border-brand font-semibold text-ink-1' : 'border-transparent text-ink-4',
          )"
          @click="goPage(id)"
        >{{ name }}</button>
        <div class="flex-1" />
        <span
          v-if="viewer.zone"
          :class="cn('rounded-full px-2.5 py-[3px] text-xs font-medium whitespace-nowrap', ZONE_STYLE[viewer.zone.tone])"
        >{{ viewer.zone.label }}</span>
      </div>
    </template>

    <slot />

    <!-- page-switch skeleton -->
    <div
      v-if="loading"
      class="pointer-events-none fixed inset-x-0 top-[132px] bottom-0 z-[35] bg-background px-8 py-6"
    >
      <div class="mx-auto flex max-w-[1600px] flex-col gap-4">
        <div class="flex items-end justify-between">
          <div class="flex flex-col gap-2">
            <div class="yb-skeleton h-[26px] w-[220px] rounded-xl" />
            <div class="yb-skeleton h-3.5 w-[380px] rounded-xl" />
          </div>
          <div class="yb-skeleton h-9 w-[140px] rounded-xl" />
        </div>
        <div class="grid grid-cols-4 gap-3">
          <div v-for="i in 4" :key="i" class="yb-skeleton h-24 rounded-xl" />
        </div>
        <div class="grid grid-cols-[minmax(0,1fr)_360px] gap-4">
          <div class="yb-skeleton h-[420px] rounded-xl" />
          <div class="yb-skeleton h-[420px] rounded-xl" />
        </div>
      </div>
    </div>

    <!-- real-name dynamic watermark -->
    <div
      v-if="layout !== 'bare'"
      data-watermark
      class="pointer-events-none fixed inset-0 z-[90] grid auto-rows-[150px] grid-cols-6 overflow-hidden"
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
      :style="{ '--width': '760px' }"
      :toast-options="{
        unstyled: true,
        class: '!inset-x-0 mx-auto !w-fit max-w-full whitespace-nowrap rounded-[10px] bg-chrome px-[18px] py-2.5 text-[13px] text-white shadow-[0_8px_24px_rgba(11,21,38,.25)]',
      }"
    />
  </div>
</template>
