/**
 * 学习接口：学习记录 分页 / 统计 / 增删改 + 附件上传/删除/列表
 */
import { requestApi } from './request'
import type { PageResult } from './types'

/** 学习附件 */
export interface NoteFile {
  id: number
  learnRecordId: number
  /** 原始文件名 */
  fileName: string
  /** 本地相对路径（/uploads/** 静态回显） */
  filePath: string
  /** 类型：pdf/png/jpg */
  fileType: string
  /** 文件大小（字节） */
  fileSize: number
  createdAt: string
}

/** 学习记录（分页返回，含附件列表） */
export interface LearnRecord {
  id: number
  /** 学习主题 */
  title: string
  /** 收获笔记 */
  content: string | null
  /** 时长（分钟） */
  duration: number | null
  /** 方式：阅读/视频/课程/实践/其他 */
  way: string
  /** 掌握程度 1-5 */
  mastery: number | null
  /** yyyy-MM-dd */
  learnDate: string
  createdAt: string
  files: NoteFile[]
}

/** 查询参数 */
export interface LearnQuery {
  way?: string
  keyword?: string
  startDate?: string
  endDate?: string
  page?: number
  size?: number
}

/** 保存参数（新增 / 修改共用） */
export interface LearnSave {
  title: string
  content?: string
  duration?: number | null
  way: string
  mastery?: number | null
  learnDate: string
}

/** 学习统计结果 */
export interface LearnStatistics {
  /** 累计记录数 */
  totalCount: number
  /** 累计时长（分钟） */
  totalDuration: number
  /** 今日时长（分钟） */
  todayMinutes: number
  /** 本月时长（分钟） */
  monthMinutes: number
  /** 掌握程度均分（无记录为 null） */
  avgMastery: number | null
  /** 按方式计数（阅读/视频/课程/实践/其他） */
  byWay: Record<string, number>
  /** 掌握程度分布（1-5） */
  mastery: Record<string, number>
  /** 近 14 天学习时长趋势 */
  trend: { dates: string[]; minutes: number[] }
}

/** 分页查询（最新在前） */
export function listLearnRecords(query: LearnQuery = {}) {
  return requestApi<PageResult<LearnRecord>>({
    url: '/learn-records',
    method: 'GET',
    params: query
  })
}

/** 学习统计 */
export function getLearnStatistics() {
  return requestApi<LearnStatistics>({
    url: '/learn-records/statistics',
    method: 'GET'
  })
}

/** 新增学习记录 */
export function createLearnRecord(data: LearnSave) {
  return requestApi<LearnRecord>({
    url: '/learn-records',
    method: 'POST',
    data
  })
}

/** 修改学习记录 */
export function updateLearnRecord(id: number, data: LearnSave) {
  return requestApi<LearnRecord>({
    url: `/learn-records/${id}`,
    method: 'PUT',
    data
  })
}

/** 删除学习记录（级联软删附件） */
export function deleteLearnRecord(id: number) {
  return requestApi<null>({
    url: `/learn-records/${id}`,
    method: 'DELETE'
  })
}

/** 上传学习附件（multipart） */
export function uploadNoteFile(file: File, learnRecordId: number) {
  const form = new FormData()
  form.append('file', file)
  form.append('learnRecordId', String(learnRecordId))
  return requestApi<NoteFile>({
    url: '/files/note',
    method: 'POST',
    data: form
  })
}

/** 删除学习附件 */
export function deleteNoteFile(id: number) {
  return requestApi<null>({
    url: `/files/note/${id}`,
    method: 'DELETE'
  })
}

/** 某学习记录的附件列表 */
export function listNoteFiles(learnRecordId: number) {
  return requestApi<NoteFile[]>({
    url: '/files/note/list',
    method: 'GET',
    params: { learnRecordId }
  })
}
