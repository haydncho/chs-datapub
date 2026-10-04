import { reactive } from 'vue'
import { toast } from 'vue-sonner'
import type { Viewer } from './nav'

/**
 * Per-page shell state. Pages that change identity at runtime
 * (全息图 身份切换) call `setViewer`; everything else uses VIEWERS.
 */
export const shell = reactive<{ viewerOverride: Viewer | null }>({ viewerOverride: null })

export function setViewer(v: Viewer | null) {
  shell.viewerOverride = v
}

/** Dark toast at the bottom centre, as in the prototypes (2.4s). */
export function say(message: string) {
  toast(message, { duration: 2400 })
}
