/**
 * 基础代谢（纯函数工具）
 * - Katch-McArdle 公式 BMR = 370 + 21.6 × 瘦体重(LBM)，LBM = 体重 × (1 − 体脂率/100)
 * - 每日实际消耗 = BMR × 1.2（久坐基准） + 当日锻炼净消耗
 * - v1.35.0 起废除「活动系数档位」体系（1.375~1.9 会把运动消耗全天摊开导致虚高），
 *   只保留 BMR 作为久坐基准；锻炼消耗单独按当天记录累加
 */

/** 基础代谢（Katch-McArdle）：需体重 kg + 体脂率 %，无体脂率返回 null */
export function calcBmr(weightKg: number | null, bodyFat: number | null): number | null {
  if (weightKg == null || bodyFat == null) return null
  const lbm = weightKg * (1 - bodyFat / 100)
  return Math.round(370 + 21.6 * lbm)
}

/** 久坐基准系数（每日实际消耗的基础乘子） */
export const SEDENTARY_FACTOR = 1.2