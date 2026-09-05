<script setup lang="ts">
/**
 * 命令面板（Ctrl+K / Cmd+K 唤起）
 * - 三组命令：快捷动作（记一笔/体重打卡…）/ 模块导航（全部页面）/ 系统（切换白天黑夜）
 * - 搜索：名称 + 描述 includes 过滤；键盘 ↑↓ 循环选择、Enter 执行、Esc 关闭
 * - 挂载于 Home 布局壳，全局监听快捷键；书卷气样式（纸面弹层 + 耳语阴影 + 0.15s 淡入下移）
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { PenLine, Weight, BookOpen, Utensils, NotebookPen, Moon, Sun, CornerDownLeft, Search } from '@lucide/vue'
import type { Component } from 'vue'
import { MODULES } from '@/store/theme'
import { isDarkTheme, toggleDarkTheme } from '@/utils/theme'

interface Command {
  key: string
  label: string
  desc: string
  group: '快捷' | '导航' | '系统'
  icon: Component
  run: () => void
}

const router = useRouter()
const open = ref(false)
const keyword = ref('')
const activeIndex = ref(0)
const inputEl = ref<HTMLInputElement | null>(null)

/* ---------- 命令清单 ---------- */

/** 快捷动作：直达高频记录入口（目标模块的默认子页即录入表单） */
const quickActions: Command[] = [
  { key: 'q-expense', label: '记一笔', desc: '记支出 / 收入', group: '快捷', icon: PenLine, run: () => router.push('/expense') },
  { key: 'q-weight', label: '体重打卡', desc: '记体重 / 体脂', group: '快捷', icon: Weight, run: () => router.push('/health') },
  { key: 'q-food', label: '记饮食', desc: '记今天吃了什么', group: '快捷', icon: Utensils, run: () => router.push('/food') },
  { key: 'q-learn', label: '记学习', desc: '记今天学了什么', group: '快捷', icon: BookOpen, run: () => router.push('/learn') },
  { key: 'q-note', label: '写今日总结', desc: '一句话 + 心情', group: '快捷', icon: NotebookPen, run: () => router.push('/daily-note') }
]

/** 模块导航：沿用侧边栏 MODULES（名称/描述/图标一致） */
const navCommands: Command[] = MODULES.map((m) => ({
  key: 'nav-' + m.key,
  label: m.name,
  desc: m.desc,
  group: '导航' as const,
  icon: m.icon,
  run: () => router.push(m.path)
}))

/** 当前暗色态（面板内展示用；切换后即时更新） */
const isDarkRef = ref(isDarkTheme())

/** 系统命令：切换主题（label 随当前模式变化） */
const themeCommand = computed<Command>(() => ({
  key: 'sys-theme',
  label: isDarkRef.value ? '切换到白天模式' : '切换到黑夜模式',
  desc: '明暗主题一键切换',
  group: '系统',
  icon: isDarkRef.value ? Sun : Moon,
  run: () => { isDarkRef.value = toggleDarkTheme() }
}))

/** 全量命令：快捷 → 导航 → 系统 */
const allCommands = computed<Command[]>(() => [...quickActions, ...navCommands, themeCommand.value])

/** 过滤后的命令（名称 + 描述匹配；空关键字显示全部） */
const filtered = computed<Command[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return allCommands.value
  return allCommands.value.filter((c) =>
    c.label.toLowerCase().includes(kw) || c.desc.toLowerCase().includes(kw))
})

/** 分组视图（保持组内顺序，过滤后空组不显示） */
const grouped = computed(() => {
  const groups: { title: string; items: Command[] }[] = []
  for (const g of ['快捷', '导航', '系统'] as const) {
    const items = filtered.value.filter((c) => c.group === g)
    if (items.length) groups.push({ title: g, items })
  }
  return groups
})

/** 扁平列表（键盘 activeIndex 按此走） */
const flat = computed(() => grouped.value.flatMap((g) => g.items))

/* ---------- 交互 ---------- */

function show() {
  keyword.value = ''
  activeIndex.value = 0
  isDarkRef.value = isDarkTheme()
  open.value = true
  nextTick(() => inputEl.value?.focus())
}

function hide() {
  open.value = false
}

function execute(c: Command) {
  hide()
  c.run()
}

function onKeydown(e: KeyboardEvent) {
  // Ctrl+K / Cmd+K：开面板；已开时再按 = 关
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    if (open.value) hide()
    else show()
    return
  }
  if (!open.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    hide()
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = flat.value.length ? (activeIndex.value + 1) % flat.value.length : 0
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = flat.value.length ? (activeIndex.value - 1 + flat.value.length) % flat.value.length : 0
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const c = flat.value[activeIndex.value]
    if (c) execute(c)
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="cp">
      <div v-if="open" class="cp" @click.self="hide">
        <div class="cp__panel" role="dialog" aria-label="命令面板">
          <!-- 搜索行 -->
          <div class="cp__search">
            <span class="cp__search-icon"><component :is="Search" :size="15" /></span>
            <input
              ref="inputEl"
              v-model="keyword"
              class="cp__input"
              type="text"
              placeholder="搜索模块 / 动作…（↑↓ 选择 · Enter 确认 · Esc 关闭）"
              @input="activeIndex = 0"
            >
          </div>

          <!-- 结果列表 -->
          <div class="cp__list">
            <template v-for="g in grouped" :key="g.title">
              <div class="cp__group">{{ g.title }}</div>
              <button
                v-for="c in g.items" :key="c.key"
                class="cp__item"
                :class="{ 'is-active': flat.indexOf(c) === activeIndex }"
                type="button"
                @mouseenter="activeIndex = flat.indexOf(c)"
                @click="execute(c)"
              >
                <span class="cp__item-icon"><component :is="c.icon" :size="16" /></span>
                <span class="cp__item-body">
                  <span class="cp__item-label">{{ c.label }}</span>
                  <span class="cp__item-desc">{{ c.desc }}</span>
                </span>
                <span class="cp__item-enter" :class="{ 'is-on': flat.indexOf(c) === activeIndex }">
                  <component :is="CornerDownLeft" :size="13" />
                </span>
              </button>
            </template>
            <div v-if="!flat.length" class="cp__empty">没有匹配「{{ keyword }}」的命令</div>
          </div>

          <!-- 底部快捷键提示 -->
          <div class="cp__foot">
            <span><kbd>Ctrl</kbd> + <kbd>K</kbd> 开关</span>
            <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
            <span><kbd>Enter</kbd> 执行</span>
            <span><kbd>Esc</kbd> 关闭</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style lang="scss" scoped>
@use './commandPalette';
</style>
