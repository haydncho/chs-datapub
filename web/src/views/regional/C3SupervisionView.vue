<script setup lang="ts">
import { useEventListener, useIntervalFn } from '@vueuse/core'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { HttpError } from '@/api/http'
import { regionalApi, SEAT_EXPIRED, type MaterialPreview, type Recording, type Seat } from '@/api/regional'
import PageHeader from '@/components/shared/PageHeader.vue'
import { pageDef } from '@/lib/nav'
import { notify, notifyError } from '@/lib/notify'
import { useAuthStore } from '@/stores/auth'

/**
 * C3 外部监督只读席位：剩余有效期倒计时（每秒刷新，到期自动失效）；左材料列表 + 发布会录像限时回看；
 * 右 16:10 只读预览（叠加实名水印）。不提供下载、打印、导出：页面禁用右键、复制与打印快捷键，打印输出隐藏全部内容。
 * 有效期以服务端为准：剩余秒数由服务端给出，到期后接口一律 403（SEAT_EXPIRED）。
 */
const page = pageDef('C3')!
const auth = useAuthStore()

const seat = ref<Seat | null>(null)
const preview = ref<MaterialPreview | null>(null)
const matId = ref<number | null>(null)
const rec = ref<Recording | null>(null)
const expired = ref<string | null>(null)
const blocked = ref<string | null>(null)

// ---------------------------------------------------------------- 倒计时（以服务端剩余秒数为基准，避免终端时钟偏差）
let deadline = 0
const now = ref(Date.now())
const remainMs = computed(() => Math.max(0, deadline - now.value))
const pad = (n: number) => String(n).padStart(2, '0')
const countdown = computed(() => {
  let ms = remainMs.value
  const d = Math.floor(ms / 864e5)
  ms %= 864e5
  const h = Math.floor(ms / 36e5)
  ms %= 36e5
  const m = Math.floor(ms / 6e4)
  const s = Math.floor((ms % 6e4) / 1000)
  return `${d ? `${d} 天 ` : ''}${pad(h)}:${pad(m)}:${pad(s)}`
})

function onError(e: unknown) {
  if (e instanceof HttpError && e.status === 403) {
    if (e.code === SEAT_EXPIRED) expire(e.message)
    else blocked.value = e.message
    return
  }
  notifyError(e)
}

function expire(msg: string) {
  expired.value = msg
  preview.value = null
  playing.value = false
}

useIntervalFn(() => {
  now.value = Date.now()
  if (seat.value && !expired.value && remainMs.value <= 0) {
    expire(`只读席位已于 ${seat.value.validUntil} 到期自动失效,不能继续查阅`)
    // 向服务端确认（到期后接口返回 403 SEAT_EXPIRED，以服务端提示为准）
    setTimeout(() => regionalApi.seat().then(() => {}, onError), 1500)
  }
}, 1000)

async function loadSeat() {
  try {
    const s = await regionalApi.seat()
    deadline = Date.now() + s.remainingSeconds * 1000
    now.value = Date.now()
    seat.value = s
    rec.value = s.recording
    if (s.remainingSeconds <= 0) return expire(`只读席位已于 ${s.validUntil} 到期自动失效,不能继续查阅`)
    if (s.materials.length) await open(s.materials[0].id)
  } catch (e) {
    onError(e)
  }
}

async function open(id: number) {
  if (expired.value) return
  matId.value = id
  try {
    preview.value = await regionalApi.material(id)
  } catch (e) {
    onError(e)
  }
}

// ---------------------------------------------------------------- 录像限时回看（仅在线播放，不提供下载）
const playing = ref(false)
const fmtDur = (s: number) => {
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  return h ? `${h}:${pad(m)}:${pad(s % 60)}` : `${pad(m)}:${pad(s % 60)}`
}
const recPct = computed(() => (rec.value ? (rec.value.watchedS / rec.value.durationS) * 100 : 0))
let lastSaved = 0
async function saveProgress() {
  if (!rec.value || expired.value || rec.value.watchedS === lastSaved) return
  lastSaved = rec.value.watchedS
  try {
    await regionalApi.progress(rec.value.watchedS)
  } catch (e) {
    onError(e)
  }
}
useIntervalFn(() => {
  if (!playing.value || !rec.value) return
  rec.value.watchedS = Math.min(rec.value.durationS, rec.value.watchedS + 1)
  if (rec.value.watchedS >= rec.value.durationS) playing.value = false
  if (rec.value.watchedS % 15 === 0 || !playing.value) void saveProgress()
}, 1000)
function togglePlay() {
  if (expired.value || !rec.value) return
  playing.value = !playing.value
  if (!playing.value) void saveProgress()
}

// ---------------------------------------------------------------- 只读：禁用右键 / 复制 / 打印 / 另存
const deny = (msg: string) => (e: Event) => {
  e.preventDefault()
  notify(msg)
}
useEventListener(document, 'contextmenu', deny('只读席位不提供右键菜单'))
useEventListener(document, 'copy', deny('只读席位不提供复制'))
useEventListener(document, 'keydown', (e: KeyboardEvent) => {
  const k = e.key.toLowerCase()
  if ((e.ctrlKey || e.metaKey) && (k === 'p' || k === 's')) deny(k === 'p' ? '只读席位不提供打印' : '只读席位不提供下载')(e)
})
onMounted(() => {
  document.documentElement.classList.add('c3-no-print')
  void loadSeat()
})
onBeforeUnmount(() => {
  document.documentElement.classList.remove('c3-no-print')
  void saveProgress()
})

const wmText = computed(() => {
  const d = new Date(now.value)
  return `${auth.user?.name ?? ''} 外部监督 只读 ${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
})
const kpiCls = { default: 'text-ink', success: 'text-success', warning: 'text-warning', danger: 'text-danger' } as const
</script>

<template>
  <div class="px-10 pt-[22px] pb-9 select-none" data-testid="c3-root">
    <PageHeader :page="page" />

    <!-- 有效期横幅 -->
    <div
      v-if="!expired"
      class="mt-5 flex flex-wrap items-center gap-4 rounded-[10px] border border-warning-line bg-warning-soft px-[18px] py-3 text-warning-ink"
      data-testid="c3-banner"
    >
      <span class="text-[13px] font-semibold">限时只读席位</span>
      <span class="text-[12px]">
        剩余有效期
        <b class="font-mono text-[14px] tabular-nums" data-testid="c3-countdown">{{ seat ? countdown : '—' }}</b>
        · 到期自动失效<template v-if="seat">(截止 {{ seat.validUntil }})</template>
      </span>
      <span class="ml-auto text-[12px] text-warning">不提供下载、打印、导出</span>
    </div>
    <div
      v-else
      class="mt-5 flex flex-wrap items-center gap-4 rounded-[10px] border border-danger-line bg-danger-soft px-[18px] py-3 text-danger-ink"
      data-testid="c3-expired"
    >
      <span class="text-[13px] font-semibold">席位已失效</span>
      <span class="text-[12px]">{{ expired }}</span>
      <span class="ml-auto text-[12px]">如需继续查阅,请联系市医保局重新开通</span>
    </div>

    <div v-if="blocked" class="mt-3.5 rounded-[10px] border border-line bg-surface px-[18px] py-6 text-center text-[12px] text-ink-muted">{{ blocked }}</div>

    <div v-else-if="seat" class="mt-3.5 grid grid-cols-[300px_minmax(0,1fr)] items-start gap-3.5">
      <div class="flex flex-col gap-3.5">
        <section class="rounded-[10px] border border-line bg-surface p-2.5" data-testid="c3-materials">
          <div class="px-1.5 pt-0.5 pb-2 text-[13px] font-semibold text-ink">发布会材料</div>
          <button
            v-for="m in seat.materials"
            :key="m.id"
            type="button"
            class="mb-1 block w-full cursor-pointer rounded-lg border px-2.5 py-2 text-left transition-colors disabled:cursor-not-allowed disabled:opacity-50"
            :class="m.id === matId && !expired ? 'border-primary bg-primary-tint' : 'border-transparent hover:bg-hover'"
            :disabled="!!expired"
            :data-material="m.id"
            :aria-pressed="m.id === matId"
            @click="open(m.id)"
          >
            <div class="text-[12px] font-medium text-ink">{{ m.name }}</div>
            <div class="text-[11px] text-ink-muted">{{ m.format }}</div>
          </button>
        </section>

        <section v-if="rec" class="rounded-[10px] border border-line bg-surface px-3.5 py-3" data-testid="c3-recording">
          <div class="mb-2 text-[13px] font-semibold text-ink">限时回看 · 发布会录像</div>
          <button
            type="button"
            class="flex h-[150px] w-full cursor-pointer items-center justify-center rounded-lg bg-code text-[12px] text-ink-ghost disabled:cursor-not-allowed"
            :disabled="!!expired"
            data-testid="c3-play"
            @click="togglePlay"
          >
            <template v-if="expired">录像回看已失效</template>
            <template v-else-if="playing">❚❚ 正在回看 · {{ fmtDur(rec.watchedS) }} / {{ fmtDur(rec.durationS) }}</template>
            <template v-else>▶ {{ rec.title }} · {{ fmtDur(rec.durationS) }}</template>
          </button>
          <div class="mt-2 h-1 rounded-sm bg-line">
            <div class="h-1 rounded-sm bg-primary-solid" :style="{ width: `${recPct}%` }" />
          </div>
          <div class="mt-1 text-[11px] text-ink-muted">已观看 {{ fmtDur(rec.watchedS) }} · 不可下载</div>
        </section>
      </div>

      <!-- 16:10 只读预览（公文纸 + 实名水印） -->
      <div class="flex justify-center rounded-[10px] bg-line-soft p-5" @copy.prevent @dragstart.prevent>
        <div
          data-paper
          class="relative aspect-[16/10] w-full max-w-[720px] overflow-hidden bg-white px-12 py-10 shadow-[0_2px_8px_rgba(0,0,0,.08)]"
          data-testid="c3-preview"
        >
          <template v-if="preview && !expired">
            <div class="text-[11px] text-ink-faint">{{ preview.name }} · 只读预览</div>
            <div class="mt-6 text-[24px] leading-[1.35] font-semibold text-ink" data-testid="c3-heading">{{ preview.heading }}</div>
            <div class="my-4 h-[2px] w-20 bg-primary-solid" />
            <div v-if="preview.kpis.length" class="mt-5 grid grid-cols-3 gap-4">
              <div v-for="k in preview.kpis" :key="k.label">
                <div class="text-[12px] text-ink-muted">{{ k.label }}</div>
                <div class="text-[22px] font-semibold" :class="kpiCls[k.tone] ?? 'text-ink'">{{ k.value }}</div>
              </div>
            </div>
            <div :class="preview.kpis.length ? 'mt-6' : 'mt-2'" class="flex flex-col gap-2 text-[13px] leading-[1.8] text-ink-body">
              <p v-for="(p, i) in preview.body" :key="i">{{ p }}</p>
            </div>
          </template>
          <div v-else-if="expired" class="flex h-full flex-col items-center justify-center gap-1.5 text-center">
            <div class="text-[15px] font-semibold text-ink">席位已失效</div>
            <div class="text-[12px] text-ink-muted">有效期已过,材料不再提供预览</div>
          </div>
          <div v-else class="flex h-full items-center justify-center text-[12px] text-ink-faint">加载中…</div>

          <!-- 预览区叠加水印（不拦截点击） -->
          <div class="pointer-events-none absolute inset-0 grid auto-rows-[110px] grid-cols-3 opacity-[0.12]" aria-hidden="true" data-testid="c3-paper-wm">
            <div v-for="i in 18" :key="i" class="flex items-center justify-center">
              <span class="-rotate-[28deg] text-[13px] whitespace-nowrap text-ink">{{ wmText }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-else-if="!expired" class="mt-10 text-center text-[12px] text-ink-faint">加载中…</div>
  </div>
</template>

<style>
/* 只读席位不提供打印：本页挂载期间打印输出隐藏全部内容，仅留一行提示 */
@media print {
  html.c3-no-print body * {
    display: none !important;
  }
  html.c3-no-print body::before {
    content: '外部监督只读席位不提供打印';
    display: block;
    padding: 48px 0;
    text-align: center;
    font-size: 14px;
  }
}
</style>
