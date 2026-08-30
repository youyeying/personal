/**
 * 删除确认通用工具（跨模块复用）
 * 各页面删除逻辑（弹确认 → 执行删除 → 提示成功）几乎一致，仅确认文案与删除回调不同
 */
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 弹删除确认框；确认后执行 del() 并提示成功，取消则静默（抛出取消异常）
 * @param message 确认文案（如 `确定删除 xxx 的记录吗？`）
 * @param del     确认后的删除动作（返回删除接口调用 Promise）
 * @param successText 成功提示文字，默认「已删除」
 */
export async function confirmDelete(
  message: string,
  del: () => Promise<unknown>,
  successText = '已删除'
): Promise<void> {
  await ElMessageBox.confirm(message, '删除记录', { type: 'warning' })
  await del()
  ElMessage.success(successText)
}