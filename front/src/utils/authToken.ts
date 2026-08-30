/**
 * accessToken 内存单例 + 静默刷新
 *
 * 鉴权（双 token）：
 * - accessToken：JWT 短效，只存内存，不落 localStorage（防 XSS 读取）
 * - refreshToken：后端 httpOnly Cookie，本模块不可见，用它调 /auth/refresh 换新 access
 *
 * 刷新用独立的裸 axios（不带全局拦截器），避免 401 → 刷新 → 再 401 的无限递归；
 * refreshClient 与业务同源（/api 代理），浏览器自动携带同源 Cookie
 */
import axios from 'axios'

let accessToken: string | null = null
/** accessToken 过期时间戳（ms）；解析不到时为 null */
let accessExpiresAt: number | null = null
/** 单飞：并发多个 401 只发一次刷新，其余挂起等待同一结果 */
let refreshing: Promise<string | null> | null = null

const refreshClient = axios.create({ baseURL: '/api', timeout: 15000 })

/**
 * 从 JWT payload 解析过期时间戳（ms）。
 * 只解码 payload 读 exp，不做签名校验（校验由后端负责）。
 */
function parseExpiry(token: string): number | null {
  try {
    const parts = token.split('.')
    if (parts.length < 2) return null
    const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp === 'number' ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(t: string | null): void {
  accessToken = t
  accessExpiresAt = t ? parseExpiry(t) : null
}

export function clearAccessToken(): void {
  accessToken = null
  accessExpiresAt = null
}

/** accessToken 是否有效（存在且未过期，预留 30s 缓冲防临界） */
function isAccessTokenValid(): boolean {
  if (!accessToken) return false
  if (accessExpiresAt == null) return true // 解析不到 exp 时视为有效，避免无谓刷新
  return Date.now() < accessExpiresAt - 30_000
}

/**
 * 静默刷新：仅当内存 accessToken 仍有效时直接复用；
 * 已过期/无 token 时用 Cookie 里的 refresh 换新，避免带着过期 token 反复 401 被误判过期。
 * 成功返回新 token；失败（无会话/已过期）返回 null，调用方据此跳登录。
 */
export function refreshAccessToken(): Promise<string | null> {
  if (isAccessTokenValid()) return Promise.resolve(accessToken as string)
  if (refreshing) return refreshing

  refreshing = (async () => {
    try {
      const res = await refreshClient.post<{ code: number; data?: { accessToken: string } }>('/auth/refresh')
      if (res.status === 200 && res.data?.code === 200 && res.data.data?.accessToken) {
        setAccessToken(res.data.data.accessToken)
        return accessToken
      }
      return null
    } catch {
      return null
    } finally {
      refreshing = null
    }
  })()

  return refreshing
}