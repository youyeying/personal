/**
 * 学习模块共享常量/工具（记一笔 与 历史 子页组件共用）
 */
import { markRaw } from 'vue'
import { BookOpen, Clapperboard, GraduationCap, Wrench, Sparkles } from '@lucide/vue'

/** 学习方式（Lucide 图标，与分类图标体系一致） */
export const WAYS = [
  { key: '阅读', icon: markRaw(BookOpen) },
  { key: '视频', icon: markRaw(Clapperboard) },
  { key: '课程', icon: markRaw(GraduationCap) },
  { key: '实践', icon: markRaw(Wrench) },
  { key: '其他', icon: markRaw(Sparkles) }
] as const

/** 分钟 → 时/分（等宽分段展示用） */
export function durationParts(min: number | null | undefined): { h: number; m: number } {
  const v = min ?? 0
  return { h: Math.floor(v / 60), m: v % 60 }
}