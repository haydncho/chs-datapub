import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { registerUnauthorized } from './stores/auth'
import { initTheme } from './composables/useTheme'
import '@fontsource/inter/latin-400.css'
import '@fontsource/inter/latin-500.css'
import '@fontsource/inter/latin-600.css'
import '@fontsource/inter/latin-700.css'
import './style.css'

initTheme()
const app = createApp(App).use(createPinia()).use(router)
registerUnauthorized(() => {
  if (router.currentRoute.value.name !== 'login') void router.push({ name: 'login' })
})
app.mount('#app')
