import { defineStore } from 'pinia'
import { ref } from 'vue'
import { authApi } from '@/api'
import { getToken, setToken, setUnauthorizedHandler } from '@/api/http'
import type { Me } from '@/api/types'

/** 当前会话：账号 × 本次身份（角色、机构、数据范围、可见页面）。驱动菜单裁剪、顶栏与水印。 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const user = ref<Me | null>(null)

  function start(t: string, me: Me) {
    token.value = t
    user.value = me
    setToken(t)
  }

  function end() {
    token.value = null
    user.value = null
    setToken(null)
  }

  async function load() {
    if (!token.value) return null
    if (!user.value) user.value = await authApi.me()
    return user.value
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      /* 会话已失效时同样退出 */
    }
    end()
  }

  return { token, user, start, end, load, logout }
})

/** 401：清理会话并回到登录页（由 main.ts 注册路由跳转）。 */
export function registerUnauthorized(goLogin: () => void) {
  setUnauthorizedHandler(() => {
    useAuthStore().end()
    goLogin()
  })
}
