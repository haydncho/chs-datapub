<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { indicatorApi, type IndMeta } from '@/api/indicator'
import IndicatorDrawer from '@/components/indicator/IndicatorDrawer.vue'
import IndicatorList from '@/components/indicator/IndicatorList.vue'
import IndicatorWizard from '@/components/indicator/IndicatorWizard.vue'
import PageHeader from '@/components/shared/PageHeader.vue'
import SegTabs from '@/components/shared/SegTabs.vue'
import SandboxPanel from '@/components/indicator/SandboxPanel.vue'
import { pageDef } from '@/lib/nav'
import { notifyError } from '@/lib/notify'

/**
 * A4 指标可视化配置（召集人 / 行政管理组）：指标列表 + 右侧指标卡抽屉；「新建指标」进入四步向导（草稿自动保存，提交生成上线审批单）。
 */
const page = pageDef('A4')!
const mode = ref<'list' | 'wizard'>('list')
const view = ref<'list' | 'sandbox'>('list')
const drawerId = ref<number | null>(null)
const meta = ref<IndMeta | null>(null)

onMounted(async () => {
  try {
    meta.value = await indicatorApi.meta()
  } catch (e) {
    notifyError(e)
  }
})

function create() {
  if (!meta.value) return
  drawerId.value = null
  mode.value = 'wizard'
  window.scrollTo(0, 0)
}
</script>

<template>
  <div class="px-10 pt-[22px] pb-9">
    <PageHeader :page="page" :title="mode === 'wizard' ? '指标可视化配置 · 新建指标' : undefined" />
    <div v-if="mode === 'list'" class="mt-5">
      <SegTabs v-model="view" :items="[{ value: 'list', label: '指标列表' }, { value: 'sandbox', label: '算法沙盘' }]" data-testid="a4-views" />
    </div>
    <div class="mt-4">
      <SandboxPanel v-if="mode === 'list' && view === 'sandbox'" />
      <IndicatorList v-else-if="mode === 'list'" @open="drawerId = $event" @create="create" />
      <IndicatorWizard v-else-if="meta" :meta="meta" @back="mode = 'list'" />
    </div>
    <IndicatorDrawer :id="drawerId" @close="drawerId = null" />
  </div>
</template>
