/**
 * 基础代谢 Katch-McArdle 公式测试
 * BMR = 370 + 21.6 × 瘦体重，LBM = 体重 × (1 − 体脂率/100)
 */
import { describe, expect, it } from 'vitest'
import { calcBmr, SEDENTARY_FACTOR } from '../activity'

describe('calcBmr（Katch-McArdle）', () => {
  it('标准计算：113.2kg / 34.5% 体脂', () => {
    // LBM = 113.2 × 0.655 = 74.146 → 370 + 21.6 × 74.146 ≈ 1972
    expect(calcBmr(113.2, 34.5)).toBe(1972)
  })

  it('零体脂（纯体重）', () => {
    expect(calcBmr(80, 0)).toBe(Math.round(370 + 21.6 * 80))
  })

  it('缺体重或体脂返回 null（首页/统计页据此回退展示）', () => {
    expect(calcBmr(null, 30)).toBeNull()
    expect(calcBmr(80, null)).toBeNull()
  })

  it('久坐基准系数固定 1.2（废除活动系数档位后唯一乘子）', () => {
    expect(SEDENTARY_FACTOR).toBe(1.2)
  })
})
