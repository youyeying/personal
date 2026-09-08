import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getAccessToken, refreshAccessToken } from '@/utils/authToken'
import { getMe, logout } from '@/api/auth'

/**
 * 开发端路由
 * - /admin/login：开发账号登录页（免认证）
 * - 其余页面：需登录（userType=2），无 token 时守卫跳 /admin/login
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/admin/login',
    name: 'admin-login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '开发登录' }
  },
  {
    path: '/',
    component: () => import('@/views/layout/AdminLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/dev-log',
    children: [
      {
        path: 'dev-log',
        name: 'dev-log',
        component: () => import('@/views/dev-log/DevLog.vue'),
        meta: { title: '开发日志' }
      },
      {
        path: 'operation-log',
        name: 'operation-log',
        component: () => import('@/views/operation-log/OperationLog.vue'),
        meta: { title: '操作日志' }
      },
      {
        path: 'template',
        name: 'template',
        component: () => import('@/views/template/TemplateManage.vue'),
        meta: { title: '基础数据管理' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

/** 校验当前会话是否为开发账号（userType=2），非开发账号则登出并跳登录 */
async function ensureAdmin(): Promise<boolean> {
  try {
    const res = await getMe()
    if (res.userType === 2) return true
    // 业务用户 token 闯入开发端：清本地登录态
    await logout().catch(() => undefined)
    return false
  } catch {
    return false
  }
}

/**
 * 全局路由守卫
 * - 目标页需登录：无内存 accessToken 先用 Cookie 里的 refresh 静默换新；
 *   换新成功再校验 userType=2，失败跳 /admin/login（带 redirect）
 * - 已登录访问 /admin/login → 跳首页
 */
router.beforeEach(async (to) => {
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  if (requiresAuth && !getAccessToken()) {
    const token = await refreshAccessToken()
    if (!token) {
      return { path: '/admin/login', query: { redirect: to.fullPath } }
    }
  }

  if (requiresAuth && !(await ensureAdmin())) {
    return { path: '/admin/login', query: { redirect: to.fullPath } }
  }

  if (to.path === '/admin/login' && getAccessToken()) {
    return { path: '/' }
  }
})

/**
 * 全局标题绑定
 */
router.afterEach((to) => {
  const matched = to.matched.filter((record) => record.meta?.title)
  const title = matched.length > 0 ? (matched[matched.length - 1].meta.title as string) : ''
  document.title = title ? `${title} - 开发端` : '开发端'
})

export default router
