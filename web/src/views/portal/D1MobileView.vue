<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { portalReportsApi, type MobileAlert, type MobileSummary } from '@/api/portalReports'
import MobileWatermark from '@/components/portal-reports/MobileWatermark.vue'
import { notify, notifyError } from '@/lib/notify'

/**
 * D1 移动端摘要（政务 APP 内嵌，390 宽独立布局，不在 AppLayout 内）。
 * 顶栏无分享 / 复制链接 / 二维码入口；常驻安全提示条；满屏实名水印；所有点击目标 ≥ 44px。
 * 仅摘要与签收（与 B4 同一数据）；意见、核对、回执、导出在 PC 端。
 */
const sum = ref<MobileSummary | null>(null)
const alert = ref<MobileAlert | null>(null)
const view = ref<'home' | 'alert'>('home')
const busy = ref(false)

onMounted(async () => {
  try {
    sum.value = await portalReportsApi.mobileSummary()
  } catch (e) {
    notifyError(e)
  }
})

async function sign() {
  const p = sum.value?.pending
  if (!p || busy.value) return
  busy.value = true
  try {
    await portalReportsApi.sign(p.id)
    notify(`已签收 ${p.title}`)
    sum.value = await portalReportsApi.mobileSummary()
  } catch (e) {
    notifyError(e)
  } finally {
    busy.value = false
  }
}

async function openAlert(id: number) {
  try {
    alert.value = await portalReportsApi.mobileAlert(id)
    view.value = 'alert'
    window.scrollTo(0, 0)
  } catch (e) {
    notifyError(e)
  }
}

const back = () => (view.value = 'home')
const diffText = (n: number) => (n > 0 ? '+' : n < 0 ? '−' : '') + Math.abs(n).toLocaleString('zh-CN')
const kpiTone = (t?: string) => (t === 'danger' ? 'text-danger' : t === 'success' ? 'text-success' : 'text-ink')
/** 分位条：P25–P75 浅带；本院标记 ≥ 关注线用橙。 */
const mark = computed(() => alert.value?.percentile ?? 0)
const attention = computed(() => mark.value >= (alert.value?.line ?? 70))
</script>

<template>
  <div class="min-h-screen bg-body">
    <div class="relative mx-auto flex min-h-screen w-full max-w-[390px] flex-col bg-app text-[13px] text-ink" data-testid="mobile">
      <!-- 顶栏：无分享入口 -->
      <header class="sticky top-0 z-20 border-b border-primary-line bg-surface" style="background-image: linear-gradient(135deg, color-mix(in srgb, var(--c-primary) 14%, var(--c-surface)), var(--c-surface) 70%)">
        <div class="flex h-12 items-center gap-1.5 px-2">
          <button
            v-if="view === 'alert'"
            type="button"
            class="flex size-11 flex-none cursor-pointer items-center justify-center text-[24px] leading-none text-primary"
            aria-label="返回"
            data-testid="m-back"
            @click="back"
          >‹</button>
          <span class="flex-1 text-[16px] font-semibold" :class="view === 'alert' ? '' : 'pl-2'">医保数据 · 本院摘要</span>
          <span class="mr-2 rounded-[3px] border border-primary px-1.5 text-[10px] text-primary">专网</span>
        </div>
        <div class="bg-warning-soft px-4 py-1.5 text-[11px] text-warning-ink" data-testid="m-security">仅限内部工作使用,禁止截图外传</div>
      </header>

      <main v-if="sum" class="flex flex-1 flex-col gap-2.5 p-3">
        <template v-if="view === 'home'">
          <!-- 待签收（与 B4 同一数据） -->
          <div v-if="sum.pending" class="flex items-center gap-2.5 rounded-2xl border border-line-soft bg-surface shadow-card px-3.5 py-3" data-testid="m-pending">
            <div class="min-w-0 flex-1">
              <div class="text-[11px] text-warning-ink">待签收</div>
              <div class="font-semibold">{{ sum.pending.title }}</div>
            </div>
            <button
              type="button"
              class="h-11 flex-none cursor-pointer rounded-lg bg-primary-solid px-[18px] text-[14px] font-semibold text-white disabled:opacity-60"
              :disabled="busy"
              data-testid="m-sign"
              @click="sign"
            >签收</button>
          </div>
          <div v-else-if="sum.lastSigned" class="rounded-[10px] bg-success-soft px-3.5 py-3 text-success-ink" data-testid="m-signed">✓ {{ sum.lastSigned.title }} 已签收</div>

          <!-- 预警卡 → 详情 -->
          <button
            v-for="a in sum.alerts"
            :key="a.id"
            type="button"
            class="flex min-h-11 w-full cursor-pointer items-center gap-2.5 rounded-2xl border border-line-soft bg-surface shadow-card px-3.5 py-3 text-left"
            :data-alert="a.id"
            @click="openAlert(a.id)"
          >
            <span class="size-2 flex-none rounded-full bg-danger" />
            <div class="min-w-0 flex-1">
              <div class="font-semibold">预警:{{ a.rule }}</div>
              <div class="text-[12px] text-ink-muted">{{ a.summary }}</div>
            </div>
            <span class="text-[18px] text-ink-ghost">›</span>
          </button>

          <!-- 本院 4 KPI -->
          <div v-if="sum.kpis" class="rounded-2xl border border-line-soft bg-surface shadow-card p-3.5" data-testid="m-kpis">
            <div class="mb-2.5 font-semibold">本院 {{ sum.period }}</div>
            <div class="grid grid-cols-2 gap-2.5">
              <div v-for="k in sum.kpis" :key="k.label" class="rounded-xl border border-line-soft px-3 py-2.5" :style="{ backgroundImage: 'linear-gradient(135deg, color-mix(in srgb, ' + (k.tone === 'danger' ? 'var(--c-danger)' : 'var(--c-primary)') + ' 10%, var(--c-surface)), var(--c-surface) 75%)' }">
                <div class="text-[11px] text-ink-muted">{{ k.label }}</div>
                <div class="text-[18px] leading-[1.4] font-semibold" :class="kpiTone(k.tone)">{{ k.value }}</div>
                <div v-if="k.sub" class="text-[11px] text-ink-muted">{{ k.sub }}</div>
              </div>
            </div>
          </div>

          <!-- 重点病组 -->
          <div v-if="sum.groups" class="rounded-2xl border border-line-soft bg-surface shadow-card p-3.5" data-testid="m-groups">
            <div class="mb-2 font-semibold">重点病组 <span class="text-[11px] font-normal text-ink-muted">· 例均基金差额(元)</span></div>
            <div class="grid grid-cols-[48px_1fr_72px] gap-y-2 text-[12px]">
              <template v-for="g in sum.groups" :key="g.code">
                <span class="text-primary">{{ g.code }}</span>
                <span>{{ g.name }}</span>
                <span class="text-right" :class="g.diff > 0 ? 'text-danger' : 'text-success'">{{ diffText(g.diff) }}</span>
              </template>
            </div>
          </div>

          <!-- 核对引导 -->
          <div v-if="sum.check" class="flex min-h-11 items-center justify-between gap-2 rounded-2xl border border-line-soft bg-surface shadow-card px-3.5 py-3" data-testid="m-check">
            <span>{{ sum.check.title.replace('(核对稿)', '') }}核对</span>
            <span class="text-[12px] text-ai">{{ sum.check.closed ? '核对期已截止' : `请在 PC 端完成 · 截止 ${sum.check.deadlineLabel}` }}</span>
          </div>
        </template>

        <!-- 预警详情 -->
        <template v-else-if="alert">
          <div class="rounded-2xl border border-line-soft bg-surface shadow-card p-3.5" data-testid="m-alert">
            <span class="rounded-[3px] bg-danger-soft px-1.5 py-px text-[11px] text-danger-ink">预警</span>
            <div class="mt-1.5 text-[16px] font-semibold">{{ alert.title }}</div>
            <div class="text-[12px] text-ink-muted">{{ alert.period }} · 示例市医保数据工作组</div>
            <div class="mt-3 text-[28px] leading-[1.3] font-semibold">{{ alert.value }}</div>
            <template v-if="alert.percentile != null">
              <div class="relative mt-2 h-2.5 rounded-[3px] bg-divider">
                <div class="absolute inset-y-0 left-1/4 w-1/2 bg-primary-soft" />
                <div class="absolute top-[-4px] h-[18px] w-1 rounded-sm" :class="attention ? 'bg-warning' : 'bg-primary'" :style="{ left: `calc(${mark}% - 2px)` }" />
              </div>
              <div class="mt-1.5 text-[12px] text-ink-sub">同级 P{{ alert.percentile }} · 关注线 P{{ alert.line }}</div>
            </template>
          </div>
          <div v-if="alert.narrative" class="rounded-2xl border border-line-soft bg-surface shadow-card p-3.5 text-[12px] leading-[1.8] text-ink-body">{{ alert.narrative }}</div>
          <div class="rounded-2xl border border-line-soft bg-surface shadow-card px-3.5 py-3 text-[12px] text-ink-muted">提醒函 {{ alert.letterNo }} · 涉及 {{ alert.group }}</div>
          <div class="text-center text-[11px] text-ink-faint">移动端仅查看,回执与意见请在 PC 端处理</div>
        </template>
      </main>
    </div>
    <MobileWatermark />
  </div>
</template>
