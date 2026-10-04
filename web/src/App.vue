<script setup lang="ts">
import { Toaster } from '@/components/ui/sonner'
import 'vue-sonner/style.css'
</script>

<template>
  <RouterView />
  <!-- 深色提示条：12px、居中贴底 -->
  <Toaster
    position="bottom-center"
    :offset="32"
    :visible-toasts="1"
    :toast-options="{
      style: {
        background: 'var(--c-tip-bg)',
        color: 'var(--c-toast-fg)',
        border: 'none',
        borderRadius: '8px',
        fontSize: '12px',
        letterSpacing: '.04em',
        padding: '11px 22px',
        width: 'auto',
        boxShadow: '0 8px 30px rgba(20,29,38,.4)',
        whiteSpace: 'nowrap',
        left: '50%',
        translate: '-50% 0',
      },
    }"
  />
</template>
