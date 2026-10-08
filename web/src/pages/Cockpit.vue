<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePageData, runAction } from '@/api/client'
import { session } from '@/app/session'
import { setViewer, say } from '@/app/shell'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { COCKPIT_SEED, type CockpitData, type CockpitSavedSubscription } from '@/mock/cockpit'
import AlarmOverlay from './cockpit/AlarmOverlay.vue'
import CockpitBottom from './cockpit/CockpitBottom.vue'
import CockpitCenter from './cockpit/CockpitCenter.vue'
import CockpitLoop from './cockpit/CockpitLoop.vue'
import CockpitRight from './cockpit/CockpitRight.vue'
import CockpitTop from './cockpit/CockpitTop.vue'
import SecondScreen from './cockpit/SecondScreen.vue'
import SubscribeDialog from './cockpit/SubscribeDialog.vue'
import { COCKPIT_KEY, IDN_TO_WHO, SCR_BG, SCR_GLOW, SCR_VARS, WHO_TO_IDN, createCockpitStore } from './cockpit/store'
import { vPress } from '@/lib/a11y'

const raw = usePageData('cockpit', COCKPIT_SEED)

/**
 * 全景图展示什么,由登录身份决定:医院身份 → 本院视角(机构名取自该身份所属医院);
 * 其余身份 → 市医保局视角。不再提供视角切换。无会话(仅开发回退)时保留两个视角可切换。
 */
const ownId = computed<'hosp' | 'conv' | null>(() => {
  const role = session.current?.identity.role
  return role ? (role === 'hospital' ? 'hosp' : 'conv') : null
})
const data = computed<CockpitData>(() => {
  const d = raw.value
  const own = ownId.value
  if (!own) return d
  let ids = d.identities.filter(x => x.id === own)
  if (ids.length === 0) ids = d.identities.slice(0, 1) // 服务端已按身份裁剪过
  if (own !== 'hosp') return { ...d, identities: ids }
  const org = session.current?.identity.orgName
  if (org) ids = ids.map(x => ({ ...x, org, orgShort: org }))
  // 本院视角不含全市机构明细、异地流向与公开矩阵(服务端同样不下发)
  return { ...d, identities: ids, institutions: [], flows: [], matrix: [] }
})
const store = createCockpitStore(data)
provide(COCKPIT_KEY, store)
const { s, I, views, rotN, scr, alarm, go2, replay, restart } = store
/** 机构端且没有本院数据(非示例数据所属医院):只显示标题与空状态,不画任何本院图表 */
const noOwn = computed(() => data.value.noOwnData === true && I.value.id === 'hosp')

/* ---------- identity ⇄ ?who= */
const route = useRoute()
const router = useRouter()
function syncFromQuery() {
  if (ownId.value) { // 按登录身份固定视角:忽略 ?who=,并把地址规范成自己的视角
    if (s.idn !== 0) go2({ idn: 0, sel: null })
    if (route.query.who !== ownId.value) router.replace({ query: { ...route.query, who: ownId.value } })
    return
  }
  const w = route.query.who
  const idn = typeof w === 'string' ? WHO_TO_IDN[w] : undefined
  if (idn != null && idn !== s.idn) go2({ idn, sel: null })
}
watch(() => route.query.who, syncFromQuery)
function pickIdentity(i: number) {
  go2({ idn: i, sel: null })
  router.replace({ query: { ...route.query, who: IDN_TO_WHO[i] } })
}
const idTabs = computed(() =>
  data.value.identities.slice(0, 2).map((x, i) => ({ i, label: x.tab, sub: x.orgShort, on: i === s.idn })),
)

watch(
  [I, ownId],
  ([v]) => {
    if (ownId.value) { setViewer(null); return } // 顶栏显示真正登录的用户
    setViewer({
      name: v.name,
      role: v.role,
      scope: v.scope,
      zone: { label: v.zone, tone: v.id === 'hosp' ? 'green' : 'blue' },
      org: v.orgShort,
    })
  },
  { immediate: true },
)
watch(ownId, syncFromQuery)

/* ---------- 竖屏提示(当次会话记住关闭) */
const PORTRAIT_KEY = 'yb-cockpit-portrait-hint'
const portrait = ref(false)
const portraitHidden = ref(false)
try { portraitHidden.value = sessionStorage.getItem(PORTRAIT_KEY) === '1' } catch { /* 无存储 */ }
const checkPortrait = () => { portrait.value = window.innerWidth < window.innerHeight }
function closePortrait() {
  portraitHidden.value = true
  try { sessionStorage.setItem(PORTRAIT_KEY, '1') } catch { /* 仅本次有效 */ }
}
checkPortrait()
window.addEventListener('resize', checkPortrait)
onBeforeUnmount(() => window.removeEventListener('resize', checkPortrait))

/* ---------- scale-to-fit 1920×1080 canvas */
const box = ref<HTMLDivElement | null>(null)
const fit = ref({ cs: 0.5, cx: 0, cy: 0, fs: false })
function measure() {
  const el = box.value
  if (!el) return
  const fs = document.fullscreenElement === el
  const w = el.clientWidth
  const h = el.clientHeight
  const TW = s.dual ? 3840 : 1920
  const sc = fs ? Math.min(w / TW, h / 1080) : w / TW
  const cx = (w - TW * sc) / 2
  const cy = fs ? (h - 1080 * sc) / 2 : 0
  const f = fit.value
  if (Math.abs(sc - f.cs) > 0.0005 || fs !== f.fs || Math.abs(cx - f.cx) > 0.5 || Math.abs(cy - f.cy) > 0.5) fit.value = { cs: sc, cx, cy, fs }
}
const screenStyle = (x: number) => ({
  left: x.toFixed(1) + 'px',
  top: fit.value.cy.toFixed(1) + 'px',
  transform: `scale(${fit.value.cs.toFixed(4)})`,
  background: SCR_BG[scr.value],
  ...SCR_VARS[scr.value],
})
const boxStyle = computed(() => ({
  height: fit.value.fs ? '100%' : Math.round(1080 * fit.value.cs) + 'px',
  background: SCR_BG[scr.value],
}))
const glow = computed(() => SCR_GLOW[scr.value])

function toggleFs() {
  const el = box.value
  if (!el) return
  if (document.fullscreenElement) void document.exitFullscreen()
  else void el.requestFullscreen?.()
}
function goCast() {
  s.rot = true
  s.rotT = 0
  const el = box.value
  if (el && el.requestFullscreen && !document.fullscreenElement) void el.requestFullscreen()
}
function toggleDual() {
  s.dual = !s.dual
  setTimeout(measure, 30)
}

/* ---------- alarm + sound */
/*
 * 浏览器只允许在用户手势之后创建 / 启动 AudioContext。进入页面时还没有手势,就先不响,
 * 等第一次点击 / 按键时若告警仍未确认再补响一次 —— 控制台不再出现 AudioContext 警告。
 */
let ac: AudioContext | null = null
const hasGesture = () => (navigator as Navigator & { userActivation?: { hasBeenActive: boolean } }).userActivation?.hasBeenActive === true
let beepPending = false
function onFirstGesture() {
  document.removeEventListener('pointerdown', onFirstGesture, true)
  document.removeEventListener('keydown', onFirstGesture, true)
  if (beepPending && s.alOn && s.snd) beep()
  beepPending = false
}
function beep() {
  if (!hasGesture()) {
    if (!beepPending) {
      beepPending = true
      document.addEventListener('pointerdown', onFirstGesture, true)
      document.addEventListener('keydown', onFirstGesture, true)
    }
    return
  }
  try {
    const W = window as unknown as { AudioContext?: typeof AudioContext; webkitAudioContext?: typeof AudioContext }
    const Ctor = W.AudioContext ?? W.webkitAudioContext
    if (!Ctor) return
    const C = ac ?? (ac = new Ctor())
    ;[0, 0.32, 0.64].forEach(t => {
      const o = C.createOscillator()
      const g = C.createGain()
      o.type = 'square'
      o.frequency.value = t === 0.32 ? 660 : 880
      g.gain.setValueAtTime(0.0001, C.currentTime + t)
      g.gain.exponentialRampToValueAtTime(0.12, C.currentTime + t + 0.02)
      g.gain.exponentialRampToValueAtTime(0.0001, C.currentTime + t + 0.24)
      o.connect(g)
      g.connect(C.destination)
      o.start(C.currentTime + t)
      o.stop(C.currentTime + t + 0.28)
    })
  } catch { /* audio unavailable */ }
}
/*
 * 告警由真实的高等级预警触发(当前身份提醒里的预警 / 提醒函)。是否已确认以服务端为准:
 * 页面数据里每条提醒带 acked(cockpit_alarm_ack);只有离线演示(无服务端状态)时才退回本会话记录。
 * 等服务端数据到达(或超时)后再判断,避免先按演示数据弹出、再被服务端状态收回。
 */
const ACK_KEY = 'yb-alarm-ack'
function sessionAcked(): string[] {
  try { return JSON.parse(sessionStorage.getItem(ACK_KEY) ?? '[]') as string[] } catch { return [] }
}
const alarmKey = () => `${I.value.id}|${alarm.value.key}`
function isAcked() {
  const a = alarm.value
  if (!a.key || s.acked.includes(alarmKey())) return true
  if (a.serverAcked !== undefined) return a.serverAcked
  return sessionAcked().includes(alarmKey())
}
const ready = ref(false)
watch(raw, () => { ready.value = true })
const readyT = setTimeout(() => { ready.value = true }, 2500)
function checkAlarm() {
  if (!ready.value) return
  if (isAcked()) { s.alOn = false; return }
  if (s.alOn) return
  s.alOn = true
  if (s.snd) beep()
}
const acking = ref(false)
async function ackAlarm() {
  if (acking.value) return
  const a = alarm.value
  const key = alarmKey()
  acking.value = true
  const r = await runAction('cockpit', 'ackAlarm', { identity: I.value.id, type: a.ty, text: a.t })
  acking.value = false
  if (!r.ok) { say(r.error || '确认未保存,请重试'); return }
  s.acked = [...s.acked, key]
  try { sessionStorage.setItem(ACK_KEY, JSON.stringify([...sessionAcked(), key])) } catch { /* 无存储时仅本次有效 */ }
  s.alOn = false
  say('已确认处置')
}
// 服务端数据到达、切换身份或预警内容 / 确认状态变化时重新检查
watch([ready, () => alarmKey(), () => alarm.value.serverAcked], () => checkAlarm())

/* ---------- subscription(草稿在对话框里;服务端接受后才更新全局并关闭) */
const savingSub = ref(false)
async function saveSub(sub: Omit<CockpitSavedSubscription, 'updatedAt'>) {
  if (savingSub.value) return
  savingSub.value = true
  const idn = I.value.id
  const r = await runAction<{ result?: CockpitSavedSubscription }>('cockpit', 'saveSubscription', { identity: idn, ...sub })
  savingSub.value = false
  if (!r.ok) { say(r.error || '订阅未保存'); return }
  s.subSaved = { ...s.subSaved, [idn]: r.data?.result ?? { ...sub } }
  s.subOn = false
  say(sub.enabled ? '订阅已保存' : '订阅已停用')
}

/* ---------- clock + auto-rotation */
let iv: ReturnType<typeof setInterval> | undefined
let ro: ResizeObserver | null = null
onMounted(() => {
  syncFromQuery()
  replay()
  restart()
  checkAlarm()
  iv = setInterval(() => {
    if (s.rot) {
      const n = s.rotT + 1
      if (n >= rotN.value) {
        const vs = views.value
        if (vs.length <= 1) { s.rotT = 0; s.now = Date.now(); return } // 只有一个视图时不重播
        const cur = vs.includes(s.view) ? s.view : 'bub'
        const nx = vs[(vs.indexOf(cur) + 1) % vs.length] ?? 'bub'
        go2({ view: nx, sel: null, rotT: 0, now: Date.now() })
        return
      }
      s.rotT = n
    }
    s.now = Date.now()
  }, 1000)
  document.addEventListener('fullscreenchange', measure)
  if (box.value) {
    ro = new ResizeObserver(() => measure())
    ro.observe(box.value)
  }
  measure()
})
onBeforeUnmount(() => {
  clearInterval(iv)
  clearTimeout(readyT)
  document.removeEventListener('pointerdown', onFirstGesture, true)
  document.removeEventListener('keydown', onFirstGesture, true)
  store.dispose()
  ro?.disconnect()
  document.removeEventListener('fullscreenchange', measure)
  if (document.fullscreenElement === box.value) void document.exitFullscreen()
  void ac?.close()
  setViewer(null)
})

/* ---------- toolbar button states */
const tb = 'h-8 px-3.5 max-xl:h-10 max-xl:px-4 text-xs font-normal transition-[filter] duration-150 hover:brightness-110'
const tbOn = 'border-[rgb(var(--ck-acc))] bg-[rgb(var(--ck-acc)/.16)] text-[#CFE6FF]'
</script>

<template>
  <section data-screen-label="01 全景图" class="flex-1 px-5 pt-3.5 pb-6">
    <div
      v-if="portrait && !portraitHidden"
      role="status"
      class="mb-3 flex items-center gap-3 rounded-lg border border-[rgba(255,184,77,.35)] bg-[rgba(255,184,77,.12)] px-3.5 py-2 text-xs text-[#FFD9A0]"
    >
      <span class="flex-1">竖屏下大屏被缩得很小,建议横屏查看</span>
      <button type="button" class="flex size-10 shrink-0 cursor-pointer items-center justify-center rounded-md text-base text-[#FFD9A0]" aria-label="关闭提示" @click="closePortrait">✕</button>
    </div>
    <div class="mb-3 flex flex-wrap items-center gap-x-3 gap-y-2.5">
      <span class="text-xs whitespace-nowrap text-[#6F84A6]">{{ ownId ? '当前视角' : '查看身份' }}</span>
      <div v-if="ownId" class="rounded-[10px] bg-[#0E1A2E] px-3.5 py-[5px] leading-[1.3] max-xl:flex max-xl:min-h-10 max-xl:flex-col max-xl:justify-center" data-testid="cockpit-identity">
        <div class="text-[13px] font-semibold whitespace-nowrap text-[#DDE6F3]">{{ I.tab }}</div>
        <div class="text-[10px] whitespace-nowrap text-[#6F84A6]">{{ I.orgShort }}</div>
      </div>
      <div v-else class="flex gap-0.5 rounded-[10px] bg-[#0E1A2E] p-[3px]">
        <div v-press
          v-for="t in idTabs"
          :key="t.i"
          :class="cn('cursor-pointer rounded-lg px-3.5 py-[5px] leading-[1.3] max-xl:flex max-xl:min-h-10 max-xl:flex-col max-xl:justify-center', t.on ? 'bg-white' : 'bg-transparent')"
          @click="pickIdentity(t.i)"
        >
          <div :class="cn('text-[13px] font-semibold whitespace-nowrap', t.on ? 'text-ink-1' : 'text-[#AEBBD0]')">{{ t.label }}</div>
          <div :class="cn('text-[10px] whitespace-nowrap', t.on ? 'text-ink-4' : 'text-[#6F84A6]')">{{ t.sub }}</div>
        </div>
      </div>
      <div class="flex-1" />
      <Button variant="dark" :class="cn(tb, 'gap-1.5', s.rot && tbOn)" @click="s.rot = !s.rot; s.rotT = 0">
        <span :class="cn('size-[7px] rounded-full', s.rot ? 'bg-[#3FD1A0]' : 'bg-[#6F84A6]')" />
        {{ s.rot ? `轮播中 · ${rotN - s.rotT}s` : '自动轮播' }}
      </Button>
      <Button variant="dark" :class="tb" @click="s.subOn = true">订阅推送</Button>
      <Button variant="dark" :class="cn(tb, !s.snd && 'text-[#6F84A6]')" @click="s.snd = !s.snd">{{ s.snd ? '声音 开' : '声音 关' }}</Button>
      <Button variant="dark" :class="cn(tb, s.dual && tbOn)" @click="toggleDual">{{ s.dual ? '双屏 3840 · 开' : '双屏拼接' }}</Button>
      <Button class="h-8 px-3.5 text-xs max-xl:h-10 max-xl:px-4" @click="goCast">▶ 投屏模式</Button>
      <Button variant="dark" class="h-8 px-3.5 text-xs font-normal hover:brightness-[.97] max-xl:h-10 max-xl:px-4" @click="toggleFs">⤢ 全屏</Button>
    </div>

    <div ref="box" class="relative w-full overflow-hidden rounded-[10px]" :style="boxStyle">
      <!-- main screen -->
      <div class="cockpit-screen" :style="screenStyle(fit.cx)">
        <div class="absolute inset-0" :style="{ background: glow }" />
        <div class="cockpit-grid absolute inset-0" />
        <CockpitTop />
        <template v-if="!noOwn">
          <CockpitCenter />
          <CockpitRight />
          <CockpitBottom />
          <CockpitLoop />
        </template>
        <!-- 登录机构不是示例数据所属医院:服务端已置空本院数据(HospitalCockpitScope · noOwnData) -->
        <div
          v-else
          class="absolute top-[84px] right-7 bottom-6 left-7 flex flex-col items-center justify-center gap-3 rounded-[10px] border border-dashed border-[rgb(var(--ck-line)/.3)] bg-[rgb(var(--ck-panel)/.4)] text-center"
          data-testid="cockpit-no-own-data"
        >
          <div class="text-[32px] font-semibold text-[#C9D6EA]">暂无本院数据</div>
          <div class="max-w-[900px] text-lg text-[#6F84A6]">{{ data.noOwnDataNote || '本机构的医保结算数据尚未归集,全景图暂不展示本院指标' }}</div>
        </div>
        <AlarmOverlay v-if="s.alOn" :busy="acking" @ack="ackAlarm" />
      </div>
      <!-- secondary screen (双屏拼接 3840×1080) -->
      <div
        v-if="s.dual"
        class="cockpit-screen border-l-2 border-[rgb(var(--ck-accl)/.18)]"
        :style="screenStyle(fit.cx + 1920 * fit.cs)"
      >
        <div class="absolute inset-0" :style="{ background: glow }" />
        <div class="cockpit-grid absolute inset-0" />
        <SecondScreen v-if="!noOwn" />
        <div v-else class="absolute inset-0 flex items-center justify-center text-[28px] text-[#6F84A6]">暂无本院数据</div>
      </div>
    </div>

    <SubscribeDialog :busy="savingSub" @save="saveSub" />
  </section>
</template>

<style>
.cockpit-screen {
  position: absolute;
  width: 1920px;
  height: 1080px;
  transform-origin: 0 0;
  color: #E6EEF9;
  overflow: hidden;
  font-family: 'Noto Sans SC', sans-serif;
}
/* 指标卡条底色(主屏 / 副屏):随大屏配色(--ck-*)变化,不写死蓝色 */
.ck-ribbon {
  background: linear-gradient(180deg, rgb(var(--ck-acc) / .10), rgb(var(--ck-panel) / .35)), rgb(var(--ck-panel) / .55);
}
.cockpit-grid {
  background-image: linear-gradient(rgb(var(--ck-line)/.03) 1px, transparent 1px), linear-gradient(90deg, rgb(var(--ck-line)/.03) 1px, transparent 1px);
  background-size: 40px 40px;
}
/* 2.5D 立方柱(钱 · 月度柱、副屏 · 受众查阅率):侧面压暗、顶面提亮 */
.cube-side {
  clip-path: polygon(0 4px, 100% 0, 100% calc(100% - 4px), 0 100%);
  filter: brightness(.55);
}
.cube-top {
  clip-path: polygon(0 100%, 5px 0, 100% 0, calc(100% - 5px) 100%);
  filter: brightness(1.45);
}
@keyframes ybAlarm { 0%, 100% { opacity: 1 } 50% { opacity: .2 } }
@keyframes ybRing { 0% { transform: scale(1); opacity: .8 } 100% { transform: scale(2.2); opacity: 0 } }
@keyframes cockScan { 0% { left: -14% } 100% { left: 100% } }
@keyframes cockPulse { 0% { transform: scale(1); opacity: .75 } 100% { transform: scale(2.4); opacity: 0 } }
@keyframes cockDash { to { stroke-dashoffset: -36 } }
</style>
