import { createApp, watch } from 'vue'
import '@fontsource/noto-sans-sc/400.css'
import '@fontsource/noto-sans-sc/500.css'
import '@fontsource/noto-sans-sc/600.css'
import '@fontsource/noto-sans-sc/700.css'
import '@fontsource/barlow/400.css'
import '@fontsource/barlow/500.css'
import '@fontsource/barlow/600.css'
import '@fontsource/barlow/700.css'
import '@fontsource/barlow-condensed/500.css'
import '@fontsource/barlow-condensed/600.css'
import './style.css'
import App from './App.vue'
import { applyAppearance, syncAppearanceFromServer } from './app/appearance'
import { router } from './app/router'
import { session } from './app/session'

applyAppearance()
createApp(App).use(router).mount('#app')

// 平台外观以服务端保存的为准:启动时同步一次,登录成功(会话建立)后再同步一次;失败时保留本地外观
void syncAppearanceFromServer()
watch(() => session.current?.token ?? null, token => { if (token) void syncAppearanceFromServer() })
