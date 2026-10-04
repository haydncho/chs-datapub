<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { usePageData } from '@/api/client'
import { isOffline, login, logout, requestSmsCode, selectIdentity } from '@/api/auth'
import { goPage } from '@/app/router'
import { session } from '@/app/session'
import { say } from '@/app/shell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { A1_SEED, type A1Identity } from '@/mock/A1'
import BrandPanel from './A1/BrandPanel.vue'

const data = usePageData('A1', A1_SEED)

const tab = ref(0)
const idSel = ref(0)
const account = ref('')
const pin = ref('')
const code = ref('')
const busy = ref(false)
const authMethod = ref<'cert' | 'sms'>('cert')

const inputCls = 'h-11 rounded-[10px] border-line-4 px-3.5 text-sm md:text-sm shadow-none'

// already logged in (e.g. 切换身份 from the header) → straight to identity choice
const step = ref<1 | 2>(session.current ? 2 : 1)
if (session.current) {
  const cur = session.current.identity.id
  idSel.value = Math.max(0, session.current.identities.findIndex(i => i.id === cur))
}

/** identity cards: the session's identities when logged in, the demo seed otherwise */
const identities = computed<(A1Identity & { id?: number })[]>(() => session.current?.identities ?? data.value.identities)
const userName = computed(() => session.current?.user.name ?? data.value.user.name)
const authNote = computed(() =>
  session.current && authMethod.value === 'sms' ? data.value.user.authNote.replace('证书', '短信') : data.value.user.authNote,
)

/* ---------- SMS code with 60s cool-down */
const codeLeft = ref(0)
const codeSent = ref(false)
let codeT: ReturnType<typeof setInterval> | undefined
function countdown(sec: number) {
  clearInterval(codeT)
  codeLeft.value = sec
  codeT = setInterval(() => {
    codeLeft.value -= 1
    if (codeLeft.value <= 0) clearInterval(codeT)
  }, 1000)
}
onBeforeUnmount(() => clearInterval(codeT))
const codeLabel = computed(() => (codeLeft.value > 0 ? `已发送 · ${codeLeft.value}s` : codeSent.value ? '重新获取' : '获取验证码'))

async function sendCode() {
  if (codeLeft.value > 0 || busy.value) return
  try {
    const r = await requestSmsCode(account.value)
    codeSent.value = true
    countdown(r.cooldown)
    if (r.phone) say(`验证码已发送至 ${r.phone}`)
  } catch (e) {
    if (isOffline(e)) {
      // demo mode: no backend — behave like the prototype
      codeSent.value = true
      countdown(60)
      return
    }
    const err = e as { message: string; retryAfter?: number }
    if (err.retryAfter) {
      codeSent.value = true
      countdown(err.retryAfter)
    }
    say(err.message)
  }
}

/* ---------- step 1 → 2 */
async function next() {
  if (busy.value) return
  busy.value = true
  const method = tab.value === 0 ? 'cert' : 'sms'
  try {
    const certAccount = data.value.cert.account ?? A1_SEED.cert.account ?? ''
    const s = await login(method, method === 'cert' ? certAccount : account.value, method === 'cert' ? pin.value : code.value)
    authMethod.value = method
    idSel.value = Math.max(0, s.identities.findIndex(i => i.id === s.identity.id))
    pin.value = ''
    code.value = ''
    step.value = 2
  } catch (e) {
    if (isOffline(e)) step.value = 2 // demo mode
    else say((e as Error).message)
  } finally {
    busy.value = false
  }
}

/* ---------- 进入平台 */
async function enter() {
  const r = identities.value[idSel.value]
  if (!r || busy.value) return
  const s = session.current
  if (s && r.id != null && r.id !== s.identity.id) {
    busy.value = true
    try {
      await selectIdentity(r.id)
    } catch (e) {
      if (!isOffline(e)) say((e as Error).message)
      return
    } finally {
      busy.value = false
    }
  }
  goPage(r.target, r.who ? { who: r.who } : undefined)
}

function back() {
  step.value = 1
  if (session.current) void logout()
}
</script>

<template>
  <section
    data-screen-label="A1 登录与身份"
    class="grid min-h-screen min-w-[1280px] grid-cols-[minmax(0,1.15fr)_minmax(460px,1fr)] bg-white text-[13px] leading-normal text-ink-1"
  >
    <BrandPanel :data="data" />

    <div class="flex items-center justify-center bg-white p-10">
      <div class="flex w-full max-w-[400px] flex-col gap-[22px]">
        <template v-if="step === 1">
          <div>
            <div class="text-[26px] font-semibold">登录</div>
            <div class="mt-1 text-[13px] text-ink-4">使用统一身份认证进入平台</div>
          </div>
          <div class="flex rounded-[10px] bg-surface-3 p-[3px]">
            <span
              v-for="(l, i) in data.loginTabs"
              :key="l"
              :class="cn(
                'flex-1 cursor-pointer rounded-lg py-2 text-center font-medium whitespace-nowrap',
                i === tab ? 'bg-white text-ink-1 shadow-[0_1px_3px_rgba(15,23,42,.1)]' : 'text-ink-4',
              )"
              @click="tab = i"
            >{{ l }}</span>
          </div>

          <template v-if="tab === 0">
            <div class="flex flex-col items-center gap-2.5 rounded-xl border-[1.5px] border-dashed border-[#C9D3E1] bg-[#FAFBFD] p-[22px]">
              <span class="flex size-11 items-center justify-center rounded-xl bg-brand-soft">
                <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="var(--brand)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M7 11V7a5 5 0 0 1 10 0v4M5 11h14v10H5zM12 15v2" /></svg>
              </span>
              <div class="font-semibold">{{ data.cert.title }}</div>
              <div class="text-xs text-ink-4">{{ data.cert.subject }}</div>
            </div>
            <Input v-model="pin" placeholder="证书 PIN 码" type="password" autocomplete="off" :class="inputCls" @keydown.enter="next" />
          </template>
          <template v-else>
            <Input v-model="account" placeholder="账号 / 统一社会信用代码" :class="inputCls" />
            <Input placeholder="密码" type="password" :class="inputCls" />
            <div class="flex gap-2">
              <Input v-model="code" placeholder="短信验证码" inputmode="numeric" autocomplete="one-time-code" :class="cn(inputCls, 'min-w-0 flex-1')" @keydown.enter="next" />
              <Button variant="outline" class="h-11 rounded-[10px] border-line-4 px-3.5 text-[13px] font-normal text-brand" @click="sendCode">
                {{ codeLabel }}
              </Button>
            </div>
          </template>

          <Button class="h-[46px] rounded-[10px] text-[15px]" :disabled="busy" @click="next">登录</Button>
          <div class="text-xs leading-[1.7] text-ink-5">{{ data.agreement }}</div>
        </template>

        <template v-else>
          <div>
            <div class="text-[26px] font-semibold">选择本次身份</div>
            <div class="mt-1 text-[13px] text-ink-4">{{ userName }} · {{ authNote }}</div>
          </div>
          <div class="flex flex-col gap-2.5">
            <div
              v-for="(r, i) in identities"
              :key="r.name"
              :class="cn(
                'flex cursor-pointer items-center gap-3.5 rounded-xl border-[1.5px] px-4 py-3.5',
                i === idSel ? 'border-brand bg-brand-tint' : 'border-line-1 bg-white',
              )"
              @click="idSel = i"
            >
              <span
                :class="cn(
                  'flex size-[38px] shrink-0 items-center justify-center rounded-[10px] font-bold',
                  i === idSel
                    ? (r.tone === 'ok' ? 'bg-ok-ink text-white' : 'bg-brand text-white')
                    : (r.tone === 'ok' ? 'bg-ok-soft text-ok-ink' : 'bg-brand-soft text-brand'),
                )"
              >{{ r.initial }}</span>
              <div class="min-w-0 flex-1">
                <div class="font-semibold">{{ r.name }}</div>
                <div class="truncate text-xs text-ink-4">{{ r.desc }}</div>
              </div>
              <span
                :class="cn(
                  'rounded-full px-2 py-0.5 text-[11px] whitespace-nowrap',
                  r.tone === 'ok' ? 'bg-ok-soft text-ok-ink' : 'bg-brand-soft text-brand',
                )"
              >{{ r.zone }}</span>
            </div>
          </div>
          <Button class="h-[46px] rounded-[10px] text-[15px]" :disabled="busy" @click="enter">进入平台</Button>
          <span class="cursor-pointer text-center text-[13px] text-ink-4" @click="back">← 返回登录</span>
        </template>
      </div>
    </div>
  </section>
</template>
