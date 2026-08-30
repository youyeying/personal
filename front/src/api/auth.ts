/**
 * 认证接口
 */
import { requestApi } from './request'

/** 用户信息（后端 toUserInfo 脱敏返回，不含密码） */
export interface AuthUserInfo {
  id: number
  username: string
  phone: string
  nickname: string
  avatar: string | null
  targetWeight: number | null
  /** 年龄（岁，BMR 二期用） */
  age: number | null
  /** 身高 cm（BMR 二期用） */
  height: number | null
  /** 性别 male/female（BMR 二期用） */
  gender: string | null
  /** 上次修改密码时间（首次为空可免限修改） */
  passwordUpdatedAt: string | null
  createdAt: string
}

/** 登录/注册响应：登录返回短效 accessToken（仅存内存）；refresh 换新走 Cookie */
export interface LoginResult {
  accessToken: string
  userInfo: AuthUserInfo
}

/** 登录（silent：错误由登录页字段显示，不弹全局提示） */
export function login(data: { username: string; password: string }) {
  return requestApi<LoginResult>({
    url: '/auth/login',
    method: 'POST',
    data,
    silent: true
  })
}

/** 注册（silent：错误由注册页字段显示，不弹全局提示） */
export function register(data: { username: string; password: string; phone: string; nickname?: string }) {
  return requestApi<{ userInfo: AuthUserInfo }>({
    url: '/auth/register',
    method: 'POST',
    data,
    silent: true
  })
}

/** 登出：撤销服务端会话 + 清 refresh Cookie */
export function logout() {
  return requestApi<null>({
    url: '/auth/logout',
    method: 'POST',
    silent: true
  })
}

/** 获取当前用户信息 */
export function getMe() {
  return requestApi<{ userInfo: AuthUserInfo }>({
    url: '/auth/me',
    method: 'GET'
  })
}

/** 修改个人信息：昵称 / 目标体重 / 头像 / 年龄 / 身高 / 性别（本期不允许改手机号，由二期验证码开放） */
export function updateProfileApi(data: {
  nickname?: string
  targetWeight?: number | null
  /** 清除目标体重（true 时置空，忽略 targetWeight） */
  clearTargetWeight?: boolean
  avatar?: string
  /** 年龄（岁） */
  age?: number
  /** 身高 cm */
  height?: number
  /** 性别 male/female */
  gender?: string
}) {
  return requestApi<{ userInfo: AuthUserInfo }>({
    url: '/auth/profile',
    method: 'PUT',
    data
  })
}

/** 修改密码 */
export function changePasswordApi(data: { oldPassword: string; newPassword: string }) {
  return requestApi<null>({
    url: '/auth/password',
    method: 'PUT',
    data
  })
}

/** 上传头像（multipart），返回相对路径 */
export function uploadAvatar(file: File) {
  const form = new FormData()
  form.append('file', file)
  return requestApi<{ path: string }>({
    url: '/files/avatar',
    method: 'POST',
    data: form
  })
}