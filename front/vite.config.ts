import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * Vite 配置
 * - @ 别名指向 src
 * - 开发服务器代理 /api 到后端 8080
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    host: true,
    // 放行公网隧道域名（Cloudflare quick tunnel / 内网穿透），真机通过公网地址访问时 Host 不在默认白名单
    // 用后缀通配，隧道每次生成的子域名都能复用，无需每次改配置
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      // 前端请求 /api/** 代理到后端
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      // 本地文件上传回显
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
