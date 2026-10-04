/* 主题预置：首屏绘制前按本机偏好（dpub.theme：light | dark | system）给 <html> 加 dark 类。完整逻辑见 src/composables/useTheme.ts。 */
;(function () {
  try {
    var m = null
    try { m = localStorage.getItem('dpub.theme') } catch (e) {}
    var dark = m === 'dark' || (m !== 'light' && window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches)
    var root = document.documentElement
    root.dataset.theme = dark ? 'dark' : 'light'
    if (dark) root.classList.add('dark')
    var meta = document.querySelector('meta[name="theme-color"]')
    if (meta) meta.setAttribute('content', dark ? '#0d1117' : '#f7f8fa')
  } catch (e) {}
})()
