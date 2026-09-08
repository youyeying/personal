import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clearAccessToken } from '@/utils/authToken'
import type { AdminInfo } from '@/api/auth'

/**
 * 开发端登录状态：当前开发账号信息（内存）
 * - 仅存开发账号（userType=2）；token 统一走 @/utils/authToken 内存单例（防 XSS 落盘）
 */
export const useUserStore = defineStore('user', () => {
  /** 当前开发账号信息 */
  const userInfo = ref<AdminInfo | null>(null)

  function setUserInfo(value: AdminInfo | null) {
    userInfo.value = value
  }

  /** 仅清理前端本地登录态（登出/过期）。服务端会话由调用方另行调 /auth/logout 撤销 */
  function clearLocalAuth() {
    clearAccessToken()
    userInfo.value = null
  }

  return { userInfo, setUserInfo, clearLocalAuth }
})
