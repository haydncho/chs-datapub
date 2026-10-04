import type { VariantProps } from "class-variance-authority"
import { cva } from "class-variance-authority"

export { default as Button } from "./Button.vue"

/**
 * 按钮变体对齐原型：
 * - default  ≙ .btn-solid（实心主色）
 * - outline  ≙ .btn（白底描边）
 * - pill / pillTint ≙ 工作台分解面板里的药丸按钮（实心蓝 / 浅蓝底描边）
 * - ai       ≙ AI 紫色实心按钮
 */
export const buttonVariants = cva(
  "inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-lg text-[13px] leading-[1.6] tracking-normal cursor-pointer transition-colors disabled:pointer-events-none disabled:opacity-45 disabled:cursor-default [&_svg]:pointer-events-none [&_svg]:shrink-0 shrink-0 outline-none focus-visible:ring-2 focus-visible:ring-ring/60",
  {
    variants: {
      variant: {
        default:
          "border border-primary bg-primary-solid text-white font-semibold hover:bg-primary-solid-hover hover:border-primary-solid-hover",
        outline:
          "border border-line bg-surface text-ink-sub hover:bg-hover hover:border-line-strong",
        pill:
          "rounded-full border border-primary bg-primary-solid text-white font-semibold hover:bg-primary-solid-hover hover:border-primary-solid-hover",
        pillTint:
          "rounded-full border border-primary-line-strong bg-primary-tint text-primary hover:bg-primary-tint-hover",
        ai:
          "rounded-lg border border-ai bg-ai-solid text-white font-semibold hover:bg-ai-solid-hover",
        ghost: "text-ink-sub hover:bg-hover",
        link: "text-primary underline-offset-4 hover:underline",
        destructive: "border border-danger bg-danger-solid text-white hover:bg-danger-solid-hover",
      },
      size: {
        default: "px-4 py-2",
        sm: "px-3 py-[5px] text-[12px]",
        xs: "px-3 py-1 text-[11px]",
        pill: "px-3.5 py-[7px]",
        icon: "size-[30px] rounded-full p-0",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  },
)
export type ButtonVariants = VariantProps<typeof buttonVariants>
