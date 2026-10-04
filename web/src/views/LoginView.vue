<script setup lang="ts">
import { useIntervalFn } from '@vueuse/core'
import { Moon, Sun } from '@lucide/vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api'
import type { AuthConfig, Identity } from '@/api/types'
import { Input } from '@/components/ui/input'
import { useTheme } from '@/composables/useTheme'
import { notify, notifyError } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

/**
 * A1 登录与身份：省医保统一身份认证 —— 数字证书（UKey）或 账号 + 密码 + 短信验证码；
 * 认证后选择本次登录身份（不同身份数据范围不同）。页面不出现任何公网元素（二维码、第三方登录）。
 */
const router = useRouter()
const auth = useAuthStore()
const { resolved, toggle } = useTheme()

const cfg = ref<AuthConfig | null>(null)
const method = ref<'CA' | 'PASSWORD'>('CA')
const pin = ref('')
const username = ref('')
const password = ref('')
const smsCode = ref('')
const resend = ref(0)
const busy = ref(false)

const step = ref<'login' | 'role'>('login')
const ticket = ref('')
const userName = ref('')
const identities = ref<Identity[]>([])
const picked = ref<number | null>(null)

onMounted(async () => {
  if (auth.token) {
    try {
      const me = await auth.load()
      if (me?.pages.length) return void router.replace({ name: me.pages[0] })
    } catch {
      auth.end()
    }
  }
  try {
    cfg.value = await authApi.config()
    if (!cfg.value.cert) method.value = 'PASSWORD'
  } catch (e) {
    notifyError(e)
  }
})

const { pause, resume } = useIntervalFn(
  () => {
    resend.value = Math.max(0, resend.value - 1)
    if (!resend.value) pause()
  },
  1000,
  { immediate: false },
)

async function sendCode() {
  if (resend.value) return
  if (!username.value.trim()) return notify('请先输入账号')
  try {
    const r = await authApi.sms(username.value.trim())
    resend.value = r.resendSeconds
    resume()
    notify(`验证码已发送至登记手机 ${r.sentTo}`)
  } catch (e) {
    notifyError(e)
  }
}

async function login() {
  busy.value = true
  try {
    const r = await authApi.login(
      method.value === 'CA'
        ? { method: 'CA', pin: pin.value }
        : { method: 'PASSWORD', username: username.value.trim(), password: password.value, smsCode: smsCode.value },
    )
    ticket.value = r.ticket
    userName.value = r.name
    identities.value = r.identities
    picked.value = r.identities[0]?.id ?? null
    if (r.identities.length === 1) await enter()
    else step.value = 'role'
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

async function enter() {
  if (picked.value == null) return
  busy.value = true
  try {
    const r = await authApi.identity(ticket.value, picked.value)
    auth.start(r.token, r.user)
    const home = identities.value.find((i) => i.id === picked.value)?.home
    await router.replace({ name: home && r.user.pages.includes(home) ? home : r.user.pages[0] })
  } catch (e) {
    notifyError(e)
    step.value = 'login'
  } finally {
    busy.value = false
  }
}

const field = 'h-11 rounded-[10px] border-(--c-lg-field) bg-transparent text-[13px]'
const checks = ['专网地址 10.86.12.47', '终端准入通过', '浏览器版本符合']
const features = [
  { t: '医保专网 / 政务云部署,无互联网出口', d: 'M12 2l8 4v6c0 5-3.4 9.4-8 10-4.6-.6-8-5-8-10V6z' },
  { t: '仅限准入的专网终端访问', d: 'M3 5h18v11H3zM8 21h8M12 16v5' },
  { t: '省医保统一身份认证平台', d: 'M12 3l7.5 2.6v6.1c0 4.8-3.1 8.7-7.5 10-4.4-1.3-7.5-5.2-7.5-10V5.6zM9.2 12.2l2 2 3.6-3.8' },
  { t: '定向发布:多源归集 → 指标配置 → 审批发布 → 全息展示', d: 'M3 3v18h18M7 15l4-5 3 3 5-7' },
]
const certLine = computed(() => (cfg.value?.cert ? `${cfg.value.cert.holder} · ${cfg.value.cert.org}` : ''))
</script>

<template>
  <div class="fixed inset-0 z-[80] flex overflow-x-hidden overflow-y-auto bg-(--c-lg-bg)">
    <div class="absolute top-6 right-8 z-10">
      <button
        type="button"
        :aria-label="resolved === 'dark' ? '切换为浅色' : '切换为深色'"
        class="flex size-9 cursor-pointer items-center justify-center rounded-full border border-white/45 bg-white/16 text-white backdrop-blur-[10px] hover:bg-white/24"
        @click="toggle"
      >
        <Moon v-if="resolved === 'dark'" class="size-4" />
        <Sun v-else class="size-4" />
      </button>
    </div>

    <!-- 左:品牌与部署说明 -->
    <div class="relative hidden min-w-0 flex-1 flex-col justify-center overflow-hidden py-0 pr-10 pl-[max(76px,calc((100vw_-_1400px)/2_+_76px))] text-white lg:flex">
      <div class="pointer-events-none absolute inset-0 hidden bg-[radial-gradient(55%_50%_at_18%_8%,rgba(59,130,246,.34),transparent),radial-gradient(48%_58%_at_88%_92%,rgba(99,102,241,.26),transparent)] dark:block" />
      <div class="absolute -top-[90px] -right-10 size-[520px] animate-[lgFloat_16s_ease-in-out_infinite] rounded-full bg-white/7" />
      <div class="absolute -bottom-[190px] -left-40 size-[440px] animate-[lgFloat2_20s_ease-in-out_infinite] rounded-full bg-white/6" />
      <div class="absolute top-[120px] right-[180px] size-[150px] animate-[lgFloat2_14s_ease-in-out_infinite] rounded-full border border-white/22" />
      <div class="absolute inset-0 bg-[radial-gradient(rgba(255,255,255,.9)_1.2px,transparent_1.2px)] bg-size-[26px_26px] opacity-[.13] [mask-image:radial-gradient(60%_60%_at_75%_30%,#000,transparent)]" />

      <div class="absolute top-[52px] left-[max(76px,calc((100vw_-_1400px)/2_+_76px))] flex items-center gap-4">
        <div class="flex size-14 items-center justify-center rounded-[14px] bg-white/16 text-[26px] font-bold">医</div>
        <div class="flex flex-col gap-0.5 whitespace-nowrap">
          <span class="text-[20px] font-bold tracking-[.04em]">医保数据公开定向发布平台</span>
          <span class="text-[12px] tracking-[.06em] text-white/75">示例市医疗保障局 · 医保数据工作组</span>
        </div>
      </div>

      <div class="lg-rise mb-[18px] flex items-center gap-[7px] self-start rounded-full border border-white/38 bg-white/16 px-3.5 py-[5px] text-[13px] font-semibold tracking-[.08em] shadow-[inset_0_1px_0_rgba(255,255,255,.4)] backdrop-blur-[10px]">
        <span class="size-1.5 rounded-full bg-(--c-lg-o1a)" />医保专网 · 政务云
      </div>
      <div class="lg-rise text-[36px] leading-[1.25] font-bold whitespace-nowrap xl:text-[42px]">医保数据公开定向发布平台</div>
      <div class="lg-rise mt-[22px] h-[3px] w-16 rounded-[2px] bg-white/70" />
      <div class="lg-rise mt-[22px] max-w-[560px] text-[15px] leading-[2] text-white/82">按月 / 季 / 年向辖区定点医疗机构定向发布医保数据;经审批的聚合结果才进入发布区。</div>
      <div class="mt-[46px] flex flex-col gap-[20px]">
        <div v-for="(f, i) in features" :key="f.t" class="lg-rise flex items-center gap-[18px]" :style="{ animationDelay: `${0.2 + i * 0.06}s` }">
          <div class="flex size-[42px] flex-none items-center justify-center rounded-[10px] border border-white/12 bg-white/14">
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="rgba(255,255,255,.92)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path :d="f.d" /></svg>
          </div>
          <div class="text-[15px] whitespace-nowrap text-white/94">{{ f.t }}</div>
        </div>
      </div>
      <div class="absolute right-10 bottom-10 left-[max(76px,calc((100vw_-_1400px)/2_+_76px))] text-[13px] text-white/55">
        数据仅限内部工作使用,严禁外传。登录、查阅、导出全程留痕审计。
      </div>
    </div>

    <!-- 右:液态玻璃认证卡片 -->
    <div class="relative flex min-h-full flex-1 flex-col items-center justify-center px-4 py-24 lg:flex-none lg:pt-[76px] lg:pr-[max(56px,calc((100vw_-_1400px)/2_+_56px))] lg:pb-6 lg:pl-6">
      <div class="relative w-full max-w-[480px]">
        <div class="absolute -top-[70px] -right-[90px] size-[300px] animate-[lgFloat_14s_ease-in-out_infinite] rounded-full bg-[radial-gradient(circle_at_35%_35%,var(--c-lg-o1a),var(--c-lg-o1b)_70%)] opacity-75 blur-[6px] dark:opacity-50" />
        <div class="absolute -bottom-[60px] -left-[90px] size-[240px] animate-[lgFloat2_17s_ease-in-out_infinite] rounded-full bg-[radial-gradient(circle_at_40%_40%,var(--c-lg-o2a),var(--c-lg-o2b)_72%)] opacity-70 blur-[4px] dark:opacity-45" />
        <div
          class="relative box-border flex w-full flex-col overflow-hidden rounded-[24px] border border-white/65 bg-[linear-gradient(145deg,rgba(255,255,255,.86),rgba(255,255,255,.72))] px-12 pt-11 pb-8 shadow-[0_30px_80px_rgba(15,23,42,.28),inset_0_1px_0_rgba(255,255,255,.95)] backdrop-blur-[26px] backdrop-saturate-[170%] dark:border-white/12 dark:bg-[linear-gradient(145deg,rgba(26,33,44,.86),rgba(17,22,30,.74))]"
          data-testid="login-card"
        >
          <div class="pointer-events-none absolute top-0 right-0 left-0 h-[46%] rounded-t-[24px] bg-[linear-gradient(180deg,rgba(255,255,255,.55),rgba(255,255,255,0))] dark:bg-[linear-gradient(180deg,rgba(255,255,255,.06),rgba(255,255,255,0))]" />

          <form v-if="step === 'login'" class="relative flex flex-col" @submit.prevent="login">
            <div class="text-[26px] font-bold text-ink">统一身份认证</div>
            <div class="mt-2 text-[13px] text-ink-sub">省医保统一身份认证平台</div>

            <div class="mt-6 flex gap-5 border-b border-line" role="tablist">
              <button
                v-for="t in ([['CA', '数字证书(UKey)'], ['PASSWORD', '账号 + 短信验证码']] as const)"
                :key="t[0]"
                type="button"
                role="tab"
                :aria-selected="method === t[0]"
                class="-mb-px cursor-pointer border-b-2 px-0.5 py-2 text-[13px]"
                :class="method === t[0] ? 'border-primary font-semibold text-primary' : 'border-transparent text-ink-sub'"
                @click="method = t[0]"
              >{{ t[1] }}</button>
            </div>

            <template v-if="method === 'CA'">
              <div v-if="cfg?.cert" class="mt-5 rounded-[10px] border border-primary-line bg-primary-tint/70 px-3.5 py-3 text-[12px]" data-testid="cert-card">
                <div class="font-semibold text-primary">已识别数字证书</div>
                <div class="mt-1 text-ink">{{ certLine }}</div>
                <div class="text-ink-muted">颁发:{{ cfg.cert.issuer }} · 有效期至 {{ cfg.cert.expires }}</div>
              </div>
              <div v-else class="mt-5 rounded-[10px] border border-warning-line bg-warning-soft px-3.5 py-3 text-[12px] text-warning-ink">
                未识别到数字证书,请插入 UKey 后刷新页面,或改用账号 + 短信验证码。
              </div>
              <label class="mt-4 mb-1.5 text-[12px] text-ink-sub" for="lg-pin">证书 PIN 码</label>
              <Input id="lg-pin" v-model="pin" type="password" autocomplete="off" placeholder="请输入证书 PIN 码" :class="field" />
            </template>
            <template v-else>
              <label class="mt-5 mb-1.5 text-[12px] text-ink-sub" for="lg-user">账号</label>
              <Input id="lg-user" v-model="username" autocomplete="username" placeholder="账号 / 工号" :class="field" />
              <label class="mt-4 mb-1.5 text-[12px] text-ink-sub" for="lg-pwd">密码</label>
              <Input id="lg-pwd" v-model="password" type="password" autocomplete="current-password" placeholder="请输入密码" :class="field" />
              <label class="mt-4 mb-1.5 text-[12px] text-ink-sub" for="lg-code">短信验证码</label>
              <div class="flex gap-2.5">
                <Input id="lg-code" v-model="smsCode" inputmode="numeric" autocomplete="one-time-code" placeholder="6 位验证码" :class="[field, 'min-w-0 flex-1']" />
                <button
                  type="button"
                  class="h-11 flex-none cursor-pointer rounded-[10px] border border-(--c-lg-field) bg-transparent px-4 text-[13px] whitespace-nowrap text-ink-sub hover:bg-hover disabled:cursor-default disabled:text-ink-faint"
                  :disabled="resend > 0"
                  @click="sendCode"
                >{{ resend ? `${resend} 秒后重发` : '获取验证码' }}</button>
              </div>
            </template>

            <button
              type="submit"
              :disabled="busy || (method === 'CA' && !cfg?.cert)"
              class="mt-[22px] h-[46px] w-full cursor-pointer rounded-xl border border-primary bg-[linear-gradient(180deg,var(--c-brand-a),var(--c-primary-solid))] text-[15px] font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,.35),inset_0_1px_0_rgba(255,255,255,.35)] disabled:cursor-default disabled:opacity-45"
              data-testid="login-submit"
            >{{ busy ? '认证中…' : '登 录' }}</button>

            <div class="mt-5 flex justify-center gap-4 text-[11px] text-ink-muted">
              <span v-for="c in checks" :key="c"><span class="text-success">●</span> {{ c }}</span>
            </div>
            <div v-if="cfg?.demo" class="mt-3 text-center text-[12px] text-ink-muted" data-testid="demo-hint">
              演示模式:UKey 证书 PIN 任意;或账号 {{ cfg.demoAccounts?.join(' / ') }},任意密码,验证码 {{ cfg.demoSmsCode }}
            </div>
          </form>

          <div v-else class="relative flex flex-col" data-testid="identity-step">
            <div class="text-[22px] font-bold text-ink">选择本次登录身份</div>
            <div class="mt-2 text-[13px] text-ink-sub">{{ userName }} · 该账号绑定 {{ identities.length }} 个身份,不同身份的数据范围不同</div>
            <div class="mt-5 flex flex-col gap-2.5">
              <button
                v-for="i in identities"
                :key="i.id"
                type="button"
                class="cursor-pointer rounded-xl border-[1.5px] px-4 py-3.5 text-left transition-colors"
                :class="picked === i.id ? 'border-primary bg-primary-tint/80' : 'border-(--c-lg-field) bg-transparent hover:bg-hover'"
                :data-identity="i.role"
                @click="picked = i.id"
              >
                <div class="flex justify-between"><span class="text-[13px] font-semibold text-ink">{{ i.roleLabel }}</span><span class="text-[11px] text-ink-muted">{{ i.homeLabel }}</span></div>
                <div class="mt-0.5 text-[12px] text-ink-sub">{{ i.orgDetail }}</div>
                <div class="mt-1.5 text-[12px] text-ink">数据范围:{{ i.scope }}</div>
              </button>
            </div>
            <button
              type="button"
              :disabled="busy || picked == null"
              class="mt-[22px] h-[46px] w-full cursor-pointer rounded-xl border border-primary bg-[linear-gradient(180deg,var(--c-brand-a),var(--c-primary-solid))] text-[15px] font-semibold text-white shadow-[0_8px_20px_rgba(37,99,235,.35),inset_0_1px_0_rgba(255,255,255,.35)] disabled:opacity-45"
              data-testid="enter-btn"
              @click="enter"
            >进入工作台</button>
            <button type="button" class="mt-3 cursor-pointer text-center text-[12px] text-ink-muted hover:text-primary" @click="step = 'login'">返回重新认证</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
