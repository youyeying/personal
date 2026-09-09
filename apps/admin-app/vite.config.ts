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
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      // 共享层源码直引（双端同一份实现，消除复制）
      '@personal/shared': fileURLToPath(new URL('../../packages/shared/src/index.ts', import.meta.url))
    }
  },
  server: {
    // 开发端约定端口 5174（README/前端设计文档/记忆.md 均为此口径，勿与用户端 5173 冲突）
    port: 5174,
    host: true,
    // 放行公网隧道域名（Cloudflare quick tunnel / 内网穿透），真机通过公网地址访问时 Host 不在默认白名单
    // 用后缀通配，隧道每次生成的子域名都能复用，无需每次改配置
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      // 前端请求 /api/** 代理到后端
      // 注意：不能开 changeOrigin——后端 /auth/refresh 有 Origin vs Host 同源校验，
      // changeOrigin 会把 Host 改写为 localhost:8080，用 127.0.0.1/局域网 IP/公网隧道访问时
      // Origin host 与改写后的 Host host 必然不匹配，刷新接口永远失败 → 反复掉登录（v2.5.2）
      '/api': {
        target: 'http://localhost:8080'
      },
      // 本地文件上传回显
      '/uploads': {
        target: 'http://localhost:8080'
      }
    }
  }
})
