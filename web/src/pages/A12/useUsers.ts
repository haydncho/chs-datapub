import { computed, reactive, ref, type Ref } from 'vue'
import { ApiError, getJson, postJson } from '@/api/client'
import { session } from '@/app/session'
import { say } from '@/app/shell'
import type { A12Data, A12Side, A12User, A12UserStatus } from '@/mock/A12'

export interface Filters { side: 'all' | A12Side; role: string; status: 'all' | A12UserStatus; q: string }

export const SIDE_NAME: Record<A12Side, string> = { bureau: '医保局端', org: '机构端' }
export const STATUS_NAME: Record<A12UserStatus, string> = { on: '正常', expiring: '即将停用', off: '已停用', pending: '待复核' }

/** 用户与权限页的状态:筛选、操作(停用/启用、新增申请、复核)。无后端时在本地种子上乐观更新。 */
export function useUsers(data: Ref<A12Data>) {
  const filters = reactive<Filters>({ side: 'all', role: 'all', status: 'all', q: '' })
  const busy = ref(false)

  /** 当前会话是否召集人;无会话(种子 / 演示模式)视为可操作 */
  const isConvener = computed(() => !session.current || session.current.identity.role === 'convener')
  const canReview = computed(() => !session.current || ['convener', 'admin'].includes(session.current.identity.role))
  const meName = computed(() => session.current?.user.name ?? null)

  const rows = computed(() => {
    const q = filters.q.trim().toLowerCase()
    return data.value.users.filter(u =>
      (filters.side === 'all' || u.side === filters.side)
      && (filters.role === 'all' || u.roleCode === filters.role)
      && (filters.status === 'all' || u.status === filters.status)
      && (!q || [u.name, u.login, u.org, u.role].some(s => s.toLowerCase().includes(q))))
  })

  const sideCount = computed(() => ({
    all: data.value.users.length,
    bureau: data.value.users.filter(u => u.side === 'bureau').length,
    org: data.value.users.filter(u => u.side === 'org').length,
  }))

  function recount() {
    const us = data.value.users
    const real = us.filter(u => u.status !== 'pending')
    data.value.summary = {
      total: real.length,
      bureau: real.filter(u => u.side === 'bureau').length,
      org: real.filter(u => u.side === 'org').length,
      expiring: real.filter(u => u.status === 'expiring').length,
      disabled: real.filter(u => u.status === 'off').length,
      pending: us.length - real.length,
    }
  }

  /** 以服务端为准重新取数(失败则保留本地乐观结果) */
  async function refresh() {
    try {
      const remote = await getJson<A12Data>('/pages/A12')
      if (remote && typeof remote === 'object') data.value = { ...data.value, ...remote }
    } catch { /* 无后端 */ }
  }

  /** @returns 是否成功(服务端明确拒绝时 false 并提示原因;网络不通时按演示模式视为成功) */
  async function send(action: string, payload: object): Promise<{ ok: boolean; offline: boolean }> {
    busy.value = true
    try {
      await postJson(`/actions/A12/${action}`, payload)
      return { ok: true, offline: false }
    } catch (e) {
      if (e instanceof ApiError && e.api) {
        say(e.message || '操作失败')
        return { ok: false, offline: false }
      }
      return { ok: true, offline: true }
    } finally {
      busy.value = false
    }
  }

  async function setEnabled(u: A12User, enabled: boolean) {
    const r = await send('setUserEnabled', { login: u.login, enabled })
    if (!r.ok) return
    if (r.offline) {
      u.enabled = enabled
      u.status = enabled ? 'on' : 'off'
      recount()
    } else await refresh()
    say(enabled ? `已启用 ${u.name}(${u.login})` : `已停用 ${u.name}(${u.login}) · 该账号不能再登录,已登录的会话已作废`)
  }

  async function requestAdd(form: { name: string; login: string; role: string; org: string }): Promise<boolean> {
    const r = await send('requestAddUser', form)
    if (!r.ok) return false
    if (r.offline) {
      const role = data.value.roles.find(x => x.code === form.role)
      data.value.users.push({
        login: form.login, name: form.name, role: role?.name ?? form.role, roleCode: form.role, org: form.org || '—',
        scope: role?.dataScope ?? '', side: role?.side ?? 'bureau', sides: [role?.side ?? 'bureau'], identities: [],
        lastLogin: '—', status: 'pending', enabled: false, requestedBy: meName.value ?? '陈志远', requestedAt: '刚刚',
      })
      recount()
    } else await refresh()
    say(`已提交新增用户申请:${form.name}(${form.login})· 待另一人复核`)
    return true
  }

  async function review(u: A12User, approve: boolean) {
    const r = await send('reviewAddUser', { login: u.login, approve })
    if (!r.ok) return
    if (r.offline) {
      if (approve) {
        u.status = 'on'
        u.enabled = true
        u.lastLogin = '从未登录'
      } else data.value.users = data.value.users.filter(x => x !== u)
      recount()
    } else await refresh()
    say(approve ? `已复核通过,${u.name} 账号已创建` : `已驳回 ${u.name} 的新增申请`)
  }

  function resetFilters() {
    filters.side = 'all'
    filters.role = 'all'
    filters.status = 'all'
    filters.q = ''
  }

  return { filters, rows, sideCount, busy, isConvener, canReview, meName, setEnabled, requestAdd, review, resetFilters }
}
