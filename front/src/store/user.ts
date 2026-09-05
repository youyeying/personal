import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { clearAccessToken } from '@/utils/authToken'

/** 用户信息 */
export interface UserInfo {
  id: number
  username: string
  phone: string
  nickname: string
  /** 后端存相对路径（如 avatar/1/xxx.jpg），回显时由 avatarUrl 拼 /uploads/ */
  avatar: string | null
  targetWeight: number | null
  /** 年龄（岁，BMR 二期用） */
  age: number | null
  /** 身高 cm（BMR 二期用） */
  height: number | null
  /** 性别 male/female */
  gender: string | null
  /** 每日目标热量缺口 kcal/天（饮食预算 = 1.2BMR + 锻炼 − 缺口，用户自定义） */
  dietTargetGap: number | null
  passwordUpdatedAt: string | null
  createdAt: string
}

/**
 * 用户状态：当前用户信息（内存，主操作后重载）
 * - 单一可信源：userInfo → 所有头像/昵称/手机号展示均从此取
 * - accessToken 不落这里，统一走 @/utils/authToken 内存单例（防 XSS 落盘）
 * - 头像：后端返回相对路径，由 avatarUrl computed 拼为可访问 URL
 */
export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo | null>(null)

  /** 头像可访问 URL（相对路径 → /uploads/**）；无头像返回 null，UI 自行兜底 icon */
  const avatarUrl = computed<string | null>(() => {
    const a = userInfo.value?.avatar
    if (!a) return null
    return a.startsWith('/') ? a : `/uploads/${a}`
  })

  function setUserInfo(value: UserInfo | null) {
    userInfo.value = value
  }

  /** 仅清理前端本地登录态（登出/过期）。服务端会话由调用方另行调 /auth/logout 撤销 */
  function clearLocalAuth() {
    clearAccessToken()
    userInfo.value = null
  }

  return { userInfo, avatarUrl, setUserInfo, clearLocalAuth }
})