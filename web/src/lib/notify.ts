import { toast } from 'vue-sonner'
import { HttpError } from '@/api/http'

/** 底部深色提示条（2.4s）。 */
export function notify(message: string | null | undefined) {
  if (message) toast(message, { duration: 2400 })
}

/** 接口错误统一提示：服务端 {code, message} 原样给出。 */
export function notifyError(err: unknown) {
  toast(err instanceof HttpError ? err.message : '操作失败，请稍后重试', { duration: 3200 })
}
