/**
 * 数据导出接口：CSV（单模块）/ JSON（全量备份）
 * - 返回文件流（非统一 ApiResult 包装），单独 axios 实例手动带 token
 * - 浏览器侧 Blob + <a download> 触发保存，文件名前端自拟（模块+日期）
 */
import axios from 'axios'
import { getAccessToken } from '@/utils/authToken'

/** 可导出模块 */
export const EXPORT_MODULES = [
  { key: 'expense', label: '记账' },
  { key: 'food', label: '饮食' },
  { key: 'exercise', label: '锻炼' },
  { key: 'weight', label: '体重' },
  { key: 'learn', label: '学习' },
  { key: 'dailyNote', label: '每日总结' }
] as const

const fileRequest = axios.create({ baseURL: '/api', timeout: 60000 })
fileRequest.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

/** 触发浏览器保存文件 */
function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  a.click()
  URL.revokeObjectURL(url)
}

/** 导出单模块 CSV（module ∈ EXPORT_MODULES） */
export async function exportModuleCsv(module: string, label: string) {
  const res = await fileRequest.get(`/export/${module}.csv`, { responseType: 'blob' })
  saveBlob(new Blob([res.data]), `个人记录-${label}-${todayStr()}.csv`)
}

/** 导出全量 JSON 备份（记录 + 字典） */
export async function exportAllJson() {
  const res = await fileRequest.get('/export/all.json', { responseType: 'blob' })
  saveBlob(new Blob([res.data], { type: 'application/json' }), `个人记录-备份-${todayStr()}.json`)
}

function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
