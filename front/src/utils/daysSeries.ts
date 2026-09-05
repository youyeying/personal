/**
 * 按天序列工具：区间日期遍历 + Map 聚合 + 空天补齐
 * 用于：饮食按天聚合摄入、锻炼按天聚合消耗、历史按天分组等「日期序列」场景
 */

/**
 * 补齐区间内每一天并映射为行数据（含无记录日）
 *
 * @param start 起始日期 yyyy-MM-dd（含）
 * @param end 结束日期 yyyy-MM-dd（含）
 * @param map 已按日期聚合的 Map<date, Partial<T>>（只有有记录的日期有键）
 * @param emptyFactory 无记录日的空行构造器（返回该日的初始值）
 * @returns 按日期升序的完整行数组（每天一行，无记录日用 emptyFactory 填充）
 *
 * @example
 * ```ts
 * const map = new Map<string, DayRow>()
 * for (const r of records) { /* 聚合到 map *\/ }
 * const days = fillDaysRange(start, end, map, (date) => ({ date, net: 0, count: 0 }))
 * ```
 */
export function fillDaysRange<T extends { date: string }>(
  start: string,
  end: string,
  map: Map<string, T>,
  emptyFactory: (date: string) => T
): T[] {
  const rows: T[] = []
  const startMs = new Date(`${start}T00:00:00`).getTime()
  const endMs = new Date(`${end}T00:00:00`).getTime()
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || startMs > endMs) return rows
  for (let t = startMs; t <= endMs; t += 86400000) {
    const d = new Date(t)
    const s = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
    rows.push(map.get(s) ?? emptyFactory(s))
  }
  return rows
}

/**
 * 按日期字符串聚合记录（升序遍历一次，减少重复 Map.get/set 样板）
 *
 * @param records 待聚合记录
 * @param dateOf 取记录日期
 * @param accumulate (已有聚合值 | undefined, 当前记录) => 新聚合值（undefined 时自行初始化）
 * @returns Map<date, 聚合值>（仅含有记录的日期）
 *
 * @example
 * ```ts
 * const map = groupByDate(records, (r) => r.recordDate, (acc, r) => {
 *   const row = acc ?? { date: r.recordDate, net: 0, count: 0 }
 *   row.net += r.net; row.count++
 *   return row
 * })
 * ```
 */
export function groupByDate<R, T>(
  records: R[],
  dateOf: (r: R) => string,
  accumulate: (acc: T | undefined, r: R) => T
): Map<string, T> {
  const map = new Map<string, T>()
  for (const r of records) {
    const date = dateOf(r)
    map.set(date, accumulate(map.get(date), r))
  }
  return map
}
