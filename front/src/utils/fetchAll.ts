/**
 * 分页接口全量拉取工具（区间/全量数据用）
 * 后端分页接口 @Max(100) 限制每页上限，此工具循环翻页直到取完
 * 用于：饮食历史/统计、锻炼分析等需要区间全量记录的页面
 */

/** 分页响应结构（与 PageResult 对齐） */
interface PagedResult<T> {
  records: T[]
  total: number
}

/**
 * 循环翻页拉取全量记录
 *
 * @param fetchPage 页码请求函数（(page: number, size: number) => Promise<PagedResult<T>>）
 * @param size 每页条数（默认 100，即后端上限）
 * @returns 全量记录数组
 *
 * @example
 * ```ts
 * const all = await fetchAllRecords((page, size) =>
 *   listFoodRecords({ startDate, endDate, page, size })
 * )
 * ```
 */
export async function fetchAllRecords<T>(
  fetchPage: (page: number, size: number) => Promise<PagedResult<T>>,
  size = 100
): Promise<T[]> {
  const all: T[] = []
  let page = 1
  while (true) {
    const res = await fetchPage(page, size)
    all.push(...res.records)
    // 取够 total 或本页为空即停（防御 total=0 或异常响应）
    if (all.length >= res.total || res.records.length === 0) break
    page++
    // 防御上限：超过 100 页（1 万条）强制终止，防接口异常时死循环
    if (page > 100) break
  }
  return all
}
