<script setup lang="ts">
/**
 * Page title row: 24px title + grey subtitle on the left, actions on the right.
 * Slots: `title` (title plus inline badges), `subtitle`, default (actions).
 * `align`: vertical alignment of the actions against the title block — `end` (default, bottom edge),
 * `center`, or `start` (top edge; use for progress blocks / tag groups that should not hug the subtitle).
 */
withDefaults(defineProps<{ title?: string; subtitle?: string; align?: 'end' | 'center' | 'start' }>(), { align: 'end' })
const ALIGN = { end: 'items-end', center: 'items-center', start: 'items-start' } as const
</script>

<template>
  <div :class="['flex justify-between gap-x-5 gap-y-3 max-xl:flex-wrap', ALIGN[align]]">
    <!-- < 1280:标题块按内容宽度参与换行——标题 + 副标题与操作区放得下就并排,放不下时操作区整体落到标题下方,副标题不被挤成多行 / 孤字 -->
    <div class="min-w-0 flex-1 max-xl:flex-auto">
      <div class="flex min-w-0 flex-wrap items-center gap-x-2.5 gap-y-1 text-2xl font-semibold">
        <slot name="title"><span class="tracking-[-0.2px]">{{ title }}</span></slot>
      </div>
      <div v-if="subtitle || $slots.subtitle" class="mt-0.5 text-[13px] text-pretty text-ink-4">
        <slot name="subtitle">{{ subtitle }}</slot>
      </div>
    </div>
    <div v-if="$slots.default" class="flex shrink-0 items-center gap-2 max-xl:max-w-full max-xl:min-w-0 max-xl:flex-wrap">
      <slot />
    </div>
  </div>
</template>
