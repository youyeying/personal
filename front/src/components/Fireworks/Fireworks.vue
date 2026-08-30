<script setup lang="ts">
/**
 * Fireworks 公共烟花组件
 * 登录成功 / 庆祝场景使用
 * 特性：
 * - Canvas 粒子动画，一次性播放后自动消失
 * - 支持多种烟花形状：circle(圆球) / heart(爱心) / star(星形)
 * - 可配置：颜色 / 粒子数 / 发射位置 / 是否全屏
 * - 通过 ref 调用 play() 触发
 */
import { nextTick, onBeforeUnmount, ref } from 'vue'

/** 烟花粒子 */
interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  life: number
  maxLife: number
  color: string
  size: number
  gravity: number
}

const props = withDefaults(
  defineProps<{
    /** 烟花形状：circle / heart / star */
    type?: 'circle' | 'heart' | 'star'
    /** 颜色数组（随机取用） */
    colors?: string[]
    /** 粒子数 */
    count?: number
    /** 是否全屏播放（默认 true） */
    fullscreen?: boolean
    /** 动画时长（毫秒） */
    duration?: number
  }>(),
  {
    type: 'circle',
    colors: () => ['#a8765a', '#e0a96d', '#3d7a55', '#e8d5b7', '#8a5a44'],
    count: 60,
    fullscreen: true,
    duration: 1600
  }
)

const emit = defineEmits<{
  (e: 'done'): void
}>()

/** canvas 引用 */
const canvasRef = ref<HTMLCanvasElement | null>(null)

/** 是否正在播放 */
const playing = ref(false)
/** 是否渲染（播放中显示，结束隐藏） */
const visible = ref(false)

/** canvas 尺寸（全屏跟随窗口） */
const canvasSize = ref({ width: 300, height: 300 })

let ctx: CanvasRenderingContext2D | null = null
let particles: Particle[] = []
let animFrame = 0
let timer = 0

/** 随机取颜色 */
function pickColor(): string {
  return props.colors[Math.floor(Math.random() * props.colors.length)]
}

/**
 * 生成某形状的粒子偏移量
 * circle: 均匀分布圆
 * heart: 心形参数方程
 * star: 五角星轮廓
 */
function shapeOffset(radius: number): { x: number; y: number } {
  if (props.type === 'heart') {
    // 心形参数方程 t ∈ [0, 2π]
    const t = Math.random() * Math.PI * 2
    const s = Math.sin(t)
    return {
      x: 16 * Math.pow(s, 3) * (radius / 16),
      y: (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) * (radius / 16)
    }
  }
  if (props.type === 'star') {
    // 五角星：外圈 10 个角点，随机内/外半径
    const angle = (Math.random() * Math.PI * 2)
    const spike = Math.random() < 0.5 // 外角/内角
    const r = spike ? radius : radius * 0.45
    return { x: Math.cos(angle) * r, y: Math.sin(angle) * r }
  }
  // circle: 均匀分布
  const angle = Math.random() * Math.PI * 2
  const r = Math.sqrt(Math.random()) * radius
  return { x: Math.cos(angle) * r, y: Math.sin(angle) * r }
}

/**
 * 播放一次烟花
 * @param x 发射 x（全屏模式可空，默认屏幕中心）
 * @param y 发射 y（可空，默认屏幕中心偏上）
 */
function play(x?: number, y?: number) {
  // 先显示组件（canvas 通过 v-if 渲染），再获取实例
  visible.value = true
  playing.value = true

  nextTick(() => {
    const canvas = canvasRef.value
    if (!canvas) return
    ctx = canvas.getContext('2d')

    // 重置粒子
    particles = []

    // 全屏模式同步 canvas 尺寸
    if (props.fullscreen) {
      canvas.width = window.innerWidth
      canvas.height = window.innerHeight
      canvasSize.value = { width: canvas.width, height: canvas.height }
    }

    // 定位（默认屏幕中心偏上）
    const cx = x ?? window.innerWidth / 2
    const cy = y ?? window.innerHeight * 0.4

    // 生成粒子
    const radius = Math.min(window.innerWidth, window.innerHeight) * 0.12
    for (let i = 0; i < props.count; i++) {
      const offset = shapeOffset(radius)
      particles.push({
        x: cx + offset.x,
        y: cy + offset.y,
        vx: offset.x * 0.02,
        vy: offset.y * 0.02,
        life: 0,
        maxLife: props.duration,
        color: pickColor(),
        size: 2 + Math.random() * 2,
        gravity: 0.02
      })
    }

    // 动画循环
    animFrame = requestAnimationFrame(animate)
    // 定时结束
    timer = window.setTimeout(() => {
      stop()
    }, props.duration + 200)
  })
}

/** 动画帧 */
function animate(timestamp: number) {
  if (!ctx || !canvasRef.value) return
  const canvas = canvasRef.value
  ctx.clearRect(0, 0, canvas.width, canvas.height)

  const now = performance.now()
  let alive = false

  for (const p of particles) {
    p.life += 16
    if (p.life >= p.maxLife) continue

    alive = true
    // 重力
    p.vy += p.gravity
    p.x += p.vx
    p.y += p.vy

    // 淡出
    const alpha = 1 - p.life / p.maxLife
    // 拖尾：小圆点
    ctx.beginPath()
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2)
    ctx.fillStyle = p.color
    ctx.globalAlpha = alpha
    ctx.fill()
  }

  ctx.globalAlpha = 1

  if (alive) {
    animFrame = requestAnimationFrame(animate)
  }
}

/** 停止动画 */
function stop() {
  if (timer) window.clearTimeout(timer)
  cancelAnimationFrame(animFrame)
  playing.value = false
  visible.value = false
  if (ctx && canvasRef.value) {
    ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  }
  emit('done')
}

onBeforeUnmount(() => {
  if (timer) window.clearTimeout(timer)
  cancelAnimationFrame(animFrame)
})

defineExpose({ play, stop })
</script>

<template>
  <!-- 全屏烟花层：覆盖页面，指针事件穿透 -->
  <div
    v-if="visible"
    class="fireworks"
    :class="{ 'fireworks--fullscreen': fullscreen }"
  >
    <canvas
      ref="canvasRef"
      class="fireworks__canvas"
      :width="canvasSize.width"
      :height="canvasSize.height"
    />
  </div>
</template>

<style lang="scss">
@use './fireworks';
</style>
