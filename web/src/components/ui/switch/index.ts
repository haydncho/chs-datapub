import type { VariantProps } from "class-variance-authority"
import { cva } from "class-variance-authority"

export { default as Switch } from "./Switch.vue"

export const switchVariants = cva(
  "peer inline-flex shrink-0 cursor-pointer items-center rounded-full border border-transparent transition-all outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50",
  {
    variants: {
      size: {
        // shadcn default: 32×18 track
        default: "h-[1.15rem] w-8 shadow-xs data-[state=checked]:bg-primary data-[state=unchecked]:bg-input",
        // prototype toggle (A4 wizard / A10 / A13): 38×22 track, 18px thumb, grey-blue off state
        lg: "h-[22px] w-[38px] border-0 px-0.5 data-[state=checked]:bg-brand data-[state=unchecked]:bg-ink-6",
      },
    },
    defaultVariants: { size: "default" },
  },
)

export const switchThumbVariants = cva(
  "pointer-events-none block rounded-full bg-white ring-0 transition-transform data-[state=unchecked]:translate-x-0",
  {
    variants: {
      size: {
        default: "size-4 data-[state=checked]:translate-x-[calc(100%-2px)]",
        lg: "size-[18px] shadow-[0_1px_2px_rgba(0,0,0,.2)] data-[state=checked]:translate-x-4",
      },
    },
    defaultVariants: { size: "default" },
  },
)

export type SwitchVariants = VariantProps<typeof switchVariants>
