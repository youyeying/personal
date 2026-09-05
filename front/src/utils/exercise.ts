/**
 * 锻炼消耗计算（纯函数工具，v1.29.0 模型）
 * 公式基于 MET 代谢当量（Compendium of Physical Activities 官方条目）：
 *   总消耗(kcal) = MET × 3.5 × 体重(kg) × 分钟 ÷ 200
 *   净消耗       = (MET − 1) × 3.5 × 体重(kg) × 分钟 ÷ 200（去掉静息基础代谢）
 * - 力量/有氧计数：速度 = 个数÷用时 → 速度比 = min(速度, 世界纪录)÷参考速度
 *   → MET = 1 + (baseMET−1)×速度比（恒 ≥ 1 净消耗不为负；速度越快强度越高）
 *   + 等效分钟 = 个数÷参考速度（总量由个数主导，用时不当乘子 → 填错时间不虚高）
 * - 散步：距离+分钟 → 平均速度 → 档位 → MET
 * - 骑行：距离+分钟 → 平均速度 → Compendium 2024 道路骑行六档 MET
 * - 爬楼梯：秒/层 = 总时长÷(层数×次数) → 分档 MET（快爬 8.8 ~ 慢爬 4.2）
 * - 平板：按秒记时长，固定基础 MET
 * 体重取「记录时体重快照」（body_weight），历史消耗固定不随当前体重变
 * 改动公式只改此文件，历史记录展示即时跟随（大卡不落库）
 * 参数依据（baseMET 官方值 / ref 用户平均节奏 / max 世界纪录）见本地文档 锻炼.md
 */

/** 散步速度 → MET 档位（km/h 区间左闭右开） */
export const WALK_MET_LEVELS = [
  { max: 3.2, met: 2.8, label: '慢走' },
  { max: 4.0, met: 3.0, label: '正常走' },
  { max: 4.8, met: 3.5, label: '稍快走' },
  { max: 5.6, met: 4.3, label: '快走' },
  { max: Infinity, met: 5.0, label: '暴走' }
] as const

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

/**
 * 骑行速度 → MET 档位（km/h 区间左闭右开）
 * 对齐 Compendium 2024 道路骑行官方条目（01010-01060，按 mph 分档，1 mph=1.609344 km/h 换算）
 */
export const CYCLING_MET_LEVELS = [
  { max: 16.09, met: 4.0, label: '休闲骑' },
  { max: 19.15, met: 6.8, label: '轻松骑' },
  { max: 22.37, met: 8.0, label: '中等骑' },
  { max: 25.59, met: 10.0, label: '较快骑' },
  { max: 30.58, met: 12.0, label: '快速骑' },
  { max: Infinity, met: 16.8, label: '竞速骑' }
] as const

/** 骑行合理速度上限（km/h）：普通骑行者 15-30，超过必为输入错误（下坡/分钟填错），拦截并提示 */
export const CYCLING_SPEED_MAX = 50

/** 由平均速度查骑行 MET 档位（速度换算复用 walkSpeedKmh） */
export function cyclingMet(speedKmh: number): { met: number; label: string } {
  const level = CYCLING_MET_LEVELS.find((l) => speedKmh < l.max) ?? CYCLING_MET_LEVELS[CYCLING_MET_LEVELS.length - 1]
  return { met: level.met, label: level.label }
}

/** 计数类等效分钟（总量主导）：等效分钟 = 总个数 ÷ 参考速度（用户平均节奏） */
export function repsEffectiveMinutes(reps: number | null | undefined, refSpeed: number | null | undefined): number {
  if (!reps || reps <= 0) return 0
  const ref = refSpeed && refSpeed > 0 ? refSpeed : 12
  return reps / ref
}

/** 计数类速度 → MET（v1.29.0）：MET = 1 + (baseMET − 1) × 速度比
 * 速度比 = min(实际速度, maxSpeed 世界纪录封顶) ÷ refSpeed 参考速度
 * - 恒 MET ≥ 1，净消耗永不为负（MET=1 即静息）
 * - 速度超世界纪录视为不可能（录入错误），按纪录速度封顶防爆炸
 * - 无时长（旧数据）按参考速度算，视为正常节奏 */
export function speedMet(
  baseMet: number,
  reps: number,
  totalSec: number,
  refSpeed: number | null | undefined,
  maxSpeed: number | null | undefined
): number {
  const ref = refSpeed && refSpeed > 0 ? refSpeed : 12
  const cap = maxSpeed && maxSpeed > 0 ? maxSpeed : ref * 3
  const speed = totalSec > 0 ? reps / (totalSec / 60) : ref
  const ratio = Math.min(speed, cap) / ref
  return 1 + (baseMet - 1) * ratio
}

/** 爬楼梯秒/层 → MET 档（两端锚定 Compendium 楼梯条目：快 8.8 / 慢 4.0） */
export function stairsMet(floors: number, times: number, totalSec: number): { met: number; label: string } {
  const layers = floors * times
  if (layers <= 0 || totalSec <= 0) return { met: 0, label: '' }
  const secPerFloor = totalSec / layers
  if (secPerFloor <= 11) return { met: 8.8, label: '快爬' }
  if (secPerFloor <= 16) return { met: 7.0, label: '正常爬' }
  if (secPerFloor <= 22) return { met: 5.5, label: '稍慢' }
  return { met: 4.2, label: '慢爬' }
}

/** 由 MET 与分钟、体重算总消耗与净消耗（kcal，四舍五入） */
export function calcKcal(met: number, minutes: number, weightKg: number): { total: number; net: number } {
  if (minutes <= 0 || weightKg <= 0) return { total: 0, net: 0 }
  const total = Math.round((met * 3.5 * weightKg * minutes) / 200)
  const net = Math.round(((met - 1) * 3.5 * weightKg * minutes) / 200)
  return { total, net }
}

/** 计算单条锻炼记录的净消耗（前端公式，供首页/各组件复用）：
 * 体重优先用记录时快照 bodyWeight，为空回退 fallbackWeight */
export function recordNetKcal(
  r: { reps: number | null; minutes: number | null; seconds: number | null; distance: number | null; floors: number | null; times: number | null; bodyWeight: number | null },
  item: { type: string; baseMet: number; refSpeed: number | null; maxSpeed: number | null },
  fallbackWeight: number | null
): number {
  const w = r.bodyWeight ?? fallbackWeight
  if (!w) return 0
  let met = item.baseMet
  let minutes = 0
  if ((item.type === 'strength' || item.type === 'cardio') && r.reps) {
    minutes = repsEffectiveMinutes(r.reps, item.refSpeed)
    met = speedMet(item.baseMet, r.reps, totalSeconds(r.minutes, r.seconds), item.refSpeed, item.maxSpeed)
  } else if ((item.type === 'walk' || item.type === 'cycling') && r.distance && r.minutes) {
    const kmh = walkSpeedKmh(Number(r.distance), Number(r.minutes))
    met = (item.type === 'cycling' ? cyclingMet(kmh) : walkMet(kmh)).met
    minutes = Number(r.minutes)
  } else if (item.type === 'stairs' && r.floors && r.times) {
    const secTotal = totalSeconds(r.minutes, r.seconds)
    met = stairsMet(Number(r.floors), Number(r.times), secTotal).met
    minutes = secTotal / 60
  } else if (item.type === 'plank' && r.seconds) {
    minutes = r.seconds / 60
  }
  return calcKcal(met, minutes, w).net
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
