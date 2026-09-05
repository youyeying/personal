/**
 * 锻炼消耗公式回归测试（历史 bug 全部固化为用例，改公式必跑）
 * 覆盖：speedMet 负消耗修复 / 速度封顶 / 等效分钟（时长不当乘子修复）/ 散步骑行楼梯档位 / 快照体重优先
 */
import { describe, expect, it } from 'vitest'
import {
  calcKcal,
  cyclingMet,
  recordNetKcal,
  repsEffectiveMinutes,
  speedMet,
  stairsMet,
  totalSeconds,
  walkMet,
  walkSpeedKmh
} from '../exercise'

describe('speedMet 计数类 MET（v1.29 公式：1 + (base−1)×速度比）', () => {
  it('参考速度节奏 = 基础 MET', () => {
    // 28 个/分钟 = 参考速度 28 → 速度比 1 → MET = base
    expect(speedMet(3.8, 28, 60, 28, 80)).toBeCloseTo(3.8, 6)
  })

  it('两倍参考速度 = 1 + (base−1)×2', () => {
    expect(speedMet(3.8, 56, 60, 28, 80)).toBeCloseTo(6.6, 6)
  })

  it('速度超世界纪录按纪录封顶（防录入错误爆炸）', () => {
    // 10 秒 100 个 = 600 个/分，远超纪录 80 → 按封顶 80/28 算
    expect(speedMet(3.8, 100, 10, 28, 80)).toBeCloseTo(1 + 2.8 * (80 / 28), 6)
  })

  it('极慢速度 MET 恒 ≥ 1（历史 bug：过原点线性曾算出负消耗）', () => {
    const met = speedMet(3.8, 1, 600, 28, 80)
    expect(met).toBeGreaterThanOrEqual(1)
  })

  it('无时长旧数据按参考速度（正常节奏）计算', () => {
    expect(speedMet(3.8, 28, 0, 28, 80)).toBeCloseTo(3.8, 6)
  })
})

describe('repsEffectiveMinutes 等效分钟（总量由个数主导）', () => {
  it('等效分钟 = 总个数 ÷ 参考速度，与用时无关（时长不当乘子修复）', () => {
    expect(repsEffectiveMinutes(100, 28)).toBeCloseTo(100 / 28, 6)
  })

  it('空个数或非法参考速度兜底', () => {
    expect(repsEffectiveMinutes(null, 28)).toBe(0)
    expect(repsEffectiveMinutes(24, null)).toBe(2) // refSpeed 缺省 12
  })
})

describe('散步 / 骑行速度档位', () => {
  it('平均速度 = 距离 ÷ 分钟 × 60', () => {
    expect(walkSpeedKmh(3, 60)).toBe(3)
    expect(walkSpeedKmh(4.5, 90)).toBeCloseTo(3, 6)
  })

  it('散步档位（区间左闭右开）', () => {
    expect(walkMet(3.0).met).toBe(2.8) // 慢走
    expect(walkMet(3.2).met).toBe(3.0) // 3.2 落「正常走」档
    expect(walkMet(4.0).met).toBe(3.5) // 4.0 落「稍快走」档
    expect(walkMet(5.7).met).toBe(5.0) // 暴走
  })

  it('骑行档位对齐 Compendium 2024', () => {
    expect(cyclingMet(15).met).toBe(4.0) // 休闲骑
    expect(cyclingMet(20).met).toBe(8.0) // 中等骑
    expect(cyclingMet(32).met).toBe(16.8) // 竞速骑
  })
})

describe('爬楼梯按秒/层分档', () => {
  it('快爬（≤11s/层）= 8.8', () => {
    expect(stairsMet(10, 2, 120)).toEqual({ met: 8.8, label: '快爬' }) // 6s/层
  })

  it('慢爬（>22s/层）= 4.2', () => {
    expect(stairsMet(5, 1, 300)).toEqual({ met: 4.2, label: '慢爬' }) // 60s/层
  })

  it('非法入参返回 0', () => {
    expect(stairsMet(0, 2, 100).met).toBe(0)
    expect(stairsMet(5, 0, 100).met).toBe(0)
  })
})

describe('calcKcal 总/净消耗', () => {
  it('净消耗 = (MET−1)×3.5×体重×分钟÷200，恒 < 总消耗', () => {
    const { total, net } = calcKcal(3.8, 30, 80)
    expect(total).toBe(Math.round((3.8 * 3.5 * 80 * 30) / 200))
    expect(net).toBe(Math.round((2.8 * 3.5 * 80 * 30) / 200))
    expect(net).toBeLessThan(total)
  })

  it('零时长返回 0', () => {
    expect(calcKcal(5, 0, 80)).toEqual({ total: 0, net: 0 })
  })
})

describe('recordNetKcal 单条记录净消耗', () => {
  const baseRec = { reps: null, minutes: null, seconds: null, distance: null, floors: null, times: null, bodyWeight: null }

  it('体重快照优先于回退体重（历史消耗不随当前体重变）', () => {
    const a = recordNetKcal(
      { ...baseRec, distance: 3, minutes: 60, bodyWeight: 100 },
      { type: 'walk', baseMet: 3.0, refSpeed: null, maxSpeed: null },
      80
    )
    const b = recordNetKcal(
      { ...baseRec, distance: 3, minutes: 60, bodyWeight: null },
      { type: 'walk', baseMet: 3.0, refSpeed: null, maxSpeed: null },
      80
    )
    // 快照 100kg 的消耗按 100 算（净 = 1.8×3.5×100×60÷200 = 189）
    expect(a).toBe(189)
    // 无快照回退 80kg（净 = 151）
    expect(b).toBe(151)
    expect(a).toBeGreaterThan(b)
  })

  it('散步：3km/60min → 慢走 2.8 MET', () => {
    expect(
      recordNetKcal(
        { ...baseRec, distance: 3, minutes: 60, bodyWeight: 80 },
        { type: 'walk', baseMet: 3.0, refSpeed: null, maxSpeed: null },
        null
      )
    ).toBe(151) // (2.8−1)×3.5×80×60÷200
  })

  it('骑行：20km/60min → 中等骑 8.0 MET', () => {
    expect(
      recordNetKcal(
        { ...baseRec, distance: 20, minutes: 60, bodyWeight: 80 },
        { type: 'cycling', baseMet: 6.8, refSpeed: null, maxSpeed: null },
        null
      )
    ).toBe(588) // (8−1)×3.5×80×60÷200
  })

  it('爬楼梯：10层×2次/120秒 → 快爬 8.8 MET', () => {
    expect(
      recordNetKcal(
        { ...baseRec, floors: 10, times: 2, seconds: 120, bodyWeight: 80 },
        { type: 'stairs', baseMet: 8.0, refSpeed: null, maxSpeed: null },
        null
      )
    ).toBe(22) // (8.8−1)×3.5×80×2÷200
  })

  it('平板支撑：按秒记时长', () => {
    expect(
      recordNetKcal(
        { ...baseRec, seconds: 120, bodyWeight: 80 },
        { type: 'plank', baseMet: 2.8, refSpeed: null, maxSpeed: null },
        null
      )
    ).toBe(5) // (2.8−1)×3.5×80×2÷200 = 5.04
  })

  it('力量计数：等效分钟 + 速度 MET 组合', () => {
    // 100 个、60 秒、ref 28、纪录 80：等效分钟 = 100/28，速度封顶 80 → MET = 1+2.8×(80/28)
    const net = recordNetKcal(
      { ...baseRec, reps: 100, seconds: 60, bodyWeight: 113.2 },
      { type: 'strength', baseMet: 3.8, refSpeed: 28, maxSpeed: 80 },
      null
    )
    const expected = Math.round((1 + 2.8 * (80 / 28) - 1) * 3.5 * 113.2 * (100 / 28) / 200)
    expect(net).toBe(expected)
  })
})

describe('totalSeconds 秒数优先', () => {
  it('seconds 优先于 minutes', () => {
    expect(totalSeconds(5, 90)).toBe(90)
    expect(totalSeconds(5, null)).toBe(300)
    expect(totalSeconds(null, null)).toBe(0)
  })
})
