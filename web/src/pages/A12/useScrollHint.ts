import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/**
 * 横向滚动提示(仅视觉):宽表在 Pad 上横滚时,告诉模板「还能往左 / 往右滚」,
 * 用来给冻结列加分隔阴影、在表头显示「左右滑动」提示。不改变任何数据或交互。
 */
export function useScrollHint(el: Ref<HTMLElement | null | undefined>) {
  const overflow = ref(false)
  const atStart = ref(true)
  const atEnd = ref(true)

  function update() {
    const e = el.value
    if (!e) return
    const max = e.scrollWidth - e.clientWidth
    overflow.value = max > 1
    atStart.value = e.scrollLeft <= 1
    atEnd.value = e.scrollLeft >= max - 1
  }

  let ro: ResizeObserver | null = null
  onMounted(() => {
    const e = el.value
    if (!e) return
    e.addEventListener('scroll', update, { passive: true })
    ro = new ResizeObserver(update)
    ro.observe(e)
    if (e.firstElementChild) ro.observe(e.firstElementChild)
    update()
  })
  onBeforeUnmount(() => {
    el.value?.removeEventListener('scroll', update)
    ro?.disconnect()
  })

  return { overflow, atStart, atEnd, update }
}
