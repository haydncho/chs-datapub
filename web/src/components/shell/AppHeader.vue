<script setup lang="ts">
import { LogOut, Moon, Sun } from '@lucide/vue'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PAGES, pagePath, type Zone } from '@/lib/nav'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { useTheme } from '@/composables/useTheme'
import { toneTag } from '@/lib/tone'
import { useAuthStore } from '@/stores/auth'
import BrandLogo from './BrandLogo.vue'

/** 顶栏：品牌、医保专网、区标签；右侧主题切换与当前身份（姓名 · 角色 · 机构 / 数据范围）。全站不出现分享、复制链接、二维码入口。 */
defineProps<{ zone?: Zone }>()
const emit = defineEmits<{ logout: [] }>()
const auth = useAuthStore()
const { resolved, toggle } = useTheme()
const u = computed(() => auth.user)
const router = useRouter()

// ---- 全局搜索:按页面名称 / 说明查找当前身份可进入的页面,Enter 跳转,Ctrl/⌘ + K 聚焦
const q = ref('')
const focused = ref(false)
const box = ref<HTMLInputElement | null>(null)
const results = computed(() => {
  const k = q.value.trim().toLowerCase()
  if (!k) return []
  return PAGES.filter((p) => u.value?.pages.includes(p.key) && p.key !== 'D1' && (p.label.toLowerCase().includes(k) || p.summary.toLowerCase().includes(k))).slice(0, 6)
})
function blur() {
  window.setTimeout(() => (focused.value = false), 150)
}
function go(path: string) {
  q.value = ''
  box.value?.blur()
  void router.push(path)
}
function onKey(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    box.value?.focus()
  }
}
onMounted(() => window.addEventListener('keydown', onKey))
onBeforeUnmount(() => window.removeEventListener('keydown', onKey))
const today = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' }).replace(/\//g, '-')
const homeKey = computed(() => u.value?.pages.find((k) => k === 'W0') ?? null)
</script>

<template>
  <header class="shell-glass flex items-center gap-3.5 border-b px-6 py-2.5 whitespace-nowrap">
    <BrandLogo />
    <div class="text-[16px] leading-[1.15] font-semibold tracking-[.03em] text-ink">{{ u?.role === 'INSTITUTION' ? '医保数据公开 · 医疗机构门户' : '医保数据公开定向发布平台' }}</div>
    <span class="rounded-full border border-primary-line-strong px-2.5 py-[1px] text-[11px] text-primary">医保专网</span>
    <span v-if="zone" class="cursor-help rounded-full border px-2.5 py-[1px] text-[11px] font-semibold" :class="toneTag[zone.tone]" :title="zone.hint" data-testid="zone-tag">{{ zone.label }}</span>
    <div class="relative ml-6 hidden w-[320px] xl:block">
      <svg class="absolute top-1/2 left-3 -translate-y-1/2 text-ink-faint" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
      <input
        ref="box"
        v-model="q"
        type="search"
        placeholder="搜索页面或功能…"
        class="h-9 w-full rounded-xl border border-line bg-surface/80 pr-12 pl-9 text-[13px] outline-none placeholder:text-ink-faint focus:border-primary focus:bg-surface"
        data-testid="global-search"
        @focus="focused = true"
        @blur="blur"
        @keydown.enter="results[0] && go(pagePath(results[0]))"
      />
      <kbd class="absolute top-1/2 right-2.5 -translate-y-1/2 rounded-md border border-line bg-subtle px-1.5 text-[10px] text-ink-muted">Ctrl K</kbd>
      <ul v-if="focused && results.length" class="absolute top-[42px] right-0 left-0 z-50 overflow-hidden rounded-xl border border-line-soft bg-popover py-1 shadow-card" data-testid="search-results">
        <li v-for="r in results" :key="r.key">
          <button type="button" class="flex w-full cursor-pointer flex-col px-3.5 py-2 text-left hover:bg-hover" @mousedown.prevent="go(pagePath(r))">
            <span class="text-[13px] font-medium text-ink">{{ r.label }}</span>
            <span class="truncate text-[11px] text-ink-muted">{{ r.summary }}</span>
          </button>
        </li>
      </ul>
    </div>
    <div class="flex-1" />
    <span class="hidden items-center gap-1.5 rounded-xl border border-line bg-surface/80 px-3 py-1.5 text-[12px] text-ink-sub lg:flex" data-testid="today-chip">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M3 10h18M8 3v4M16 3v4" /></svg>今日 · {{ today }}
    </span>
    <button
      v-if="homeKey"
      type="button"
      aria-label="待办提醒"
      title="待办提醒 · 打开工作台"
      class="relative flex size-[30px] cursor-pointer items-center justify-center rounded-full border border-line bg-surface text-ink-sub hover:bg-hover"
      @click="router.push('/w0')"
    >
      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.9 1.9 0 0 0 3.4 0" /></svg>
      <span class="absolute top-0.5 right-0.5 size-2 rounded-full bg-danger-solid ring-2 ring-surface" />
    </button>
    <button
      type="button"
      :aria-label="resolved === 'dark' ? '切换为浅色' : '切换为深色'"
      :title="resolved === 'dark' ? '切换为浅色' : '切换为深色'"
      class="flex size-[30px] cursor-pointer items-center justify-center rounded-full border border-line bg-surface text-ink-sub hover:bg-hover"
      @click="toggle"
    >
      <Moon v-if="resolved === 'dark'" class="size-[15px]" />
      <Sun v-else class="size-[15px]" />
    </button>
    <DropdownMenu v-if="u" :modal="false">
      <DropdownMenuTrigger as-child>
        <button
          type="button"
          class="flex cursor-pointer items-center gap-2.5 rounded-full border border-line bg-surface py-1 pr-4 pl-1 text-left hover:bg-hover"
          data-testid="account"
        >
          <span class="flex size-8 items-center justify-center rounded-full bg-primary-solid text-[13px] font-semibold text-white">{{ u.name[0] }}</span>
          <span class="flex flex-col leading-[1.35]">
            <span class="text-[13px]"><span class="font-semibold text-ink">{{ u.name }}</span><span class="text-ink-muted"> · {{ u.roleLabel }} · {{ u.org }}</span></span>
            <span class="text-[11px] text-ink-faint">数据范围:{{ u.scope }}</span>
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" class="w-60">
        <DropdownMenuLabel class="text-[12px] font-normal text-ink-muted">本次身份:{{ u.roleLabel }}</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem class="cursor-pointer text-[13px]" @select="emit('logout')">
          <LogOut class="size-4" />退出登录
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  </header>
</template>
