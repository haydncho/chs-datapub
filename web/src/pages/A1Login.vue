<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { ArrowLeft, ChevronRight, Hospital, Landmark } from '@lucide/vue'
import { usePageData } from '@/api/client'
import { demoLogin, isOffline, login, logout, requestSmsCode, selectIdentity } from '@/api/auth'
import { afterLogin } from '@/app/guard'
import { goPage, router } from '@/app/router'
import { landingOf, session, SIDE_NAME, type Side } from '@/app/session'
import { say } from '@/app/shell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import { A1_SEED } from '@/mock/A1'
import BrandPanel from './A1/BrandPanel.vue'
import { DETECTED_CERT_ACCOUNT } from './A1/cert'

const data = usePageData('A1', A1_SEED)

const tab = ref(0)
const idSel = ref(0)
const account = ref('')
const pin = ref('')
const code = ref('')
const busy = ref(false)
const authMethod = ref<'cert' | 'sms'>('cert')

const inputCls = 'h-11 rounded-[10px] border-line-4 px-3.5 text-sm md:text-sm shadow-none'

/** 登录的三步: 0 选择端 · 1 验证身份 · 2 选择身份 */
type Step = 0 | 1 | 2
const STEPS = ['选择端', '验证身份', '选择身份']
const side = ref<Side>(session.current?.identity.side ?? 'bureau')
// already logged in (e.g. 切换身份 from the header) → straight to identity choice within the current side
const step = ref<Step>(session.current ? 2 : 0)
if (session.current) {
  const cur = session.current.identity.id
  idSel.value = Math.max(0, session.current.identities.findIndex(i => i.id === cur))
}

/** identity cards: only the identities of the chosen side (the server filters; the demo session does the same) */
const identities = computed(() => (session.current?.identities ?? []).filter(i => i.side === side.value))
const userName = computed(() => session.current?.user.name ?? data.value.user.name)
const authNote = computed(() => (authMethod.value === 'sms' ? '已通过短信认证' : '已通过证书认证') + ' · 身份决定可见数据范围')
const sideCard = computed(() => data.value.sides.find(x => x.id === side.value) ?? A1_SEED.sides[0]!)
const sideIcon = (id: Side) => (id === 'bureau' ? Landmark : Hospital)

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
    // 服务端对存在与不存在的账号回答相同,不回显手机号
    say('如账号有效,验证码已发送至绑定手机,请查收')
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

/* ---------- step 0 → 1: 选择端 */
function pickSide(s: Side) {
  side.value = s
  step.value = 1
}

/* ---------- step 1 → 2: 登录 */
async function next() {
  if (busy.value) return
  busy.value = true
  const method = tab.value === 0 ? 'cert' : 'sms'
  // 证书登录的账号由本机证书读取(不来自公开的页面数据)
  const acc = method === 'cert' ? DETECTED_CERT_ACCOUNT : account.value
  try {
    let s
    try {
      s = await login(method, acc, method === 'cert' ? pin.value : code.value, side.value)
    } catch (e) {
      if (!isOffline(e)) throw e
      s = demoLogin(acc, side.value) // 无后端:本地演示会话(守卫逻辑与有后端时一致)
    }
    authMethod.value = method
    idSel.value = Math.max(0, s.identities.findIndex(i => i.id === s.identity.id))
    pin.value = ''
    code.value = ''
    step.value = 2
  } catch (e) {
    say((e as Error).message)
  } finally {
    busy.value = false
  }
}

/* ---------- 进入平台 */
async function enter() {
  const r = identities.value[idSel.value]
  if (!r || busy.value) return
  const s = session.current
  if (s && r.id !== s.identity.id) {
    busy.value = true
    try {
      await selectIdentity(r.id)
    } catch (e) {
      say((e as Error).message)
      return
    } finally {
      busy.value = false
    }
  }
  // 被守卫拦下的原目标(仅站内、且该身份有权访问)优先,否则进入身份自己的落地页
  const cur = session.current
  const home = landingOf(r)
  const to = cur
    ? afterLogin(router.currentRoute.value.query.redirect, cur.pages, router.getRoutes().map(x => x.name).filter((n): n is string => typeof n === 'string'), home)
    : home
  goPage(to.code, to.query)
}

/* ---------- 键盘:登录方式标签 / 身份卡片用方向键切换(roving tabindex) */
function moveFocus(e: KeyboardEvent, count: number, cur: number, set: (i: number) => void, selector: string) {
  const delta = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0
  if (!delta || count < 2) return
  e.preventDefault()
  const next = (cur + delta + count) % count
  set(next)
  const box = e.currentTarget as HTMLElement
  void Promise.resolve().then(() => box.querySelectorAll<HTMLElement>(selector)[next]?.focus())
}
function onTabKey(e: KeyboardEvent) {
  moveFocus(e, data.value.loginTabs.length, tab.value, i => { tab.value = i }, '[role="tab"]')
}
function onCardKey(e: KeyboardEvent) {
  moveFocus(e, identities.value.length, idSel.value, i => { idSel.value = i }, '[role="radio"]')
}

/** 回到「选择端」:已有会话则结束会话(换端需要重新登录) */
function restart() {
  step.value = 0
  if (session.current) void logout()
}
</script>

<template>
  <section
    data-screen-label="A1 登录与身份"
    class="grid min-h-screen min-w-0 grid-cols-1 max-lg:grid-rows-[auto_1fr] lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] xl:grid-cols-[minmax(0,1.15fr)_minmax(460px,1fr)] bg-white text-[13px] leading-normal text-ink-1"
  >
    <BrandPanel :data="data" />

    <!-- 1024–1279 两栏等分、表单 440px,避免卡片说明折出孤字;< 1024 单栏 -->
    <div class="flex items-center justify-center bg-white p-10 max-xl:px-9 max-lg:px-5 max-lg:py-8">
      <div class="flex w-full max-w-[420px] flex-col gap-[22px] max-xl:max-w-[440px]">
        <!-- 三步指示 -->
        <ol class="flex items-center gap-2 text-xs" data-testid="login-steps">
          <template v-for="(l, i) in STEPS" :key="l">
            <li class="flex items-center gap-1.5" :class="i === step ? 'font-semibold text-brand' : i < step ? 'text-ink-3' : 'text-ink-5'">
              <span
                :class="cn(
                  'yb-num flex size-5 items-center justify-center rounded-full text-[11px] font-semibold',
                  i === step ? 'bg-brand text-white' : i < step ? 'bg-brand-soft text-brand' : 'bg-surface-3 text-ink-5',
                )"
              >{{ i + 1 }}</span>{{ l }}
            </li>
            <li v-if="i < STEPS.length - 1" class="h-px flex-1 bg-line-2" aria-hidden="true" />
          </template>
        </ol>

        <!-- 1 选择端 -->
        <template v-if="step === 0">
          <div>
            <div class="text-[26px] font-semibold">选择登录端</div>
            <div class="mt-1 text-[13px] text-ink-4">请选择您所属的一端,登录后进入对应的工作区</div>
          </div>
          <div class="flex flex-col gap-3.5">
            <button
              v-for="c in data.sides"
              :key="c.id"
              type="button"
              :data-testid="`side-${c.id}`"
              class="group flex w-full cursor-pointer flex-col gap-3 whitespace-normal rounded-[14px] border-[1.5px] border-line-1 bg-white p-[18px] text-left transition-colors hover:border-brand hover:bg-brand-tint focus-visible:border-brand focus-visible:outline-none"
              @click="pickSide(c.id)"
            >
              <div class="flex items-center gap-3.5">
                <span :class="cn('flex size-12 shrink-0 items-center justify-center rounded-xl', c.id === 'org' ? 'bg-ok-soft text-ok-ink' : 'bg-brand-soft text-brand')">
                  <component :is="sideIcon(c.id)" :size="24" :stroke-width="1.8" />
                </span>
                <div class="min-w-0 flex-1">
                  <div class="text-[17px] font-semibold">{{ c.title }}</div>
                  <span :class="cn('mt-0.5 inline-block rounded-full px-2 py-px text-[11px]', c.id === 'org' ? 'bg-ok-soft text-ok-ink' : 'bg-brand-soft text-brand')">{{ c.zone }}</span>
                </div>
                <ChevronRight :size="20" class="shrink-0 text-ink-5 transition-colors group-hover:text-brand" />
              </div>
              <div class="leading-[1.65] text-pretty text-ink-3">{{ c.desc }}</div>
              <div class="flex flex-wrap gap-1.5">
                <span v-for="r in c.roles" :key="r" class="rounded-md bg-surface-3 px-2 py-0.5 text-xs text-ink-3">{{ r }}</span>
              </div>
              <div :class="cn('text-xs font-medium', c.id === 'org' ? 'text-ok-ink' : 'text-brand')">{{ c.enter }} →</div>
            </button>
          </div>
          <div class="text-xs leading-[1.7] text-pretty text-ink-5">{{ data.agreement }}</div>
        </template>

        <!-- 2 登录 -->
        <template v-else-if="step === 1">
          <div>
            <div class="text-[26px] font-semibold">登录</div>
            <div class="mt-1 text-[13px] text-ink-4">使用统一身份认证进入平台</div>
          </div>
          <div :class="cn('flex items-center gap-3 rounded-xl border px-3.5 py-2.5', side === 'org' ? 'border-ok-soft bg-ok-soft/50' : 'border-brand-line bg-brand-tint')" data-testid="login-side">
            <span :class="cn('flex size-8 shrink-0 items-center justify-center rounded-lg', side === 'org' ? 'bg-ok-soft text-ok-ink' : 'bg-brand-soft text-brand')">
              <component :is="sideIcon(side)" :size="18" :stroke-width="1.8" />
            </span>
            <div class="min-w-0 flex-1">
              <div class="font-semibold">{{ sideCard.title }}</div>
              <div class="truncate text-xs text-ink-4">{{ sideCard.zone }} · {{ sideCard.roles.join('、') }}</div>
            </div>
            <button type="button" class="flex shrink-0 cursor-pointer items-center gap-1 text-xs text-brand max-xl:min-h-11 max-xl:px-2" data-testid="change-side" @click="step = 0">
              <ArrowLeft :size="13" />更换
            </button>
          </div>
          <div class="flex rounded-[10px] bg-surface-3 p-[3px]" role="tablist" aria-label="登录方式" @keydown="onTabKey">
            <button
              v-for="(l, i) in data.loginTabs"
              :key="l"
              type="button"
              role="tab"
              :aria-selected="i === tab"
              :tabindex="i === tab ? 0 : -1"
              :data-testid="`login-tab-${i}`"
              :class="cn(
                'flex-1 cursor-pointer rounded-lg py-2 text-center font-medium whitespace-nowrap max-xl:py-3 focus-visible:outline-2 focus-visible:outline-brand',
                i === tab ? 'bg-white text-ink-1 shadow-[0_1px_3px_rgba(15,23,42,.1)]' : 'text-ink-4',
              )"
              @click="tab = i"
            >{{ l }}</button>
          </div>

          <template v-if="tab === 0">
            <div class="flex flex-col items-center gap-2.5 rounded-xl border-[1.5px] border-dashed border-[#C9D3E1] bg-[#FAFBFD] p-[22px]">
              <span class="flex size-11 items-center justify-center rounded-xl bg-brand-soft">
                <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="var(--brand)" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M7 11V7a5 5 0 0 1 10 0v4M5 11h14v10H5zM12 15v2" /></svg>
              </span>
              <div class="font-semibold">{{ data.cert.title }}</div>
              <div class="text-xs text-ink-4">{{ data.cert.subject }}</div>
            </div>
            <Input v-model="pin" placeholder="证书 PIN 码" aria-label="证书 PIN 码" type="password" autocomplete="off" :class="inputCls" @keydown.enter="next" />
          </template>
          <template v-else>
            <Input v-model="account" placeholder="账号 / 手机号" autocomplete="username" aria-label="账号或手机号" :class="inputCls" />
            <div class="flex gap-2">
              <Input v-model="code" placeholder="短信验证码" aria-label="短信验证码" inputmode="numeric" autocomplete="one-time-code" :class="cn(inputCls, 'min-w-0 flex-1')" @keydown.enter="next" />
              <Button variant="outline" class="h-11 rounded-[10px] border-line-4 px-3.5 text-[13px] font-normal text-brand" @click="sendCode">
                {{ codeLabel }}
              </Button>
            </div>
          </template>

          <Button class="h-[46px] rounded-[10px] text-[15px]" :disabled="busy" @click="next">登录</Button>
          <div class="text-xs leading-[1.7] text-pretty text-ink-5">{{ data.agreement }}</div>
        </template>

        <!-- 3 选择身份 -->
        <template v-else>
          <div>
            <div class="text-[26px] font-semibold">{{ identities.length > 1 ? '选择本次身份' : '确认本次身份' }}</div>
            <div class="mt-1 text-[13px] text-ink-4">{{ userName }} · {{ authNote }}</div>
          </div>
          <div class="flex items-center gap-2 text-xs text-ink-4" data-testid="identity-side">
            <component :is="sideIcon(side)" :size="14" />
            当前登录端:<b class="font-semibold text-ink-2">{{ SIDE_NAME[side] }}</b>
            <span v-if="identities.length > 1">· 共 {{ identities.length }} 个身份可选</span>
          </div>
          <div class="flex flex-col gap-2.5" role="radiogroup" aria-label="本次身份" @keydown="onCardKey">
            <button
              v-for="(r, i) in identities"
              :key="r.id"
              type="button"
              role="radio"
              :aria-checked="i === idSel"
              :tabindex="i === idSel ? 0 : -1"
              data-testid="identity-card"
              :class="cn(
                'flex min-h-14 w-full cursor-pointer items-center gap-3.5 whitespace-normal rounded-xl border-[1.5px] px-4 py-3.5 text-left focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand',
                i === idSel ? 'border-brand bg-brand-tint' : 'border-line-1 bg-white',
              )"
              @click="idSel = i"
              @dblclick="enter"
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
            </button>
          </div>
          <Button class="h-[46px] rounded-[10px] text-[15px]" :disabled="busy" data-testid="enter" @click="enter">进入平台</Button>
          <button type="button" class="cursor-pointer py-1 text-center text-[13px] text-ink-4 hover:text-ink-2 max-xl:py-3" data-testid="relogin" @click="restart">← 重新登录 / 改选端</button>
        </template>
      </div>
    </div>
  </section>
</template>
