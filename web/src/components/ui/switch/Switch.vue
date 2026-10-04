<script setup lang="ts">
import type { SwitchRootEmits, SwitchRootProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import type { SwitchVariants } from "."
import { reactiveOmit } from "@vueuse/core"
import {
  SwitchRoot,
  SwitchThumb,
  useForwardPropsEmits,
} from "reka-ui"
import { cn } from "@/lib/utils"
import { switchThumbVariants, switchVariants } from "."

/** `size="lg"` = the prototype's 38×22 toggle with an 18px thumb. */
const props = defineProps<SwitchRootProps & { class?: HTMLAttributes["class"], size?: SwitchVariants["size"] }>()

const emits = defineEmits<SwitchRootEmits>()

const delegatedProps = reactiveOmit(props, "class", "size")

const forwarded = useForwardPropsEmits(delegatedProps, emits)
</script>

<template>
  <SwitchRoot
    v-slot="slotProps"
    data-slot="switch"
    v-bind="forwarded"
    :class="cn(switchVariants({ size }), props.class)"
  >
    <SwitchThumb
      data-slot="switch-thumb"
      :class="switchThumbVariants({ size })"
    >
      <slot name="thumb" v-bind="slotProps" />
    </SwitchThumb>
  </SwitchRoot>
</template>
