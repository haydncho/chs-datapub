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

const field = 'h-11 rounded-[3px] border-line-strong bg-surface text-[13px]'
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
  <div class="fixed inset-0 z-[80] flex overflow-x-hidden overflow-y-auto bg-app">
    <div class="absolute top-6 right-8 z-10">
      <button
        type="button"
        :aria-label="resolved === 'dark' ? '切换为浅色' : '切换为深色'"
        class="flex size-9 cursor-pointer items-center justify-center rounded-[3px] border border-line-strong bg-surface text-ink-sub hover:bg-hover"
        @click="toggle"
      >
        <Moon v-if="resolved === 'dark'" class="size-4" />
        <Sun v-else class="size-4" />
      </button>
    </div>

    <!-- 左:墨色刊头页 -->
    <div class="login-ink relative hidden min-w-0 flex-1 flex-col justify-between overflow-hidden px-[max(64px,calc((100vw_-_1400px)/2_+_64px))] py-14 text-[#F3EFE4] lg:flex">
      <div class="flex items-center gap-4">
        <span class="seal">医</span>
        <div class="flex flex-col gap-1 whitespace-nowrap">
          <span class="text-[13px] tracking-[.28em] text-[#C9BFA8]">示例市医疗保障局 · 医保数据工作组</span>
          <span class="h-px w-24 bg-[#C9BFA8]/40" />
        </div>
      </div>

      <div>
        <div class="lg-rise mb-5 text-[12px] tracking-[.4em] text-[#E0816F]">医保专网 · 政务云</div>
        <h1 class="lg-rise m-0 font-display text-[46px] leading-[1.25] font-bold tracking-[.04em] xl:text-[56px]">医保数据公开<br />定向发布平台</h1>
        <div class="lg-rise mt-7 h-[3px] w-20 bg-[#C2371F]" />
        <p class="lg-rise mt-7 max-w-[520px] text-[15px] leading-[2] text-[#D8D0BC]">按月 / 季 / 年向辖区定点医疗机构定向发布医保数据;经审批的聚合结果才进入发布区。</p>

        <ol class="mt-12 grid max-w-[560px] grid-cols-1 gap-0 border-t border-[#F3EFE4]/20">
          <li v-for="(f, i) in features" :key="f.t" class="lg-rise flex items-baseline gap-5 border-b border-[#F3EFE4]/20 py-3.5" :style="{ animationDelay: `${0.2 + i * 0.06}s` }">
            <span class="w-6 font-display text-[18px] text-[#E0816F] tabular-nums">{{ String(i + 1).padStart(2, '0') }}</span>
            <span class="text-[14px] text-[#EDE6D4]">{{ f.t }}</span>
          </li>
        </ol>
      </div>

      <div class="text-[12px] tracking-[.06em] text-[#9B9484]">数据仅限内部工作使用,严禁外传。登录、查阅、导出全程留痕审计。</div>
    </div>

    <!-- 右:认证卡片 -->
    <div class="relative flex min-h-full flex-1 flex-col items-center justify-center px-4 py-24 lg:flex-none lg:basis-[560px] lg:px-14 lg:py-10">
      <div class="relative w-full max-w-[440px]">
        <div class="relative box-border flex w-full flex-col border border-line-strong bg-surface px-10 pt-9 pb-7" data-testid="login-card">
          <span class="absolute -top-px left-0 h-[3px] w-24 bg-[#C2371F]" />

          <form v-if="step === 'login'" class="relative flex flex-col" @submit.prevent="login">
            <div class="font-display text-[26px] font-bold text-ink">统一身份认证</div>
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
              <div v-if="cfg?.cert" class="mt-5 border border-primary-line bg-primary-tint px-3.5 py-3 text-[12px]" data-testid="cert-card">
                <div class="font-semibold text-primary">已识别数字证书</div>
                <div class="mt-1 text-ink">{{ certLine }}</div>
                <div class="text-ink-muted">颁发:{{ cfg.cert.issuer }} · 有效期至 {{ cfg.cert.expires }}</div>
              </div>
              <div v-else class="mt-5 border border-warning-line bg-warning-soft px-3.5 py-3 text-[12px] text-warning-ink">
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
                  class="h-11 flex-none cursor-pointer rounded-[3px] border border-line-strong bg-surface px-4 text-[13px] whitespace-nowrap text-ink-sub hover:bg-hover disabled:cursor-default disabled:opacity-60"
                  :disabled="resend > 0"
                  @click="sendCode"
                >{{ resend ? `${resend} 秒后重发` : '获取验证码' }}</button>
              </div>
            </template>

            <button
              type="submit"
              :disabled="busy || (method === 'CA' && !cfg?.cert)"
              class="mt-[22px] h-[46px] w-full cursor-pointer rounded-[3px] bg-ink text-[15px] font-semibold tracking-[.3em] text-surface transition-colors hover:bg-primary-solid disabled:cursor-default disabled:opacity-60"
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
            <div class="font-display text-[22px] font-bold text-ink">选择本次登录身份</div>
            <div class="mt-2 text-[13px] text-ink-sub">{{ userName }} · 该账号绑定 {{ identities.length }} 个身份,不同身份的数据范围不同</div>
            <div class="mt-5 flex flex-col gap-2.5">
              <button
                v-for="i in identities"
                :key="i.id"
                type="button"
                class="cursor-pointer border px-4 py-3.5 text-left transition-colors"
                :class="picked === i.id ? 'border-primary bg-primary-tint' : 'border-line-strong bg-surface hover:bg-hover'"
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
              class="mt-[22px] h-[46px] w-full cursor-pointer rounded-[3px] bg-ink text-[15px] font-semibold tracking-[.3em] text-surface transition-colors hover:bg-primary-solid disabled:cursor-default disabled:opacity-60"
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

<style scoped>
.login-ink {
  background:
    linear-gradient(rgba(243, 239, 228, 0.045) 1px, transparent 1px) 0 0 / 100% 56px,
    radial-gradient(900px 500px at 10% 0%, rgba(194, 55, 31, 0.16), transparent 60%),
    #16130F;
}
.seal {
  display: inline-flex;
  width: 52px;
  height: 52px;
  align-items: center;
  justify-content: center;
  background: #C2371F;
  color: #FBF3E4;
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  border-radius: 3px;
  box-shadow: inset 0 0 0 2px rgba(251, 243, 228, 0.35);
}
</style>
