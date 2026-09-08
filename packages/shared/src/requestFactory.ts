/**
 * Axios 请求封装工厂（双站点共享）
 * - 统一 baseURL：/api（vite 代理到 8080）
 * - 请求拦截：自动携带内存 accessToken（Bearer）
 * - 响应拦截：code !== 200 时 reject 业务错误；401 时并发安全地调 /auth/refresh 换新后重放原请求；
 *   刷新也失败则提示「登录已过期，请重新登录」并跳登录页
 * - 错误提示：业务失败自动 ElMessage，调用方可不重复提示
 *
 * 双端差异点参数化：
 * - loginPath：跳登录页路径（用户端 /login，开发端 /admin/login）
 * - clearAuthState：清理内存 token + 用户信息（store 由各端注入，防共享包反向依赖 pinia）
 */
import axios from 'axios'
import type { AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { TokenManager } from './tokenManager'

/** 后端统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface RequestFactoryOptions {
  token: TokenManager
  /** 跳登录页路径（用户端 /login，开发端 /admin/login） */
  loginPath: string
  /** 会话彻底失效时的清理回调（清内存 token + userInfo） */
  clearAuthState: () => void
}

export function createRequestApi(options: RequestFactoryOptions): {
  requestApi: <T>(config: AxiosRequestConfig & { silent?: boolean }) => Promise<T>
} {
  const { token, loginPath, clearAuthState } = options

  const request = axios.create({
    baseURL: '/api',
    timeout: 15000
  })

  // 请求拦截：携带内存 accessToken
  request.interceptors.request.use((config) => {
    const t = token.getAccessToken()
    if (t) {
      config.headers.Authorization = `Bearer ${t}`
    }
    return config
  })

  /** 会话彻底失效：清 token + 提示 + 跳登录（带 redirect；开发端登录页为 /admin/login） */
  function handleExpired(message?: string) {
    clearAuthState()
    if (window.location.pathname !== loginPath) {
      ElMessage.warning(message || '登录已过期，请重新登录')
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.href = `${loginPath}?redirect=${redirect}`
    }
  }

  // 响应拦截：统一处理业务错误
  request.interceptors.response.use(
    async (response) => {
      const res = response.data as ApiResult
      // 业务成功（code 200）：放行，由 requestApi 解包
      if (res.code === 200) {
        return response
      }
      // 401 未登录/登录过期：尝试刷新后重放；失败则跳登录
      if (res.code === 401) {
        const cfg = response.config
        const url = cfg.url || ''
        // 认证类接口自身（登录/注册/刷新）不走刷新重放，直接透出错误
        const isAuthFlow =
          url.includes('/auth/refresh') ||
          url.includes('/auth/login') ||
          url.includes('/auth/register')
        if (!isAuthFlow && !(cfg as { _retried?: boolean })._retried) {
          const newToken = await token.refreshAccessToken()
          if (newToken) {
            ;(cfg as { _retried?: boolean })._retried = true
            cfg.headers.set('Authorization', `Bearer ${newToken}`)
            return request(cfg) // 用新 token 重放原请求
          }
        }
        handleExpired(res.message)
        return Promise.reject(new Error(res.message || '登录已过期'))
      }
      // 业务失败：默认提示错误（silent 标记时由调用方自行处理）
      const silent = (response.config as { silent?: boolean })?.silent
      if (!silent) {
        ElMessage.error(res.message || '操作失败')
      }
      return Promise.reject(new Error(res.message || '操作失败'))
    },
    (error) => {
      // 网络错误 / HTTP 错误
      const message = error.response?.data?.message || error.message || '网络错误'
      const silent = (error.config as { silent?: boolean })?.silent
      if (!silent) {
        ElMessage.error(message)
      }
      return Promise.reject(new Error(message))
    }
  )

  async function requestApi<T>(
    config: AxiosRequestConfig & { silent?: boolean }
  ): Promise<T> {
    const response = await request(config)
    const res = response.data as ApiResult<T>
    return res.data
  }

  return { requestApi }
}