import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  // separate dep caches let several dev servers run side by side
  cacheDir: process.env.VITE_CACHE_DIR ?? 'node_modules/.vite',
  resolve: {
    alias: { '@': path.resolve(import.meta.dirname, './src') },
  },
  server: {
    // dev: forward API calls to the core service (server/core, :8080)
    proxy: { '/api': { target: process.env.API_TARGET ?? 'http://localhost:8080', changeOrigin: true } },
  },
})
