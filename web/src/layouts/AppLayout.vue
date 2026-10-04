<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '@/components/shell/AppHeader.vue'
import PageWatermark from '@/components/shell/PageWatermark.vue'
import SecurityBar from '@/components/shell/SecurityBar.vue'
import SideNav from '@/components/shell/SideNav.vue'
import { TooltipProvider } from '@/components/ui/tooltip'
import { INTERNAL_ROLES, pageDef } from '@/lib/nav'
import { useAuthStore } from '@/stores/auth'

/** 应用外壳：安全提示条（常驻）+ 液态玻璃顶栏 + 分组侧栏 + 内容区 + 实名动态水印。 */
const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const page = computed(() => pageDef((route.meta.page as string) ?? ''))
/** 区标签只对医保局内部角色显示：机构、县区、省级、监督席位看不到内部架构。 */
const zone = computed(() => (INTERNAL_ROLES.includes(auth.user?.role ?? '') ? page.value?.zone : undefined))

const headerEl = ref<HTMLElement | null>(null)
const top = ref(0)
onMounted(() => {
  const ro = new ResizeObserver(() => (top.value = headerEl.value?.offsetHeight ?? 0))
  if (headerEl.value) ro.observe(headerEl.value)
})

async function logout() {
  await auth.logout()
  void router.push({ name: 'login' })
}
</script>

<template>
  <TooltipProvider :delay-duration="0" :skip-delay-duration="0">
    <div class="relative flex min-h-screen min-w-[1280px] flex-col bg-app" :style="{ '--shell-top': `${top}px` }">
      <div ref="headerEl" class="sticky top-0 z-30">
        <SecurityBar />
        <AppHeader :zone="zone" @logout="logout" />
      </div>
      <div class="flex min-h-0 flex-1">
        <SideNav />
        <main class="flex min-w-0 flex-1 flex-col">
          <RouterView v-if="auth.user" :key="route.fullPath" />
        </main>
      </div>
    </div>
    <PageWatermark />
  </TooltipProvider>
</template>
