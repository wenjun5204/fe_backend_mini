import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 前端请求 /api 时转发到后端
      '/api': 'http://localhost:8080',
    },
  },
})
