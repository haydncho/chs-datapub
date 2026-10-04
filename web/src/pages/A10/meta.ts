import type { FeedbackStatus, FeedbackType } from '@/mock/A10'
import { AS, AT, RS, RT } from '@/lib/palette'

type BadgeTone = 'warn' | 'brand' | 'violet' | 'bad' | 'ok'

/** status → label + Badge variant */
export const FBS: Record<FeedbackStatus, { label: string; variant: BadgeTone }> = {
  todo: { label: '待分派', variant: 'warn' },
  doing: { label: '处理中', variant: 'brand' },
  reply: { label: '待答复', variant: 'violet' },
  over: { label: '已超期', variant: 'bad' },
  done: { label: '已答复', variant: 'ok' },
}

/** type tag colours [bg, fg] */
export const FBT: Record<FeedbackType, [string, string]> = {
  申诉: [RS, RT],
  意见: ['var(--brand-soft)', 'var(--brand)'],
  纠错: [AS, AT],
}
