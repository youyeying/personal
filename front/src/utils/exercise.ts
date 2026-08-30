/**
 * 锻炼消耗计算（纯函数工具）
 * 公式基于 MET 代谢当量（Compendium of Physical Activities）：
 *   总消耗(kcal) = MET × 3.5 × 体重(kg) × 分钟 ÷ 200
 *   净消耗       = (MET − 1) × 3.5 × 体重(kg) × 分钟 ÷ 200（去掉静息基础代谢）
 * - 散步：距离+分钟 → 平均速度 → 档位 → MET
 * - 力量：个数+分钟 → 实际速度/参考速度 → 强度系数(0.6~1.4) → MET
 * - 平板：按秒记时长，固定基础 MET
 * - 爬楼梯：层数×次数×12秒/层 → 分钟，固定基础 MET
 * 改动公式只改此文件，历史记录展示即时跟随（大卡不落库）
 */

/** 散步速度 → MET 档位（km/h 区间左闭右开） */
export const WALK_MET_LEVELS = [
  { max: 3.2, met: 2.8, label: '慢走' },
  { max: 4.0, met: 3.0, label: '正常走' },
  { max: 4.8, met: 3.5, label: '稍快走' },
  { max: 5.6, met: 4.3, label: '快走' },
  { max: Infinity, met: 5.0, label: '暴走' }
] as const

/** 强度系数夹取区间 */
const COEF_MIN = 0.6
const COEF_MAX = 1.4

/** 散步合理速度上限（km/h）：走路物理上限约 12-15，超过必为输入错误，拦截并提示 */
export const WALK_SPEED_MAX = 12

/** 由距离(km)与分钟算平均速度 km/h */
export function walkSpeedKmh(distanceKm: number, minutes: number): number {
  if (minutes <= 0) return 0
  return distanceKm / (minutes / 60)
}

/** 由速度查散步 MET 档位 */
export function walkMet(speedKmh: number): { met: number; label: string } {
  const level = WALK_MET_LEVELS.find((l) => speedKmh < l.max) ?? WALK_MET_LEVELS[WALK_MET_LEVELS.length - 1]
  return { met: level.met, label: level.label }
}

/** 力量强度系数：实际速度(个/分钟) ÷ 参考速度，夹在 0.6~1.4 */
export function strengthIntensity(speedPerMin: number, refSpeed: number): { coef: number; label: '低' | '中' | '高' } {
  if (refSpeed <= 0) return { coef: 1, label: '中' }
  const coef = Math.min(Math.max(speedPerMin / refSpeed, COEF_MIN), COEF_MAX)
  const label = coef < 0.85 ? '低' : coef < 1.15 ? '中' : '高'
  return { coef, label }
}

/** 力量动作有效 MET = 基础 MET × 强度系数（保留 1 位小数） */
export function strengthMet(baseMet: number, speedPerMin: number, refSpeed: number): number {
  const { coef } = strengthIntensity(speedPerMin, refSpeed)
  return Math.round(baseMet * coef * 10) / 10
}

/** 由 MET 与分钟、体重算总消耗与净消耗（kcal，四舍五入） */
export function calcKcal(met: number, minutes: number, weightKg: number): { total: number; net: number } {
  if (minutes <= 0 || weightKg <= 0) return { total: 0, net: 0 }
  const total = Math.round((met * 3.5 * weightKg * minutes) / 200)
  const net = Math.round(((met - 1) * 3.5 * weightKg * minutes) / 200)
  return { total, net }
}

/** 记录展示用的时长文本 */
export function durationText(rec: { type: string; minutes: number | null; seconds: number | null; floors: number | null; times: number | null }): string {
  switch (rec.type) {
    case 'plank':
      return `${rec.seconds ?? 0}s`
    case 'stairs':
      return `${rec.floors ?? 0}层×${rec.times ?? 0}次`
    default:
      return formatDuration(totalSeconds(rec.minutes, rec.seconds))
  }
}

/** 分钟+秒 → 总秒数（精确计算用；seconds 优先，其次 minutes） */
export function totalSeconds(minutes: number | null | undefined, seconds: number | null | undefined): number {
  if (seconds != null) return seconds
  if (minutes != null) return Math.round(minutes * 60)
  return 0
}

/** 总秒数 → 时长文本：如 100 → '1分40秒'，40 → '40秒'，0 → '0秒' */
export function formatDuration(totalSec: number): string {
  const s = Math.max(Math.round(totalSec), 0)
  const m = Math.floor(s / 60)
  const r = s % 60
  if (m === 0) return `${r}秒`
  if (r === 0) return `${m}分钟`
  return `${m}分${r}秒`
}
