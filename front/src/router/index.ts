import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { getAccessToken, refreshAccessToken } from '@/utils/authToken'

/**
 * 路由配置
 * - /login：登录页（免认证）
 * - 其余页面：需登录，无 token 时守卫跳转 /login
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    // 登录页组件懒加载
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/views/home/Home.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'overview',
        component: () => import('@/views/homepage/Overview.vue'),
        meta: { title: '首页概览' }
      },
      {
        path: 'expense',
        name: 'expense',
        component: () => import('@/views/expense/ExpenseList.vue'),
        meta: { title: '记账' }
      },
      {
        path: 'expense/stat',
        name: 'expense-stat',
        component: () => import('@/views/expense/ExpenseStat.vue'),
        meta: { title: '记账统计' }
      },
      {
        path: 'health',
        name: 'health',
        component: () => import('@/views/health/Health.vue'),
        meta: { title: '健康' }
      },
      {
        path: 'learn',
        name: 'learn',
        component: () => import('@/views/learn/Learn.vue'),
        meta: { title: '学习' }
      },
      {
        path: 'daily-note',
        name: 'daily-note',
        component: () => import('@/views/daily-note/DailyNote.vue'),
        meta: { title: '每日总结' }
      },
      {
        path: 'operation-log',
        name: 'operation-log',
        component: () => import('@/views/operation-log/OperationLog.vue'),
        meta: { title: '操作日志' }
      },
      {
        path: 'dev-log',
        name: 'dev-log',
        component: () => import('@/views/dev-log/DevLog.vue'),
        meta: { title: '开发日志' }
      },
      {
        path: 'profile',
        name: 'profile',
        component: () => import('@/views/profile/Profile.vue'),
        meta: { title: '个人中心' }
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

/**
 * 全局路由守卫
 * - 目标页需要登录：若无内存 accessToken，先用 Cookie 里的 refresh 静默换新；
 *   换新成功放行，失败跳 /login（带 redirect）
 * - 已登录访问 /login → 跳首页
 */
router.beforeEach(async (to) => {
  const requiresAuth = to.matched.some((record) => record.meta.requiresAuth)

  // 需登录但本地无 accessToken：尝试静默刷新恢复登录态（F5/重开页面场景）
  if (requiresAuth && !getAccessToken()) {
    const token = await refreshAccessToken()
    if (!token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  if (to.path === '/login' && getAccessToken()) {
    return { path: '/' }
  }
})

/**
 * 全局标题绑定：每次路由切换后设置 document.title
 * 标题取匹配链上最近一层的 meta.title，默认「个人记录系统」
 */
router.afterEach((to) => {
  const matched = to.matched.filter((record) => record.meta?.title)
  const title = matched.length > 0 ? matched[matched.length - 1].meta.title as string : ''
  document.title = title ? `${title} - 个人记录系统` : '个人记录系统'
})

export default router
