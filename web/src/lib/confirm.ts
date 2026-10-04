import { reactive } from 'vue'

/** 二次确认：`if (!(await confirm({ title, body }))) return`。宿主组件 ConfirmHost 挂在应用外壳。 */
export interface ConfirmOptions {
  title: string
  body?: string
  okText?: string
  /** 危险操作（红色确认按钮） */
  danger?: boolean
}

export const confirmState = reactive<{ open: boolean; opts: ConfirmOptions; resolve: ((v: boolean) => void) | null }>({
  open: false,
  opts: { title: '' },
  resolve: null,
})

export function confirm(opts: ConfirmOptions): Promise<boolean> {
  confirmState.resolve?.(false)
  confirmState.opts = opts
  confirmState.open = true
  return new Promise((res) => (confirmState.resolve = res))
}

export function settleConfirm(v: boolean) {
  confirmState.open = false
  confirmState.resolve?.(v)
  confirmState.resolve = null
}
