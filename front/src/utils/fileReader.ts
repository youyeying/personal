/**
 * 文件读取工具：支持读取文件内容 / 图片预览 / 获取文件信息
 * 场景：学习记录上传 PDF、截图，头像上传等
 */

/** 文件读取结果 */
export interface ReadFileResult {
  /** 原始 File 对象 */
  file: File
  /** 文件名 */
  name: string
  /** 大小（字节） */
  size: number
  /** MIME 类型 */
  type: string
  /** 扩展名（小写，不含点） */
  ext: string
  /** 内容（文本 or DataURL） */
  data: string
}

/** 将 File 读为文本内容（适用于 .txt/.md/.csv 等） */
export function readFileAsText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsText(file)
  })
}

/** 将 File 读为 DataURL（适用于图片预览 / PDF 预览） */
export function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result ?? ''))
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsDataURL(file)
  })
}

/** 将 File 读为 ArrayBuffer（适用于 Excel 等二进制解析） */
export function readFileAsArrayBuffer(file: File): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = () => reject(new Error('读取文件失败'))
    reader.readAsArrayBuffer(file)
  })
}

/** 解析文件基本信息 + 按需读取内容 */
export async function readFileInfo(file: File, mode: 'text' | 'dataUrl' | 'buffer' = 'text'): Promise<ReadFileResult> {
  const name = file.name
  const dot = name.lastIndexOf('.')
  const ext = dot >= 0 ? name.slice(dot + 1).toLowerCase() : ''

  let data = ''
  if (mode === 'text') {
    data = await readFileAsText(file)
  } else if (mode === 'dataUrl') {
    data = await readFileAsDataUrl(file)
  } else {
    const buf = await readFileAsArrayBuffer(file)
    data = new TextDecoder().decode(buf.slice(0, 0)) // 保持 data 为字符串，buffer 场景用 buffer 属性
    return {
      file,
      name,
      size: file.size,
      type: file.type,
      ext,
      data,
      ...{ buffer: buf }
    } as ReadFileResult & { buffer: ArrayBuffer }
  }

  return { file, name, size: file.size, type: file.type, ext, data }
}

/** 校验文件大小是否超限（MB） */
export function isFileTooLarge(file: File, maxMb: number): boolean {
  return file.size > maxMb * 1024 * 1024
}

/** 校验扩展名是否在白名单内 */
export function isAllowedExt(file: File, allowed: string[]): boolean {
  const dot = file.name.lastIndexOf('.')
  const ext = dot >= 0 ? file.name.slice(dot + 1).toLowerCase() : ''
  return allowed.includes(ext)
}
