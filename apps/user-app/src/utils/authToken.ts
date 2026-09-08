/**
 * 用户端 accessToken 内存单例 + 静默刷新（复用共享层工厂，site=user）
 * 细节（双 token、单飞、重试、跨 tab 同步）见 packages/shared/src/tokenManager.ts
 */
import { createTokenManager } from '@personal/shared'

const m = createTokenManager('user')

export function getAccessToken(): string | null {
  return m.getAccessToken()
}
export function setAccessToken(t: string | null): void {
  m.setAccessToken(t)
}
export function clearAccessToken(): void {
  m.clearAccessToken()
}
export function refreshAccessToken(): Promise<string | null> {
  return m.refreshAccessToken()
}