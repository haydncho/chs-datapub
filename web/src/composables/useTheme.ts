import { computed, ref } from 'vue'

/** 主题（浅色 / 深色 / 跟随系统）：设备偏好，存 localStorage（与 public/theme-init.js 一致）。 */
export type ThemeMode = 'light' | 'dark' | 'system'

const KEY = 'dpub.theme'
const mq = typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null

function read(): ThemeMode {
  try {
    const v = localStorage.getItem(KEY)
    return v === 'light' || v === 'dark' || v === 'system' ? v : 'system'
  } catch {
    return 'system'
  }
}

const mode = ref<ThemeMode>(read())
const systemDark = ref(mq?.matches ?? false)
const resolved = computed(() => (mode.value === 'system' ? (systemDark.value ? 'dark' : 'light') : mode.value))

function apply() {
  const root = document.documentElement
  root.classList.toggle('dark', resolved.value === 'dark')
  root.dataset.theme = resolved.value
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', resolved.value === 'dark' ? '#0d1117' : '#f7f8fa')
}

export function initTheme() {
  apply()
  mq?.addEventListener('change', (e) => {
    systemDark.value = e.matches
    apply()
  })
}

export function useTheme() {
  function setTheme(m: ThemeMode) {
    mode.value = m
    try {
      localStorage.setItem(KEY, m)
    } catch {
      /* 存储被禁：本次会话内生效 */
    }
    apply()
  }
  /** 顶栏按钮：浅 ↔ 深 循环。 */
  function toggle() {
    setTheme(resolved.value === 'dark' ? 'light' : 'dark')
  }
  return { mode, resolved, setTheme, toggle }
}
