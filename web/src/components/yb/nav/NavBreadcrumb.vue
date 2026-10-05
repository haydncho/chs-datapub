<script setup lang="ts">
import { ChevronRight } from '@lucide/vue'

/** 面包屑:端 / 分组 / 页面。 */
defineProps<{ crumb: { side: string; group: string; item: string } }>()
</script>

<template>
  <nav aria-label="面包屑" class="yb-crumb">
    <ol>
      <li>{{ crumb.side }}</li>
      <template v-if="crumb.group && crumb.group !== crumb.item">
        <li aria-hidden="true"><ChevronRight class="size-3" /></li>
        <li>{{ crumb.group }}</li>
      </template>
      <li v-if="crumb.item" aria-hidden="true"><ChevronRight class="size-3" /></li>
      <li v-if="crumb.item" aria-current="page" class="yb-crumb-cur">{{ crumb.item }}</li>
    </ol>
  </nav>
</template>
