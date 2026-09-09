/**
 * accessToken 内存单例 + 静默刷新（双站点共享工厂）
 *
 * 鉴权（双 token）：
 * - accessToken：JWT 短效，只存内存，不落 localStorage（防 XSS 读取）
 * - refreshToken：后端 httpOnly Cookie，本模块不可见，用它调 /auth/refresh 换新 access
 *
 * 刷新用独立的裸 axios（不带全局拦截器），避免 401 → 刷新 → 再 401 的无限递归；
 * refreshClient 与业务同源（/api 代理），浏览器自动携带同源 Cookie。
 *
 * 双站点隔离（2026-09-08）：
 * - site 决定 /auth/refresh 的 ?site= 参数（后端按此读对应 Cookie：user→refresh_token / admin→refresh_token_admin）
 * - 跨 tab 同步 key 带站点后缀：用户端/开发端同源 localhost 共享同一 localStorage，
 *   若两端共用同名 key，一端刷新会触发另一端清空内存 token，形成互相干扰
 */
import axios from 'axios'

/** token 管理实例（双端各自通过 createTokenManager 创建） */
export interface TokenManager {
  getAccessToken(): string | null
  setAccessToken(t: string | null): void
  clearAccessToken(): void
  refreshAccessToken(): Promise<string | null>
}

export function createTokenManager(site: 'user' | 'admin'): TokenManager {
  let accessToken: string | null = null
  /** accessToken 过期时间戳（ms）；解析不到时为 null */
  let accessExpiresAt: number | null = null
  /** 单飞：并发多个 401 只发一次刷新，其余挂起等待同一结果 */
  let refreshing: Promise<string | null> | null = null

  const refreshClient = axios.create({ baseURL: '/api', timeout: 15000 })

  /** 跨 tab 刷新同步信号键（只存时间戳，不含敏感 token）——带站点后缀防两端互相清空 */
  const SYNC_KEY = `auth_token_synced_at_${site}`

  /** 其他 tab 刷新成功后清空本 tab 内存 token：下次请求将用新 Cookie 刷新（避免旧 token rotation 作废） */
  if (typeof window !== 'undefined' && typeof localStorage !== 'undefined') {
    window.addEventListener('storage', (e) => {
      if (e.key === SYNC_KEY && e.newValue) {
        accessToken = null
        accessExpiresAt = null
      }
    })
  }

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

  function getAccessToken(): string | null {
    return accessToken
  }

  function setAccessToken(t: string | null): void {
    accessToken = t
    accessExpiresAt = t ? parseExpiry(t) : null
  }

  function clearAccessToken(): void {
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
   * 已过期/无 token 时用 Cookie 里的 refresh 换新。
   * - 瞬时失败（网络/429 等）自动重试最多 2 次，避免抖动直接踢登录
   * - 仅明确 401（会话真失效）才返回 null，调用方据此跳登录
   * - 成功时写跨 tab 同步信号
   */
  function refreshAccessToken(): Promise<string | null> {
    if (isAccessTokenValid()) return Promise.resolve(accessToken as string)
    if (refreshing) return refreshing

    refreshing = (async () => {
      for (let attempt = 0; attempt < 3; attempt++) {
        try {
          const res = await refreshClient.post<{ code: number; data?: { accessToken: string } }>(`/auth/refresh?site=${site}`)
          if (res.status === 200 && res.data?.code === 200 && res.data.data?.accessToken) {
            setAccessToken(res.data.data.accessToken)
            // 跨 tab 通知：其他标签页清空内存 token，用新 Cookie 刷新
            try {
              localStorage.setItem(SYNC_KEY, String(Date.now()))
            } catch {
              /* 隐私模式等无 storage 时忽略 */
            }
            return accessToken
          }
          // 明确 401（会话失效）→ 不再重试
          if (res.data?.code === 401) return null
          // 其他业务码（429 限流等）→ 稍后重试
        } catch {
          // 网络错误/超时 → 稍后重试
        }
        if (attempt < 2) {
          await new Promise((r) => setTimeout(r, 400 * (attempt + 1)))
        }
      }
      return null
    })()

    // 单飞锁用后必须复位（v2.5.3）：Promise 完成后置回 null，
    // 否则下次过期再进来时 `if (refreshing) return refreshing` 直接复用上一次
    // 已 resolve 的旧 Promise——返回早已过期的旧 accessToken，重放必然再 401 被踢
    void refreshing.finally(() => {
      refreshing = null
    })

    return refreshing
  }

  return { getAccessToken, setAccessToken, clearAccessToken, refreshAccessToken }
}
