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

/** 力量动作额外做功 → 代谢能耗（v2.5.2 物理做功模型，用户确认 2026-09-08）：
 * 动作如「仰卧举哑铃」：大臂平行床面（哑铃在肩旁）举到大臂垂直床面（哑铃在肩上方），
 * 哑铃垂直位移 ≈ 大臂长（按用户臂长默认 0.35m），每次做功 = 负重×g×位移，机械效率约 25%。
 * 每次做功：W = m×g×h = 9.5×9.8×0.35 ≈ 32.6 J ≈ 0.0078 kcal（机械功）；代谢 ≈ ÷0.25 ≈ 0.031 kcal。
 * 本函数返回「额外举起重物的代谢能耗」：W(J) ÷ 4184 ÷ 效率 × 次数，重量越大、次数越多线性增长。
 * 臂力棒（标称磅数作为负重 m）同样适用。 */
export function strengthWorkKcal(
  weightKg: number | null | undefined,
  reps: number | null | undefined,
  liftHeightM = 0.35,
  efficiency = 0.25
): number {
  const m = weightKg && weightKg > 0 ? weightKg : 0
  const n = reps && reps > 0 ? reps : 0
  if (m <= 0 || n <= 0) return 0
  const workJ = m * 9.8 * liftHeightM * n
  return workJ / 4184 / efficiency
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

/** 负重力量动作强度（v2.6.0 强度+做功口径，用户确认 2026-09-08）：
 * 哑铃/臂力棒等有负重力量动作：MET 按速度比定强度（封顶 2.5×参考速度，快做不无限放大），
 * 消耗 = 基础净消耗((MET−1)×3.5×体重×实际分钟÷200) + 做功代谢（负重×9.8×0.35×次数÷25%）。
 * 重量×次数主导做功、速度主导强度，量级与豆包参考（5-6 kcal）对齐：
 * 9.5kg×40次×30s×112kg ≈ 7.4 kcal */

/** 消耗四舍五入保留 1 位小数（单条与汇总统一用，消除浮点尾差如 2453.2000000003） */
export function roundKcal(n: number): number {
  return Math.round(n * 10) / 10
}

/** 计算单条锻炼记录的净消耗（前端公式，供首页/各组件复用）：
 * 体重优先用记录时体重快照 bodyWeight，为空回退 fallbackWeight；
 * - 负重力量动作（哑铃/臂力棒 hasWeight）：做功主导 = 做功代谢 + 实际用时×维持 MET
 * - 其余动作（自重力量/有氧计数/散步/骑行/爬楼/平板）：维持原速度/档位 MET 模型 */
export function recordNetKcal(
  r: { reps: number | null; minutes: number | null; seconds: number | null; distance: number | null; floors: number | null; times: number | null; bodyWeight: number | null; weight?: number | null },
  item: { type: string; baseMet: number; refSpeed: number | null; maxSpeed: number | null; hasWeight?: boolean },
  fallbackWeight: number | null
): number {
  const w = r.bodyWeight ?? fallbackWeight
  if (!w) return 0
  let met = item.baseMet
  let minutes = 0
  if (item.type === 'strength' && item.hasWeight && r.reps) {
    // 强度+做功：速度比定 MET（封顶 2.5×参考，防快做虚高）× 实际用时做基础，加负重做功（重量×次数线性）
    const secTotal = totalSeconds(r.minutes, r.seconds)
    minutes = secTotal / 60
    const ref = item.refSpeed && item.refSpeed > 0 ? item.refSpeed : 12
    const speed = secTotal > 0 ? r.reps / (secTotal / 60) : ref
    const ratio = Math.min(speed, ref * 2.5) / ref
    met = 1 + (item.baseMet - 1) * ratio
    // 基础净消耗不预取整，与做功相加后统一 1 位小数（9.5kg×40次×30s×112kg ≈ 7.4）
    const baseNet = ((met - 1) * 3.5 * w * minutes) / 200
    return Math.round((baseNet + strengthWorkKcal(r.weight, r.reps)) * 10) / 10
  }
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
  return Math.round(calcKcal(met, minutes, w).net * 10) / 10
}

/** 计算单条锻炼记录的总消耗（与 recordNetKcal 对称，展示「总 X kcal」用，1 位小数） */
export function recordTotalKcal(
  r: { reps: number | null; minutes: number | null; seconds: number | null; distance: number | null; floors: number | null; times: number | null; bodyWeight: number | null; weight?: number | null },
  item: { type: string; baseMet: number; refSpeed: number | null; maxSpeed: number | null; hasWeight?: boolean },
  fallbackWeight: number | null
): number {
  const w = r.bodyWeight ?? fallbackWeight
  if (!w) return 0
  let met = item.baseMet
  let minutes = 0
  if (item.type === 'strength' && item.hasWeight && r.reps) {
    // 与 recordNetKcal 对称：速度比定 MET × 实际用时基础总消耗 + 负重做功
    const secTotal = totalSeconds(r.minutes, r.seconds)
    minutes = secTotal / 60
    const ref = item.refSpeed && item.refSpeed > 0 ? item.refSpeed : 12
    const speed = secTotal > 0 ? r.reps / (secTotal / 60) : ref
    const ratio = Math.min(speed, ref * 2.5) / ref
    met = 1 + (item.baseMet - 1) * ratio
    const baseTotal = (met * 3.5 * w * minutes) / 200
    return Math.round((baseTotal + strengthWorkKcal(r.weight, r.reps)) * 10) / 10
  }
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
  return Math.round(calcKcal(met, minutes, w).total * 10) / 10
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
