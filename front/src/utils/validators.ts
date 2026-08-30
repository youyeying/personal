/**
 * 表单校验工具（公共）
 * 登录/注册等表单统一引用，保证校验规则一致
 */

/** 用户名：英文大小写 + 数字，4-30 位 */
export const USERNAME_REG = /^[A-Za-z0-9]{4,30}$/

/** 密码：英文大小写 + 数字，8-16 位 */
export const PASSWORD_REG = /^[A-Za-z0-9]{8,16}$/

/** 手机号：11 位，1 开头，第二位 3-9 */
export const PHONE_REG = /^1[3-9]\d{9}$/

/**
 * 校验用户名
 * @param value 输入值
 * @returns 错误提示（空字符串表示通过）
 */
export function validateUsername(value: string): string {
  if (value.length === 0) return ''
  if (!USERNAME_REG.test(value)) return '用户名需为 4-30 位英文或数字'
  return ''
}

/**
 * 校验密码
 * @param value 输入值
 * @returns 错误提示（空字符串表示通过）
 */
export function validatePassword(value: string): string {
  if (value.length === 0) return ''
  if (!PASSWORD_REG.test(value)) return '密码需为 8-16 位英文或数字'
  return ''
}

/**
 * 校验手机号
 * @param value 输入值
 * @returns 错误提示（空字符串表示通过）
 */
export function validatePhone(value: string): string {
  if (value.length === 0) return ''
  if (!PHONE_REG.test(value)) return '手机号格式不正确'
  return ''
}
