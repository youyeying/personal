/**
 * 用户端 Axios 请求封装（复用共享层工厂）
 * - 统一 baseURL：/api（vite 代理到 8080）
 * - 401 并发安全刷新重放、业务错误提示、在线过期跳装修登录页
 * 实现细节见 packages/shared/src/requestFactory.ts
 */
import { createRequestApi } from '@personal/shared'
import { getAccessToken, setAccessToken, refreshAccessToken } from '@/utils/authToken'
import { useUserStore } from '@/store/user'

const { requestApi } = createRequestApi({
  token: { getAccessToken, setAccessToken, clearAccessToken: () => setAccessToken(null), refreshAccessToken },
  loginPath: '/login',
  clearAuthState: () => {
    setAccessToken(null)
    useUserStore().setUserInfo(null)
  }
})

export { requestApi }
export default requestApi