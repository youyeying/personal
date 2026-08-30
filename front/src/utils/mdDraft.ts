/**
 * 今日功能记录 md 解析（结束开发弹窗「导入 md 文件」用）
 *
 * 规范格式（用户确认）：
 *   每条功能以 `### [类型] 模块 标题` 开头，标题下 `1.` / `-` 列表项合并为描述。
 *   类型限 新增/修改/删除/修复；模块任意（弹窗内可再改）。
 *   非功能节（## 会话 / ## 本次功能 / ## 待办 / ### 待办 / # 大标题 / > 说明）一律跳过。
 */

/** 功能变更类型 */
export const FEATURE_TYPES = ['新增', '修改', '删除', '修复'] as const

/** 结束开发弹窗草稿行（导入解析的目标结构） */
export interface FeatureDraft {
  type: string
  module: string
  content: string
}

const TYPE_SET = new Set<string>(FEATURE_TYPES)
/** 三级标题里以这些词开头视为非功能节（如 待办） */
const SKIP_H3_PREFIX = ['待办']

/**
 * 解析规范 md 文本为草稿条目数组
 * @param md 文件文本
 * @returns 解析出的功能条目（无法识别类型的 `###` 节会被跳过）
 */
export function parseMdDraft(md: string): FeatureDraft[] {
  const drafts: FeatureDraft[] = []
  // 当前正在累积的条目（null=不在任何功能节内）
  let cur: FeatureDraft | null = null

  const lines = md.split(/\r?\n/)
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue

    // 大标题 / 二级标题：新节开始，关闭当前条目（避免把后续节内容并入上一条）
    if (line.startsWith('# ') || line.startsWith('## ')) {
      cur = null
      continue
    }
    // 引用说明行（文件头部）
    if (line.startsWith('>')) continue

    // 三级标题：功能条目（或非功能节）
    if (line.startsWith('### ')) {
      const body = line.slice(4).trim()
      // 非功能节（如 待办）：关闭当前条目后跳过
      if (SKIP_H3_PREFIX.some((p) => body.startsWith(p))) {
        cur = null
        continue
      }
      // 规范格式：### [类型] 模块 标题
      const m = body.match(/^\[(新增|修改|删除|修复)\]\s*(\S+)\s*(.*)$/)
      if (m && TYPE_SET.has(m[1])) {
        cur = { type: m[1], module: m[2], content: m[3] }
        drafts.push(cur)
      } else {
        // 不符合规范格式的 `###` 节：不解析
        cur = null
      }
      continue
    }

    // 列表项 / 纯文本：追加到当前条目描述
    if (cur) {
      const item = line
        .replace(/^[-*]\s+/, '')
        .replace(/^\d+[.)]\s+/, '')
        .replace(/^\[[ x]\]\s*/, '')
        .trim()
      if (!item) continue
      cur.content += (cur.content ? '\n' : '') + item
    }
  }

  return drafts
}
