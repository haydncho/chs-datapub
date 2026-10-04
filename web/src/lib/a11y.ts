import type { Directive } from 'vue'

/**
 * `v-press` — makes a clickable non-button element (a selectable row, card or tile
 * whose layout must stay a `div`) keyboard- and screen-reader-operable:
 * role="button", tabindex="0", and Enter / Space trigger its click handler.
 * `v-press="false"` turns it off (e.g. a disabled row).
 * Prefer a real `<button type="button">` for small chips and links.
 */
function onKey(e: KeyboardEvent) {
  if (e.key !== 'Enter' && e.key !== ' ') return
  if (e.target !== e.currentTarget) return
  e.preventDefault()
  ;(e.currentTarget as HTMLElement).click()
}

function apply(el: HTMLElement, on: boolean) {
  if (on) {
    if (!el.hasAttribute('role')) el.setAttribute('role', 'button')
    el.tabIndex = 0
    el.addEventListener('keydown', onKey)
  } else {
    el.removeAttribute('role')
    el.removeAttribute('tabindex')
    el.removeEventListener('keydown', onKey)
  }
}

export const vPress: Directive<HTMLElement, boolean | undefined> = {
  mounted: (el, b) => apply(el, b.value !== false),
  updated: (el, b) => {
    if (b.value !== b.oldValue) apply(el, b.value !== false)
  },
  unmounted: el => el.removeEventListener('keydown', onKey),
}
