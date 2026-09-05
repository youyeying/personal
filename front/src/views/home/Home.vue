<script setup lang="ts">
/**
 * 后台布局壳（组合层，写法参照 login）：
 * - 两栏布局：左侧导航满高 + 右侧（header + 内容区 router-view）
 * - 在容器上写入 --sk-mod（当前模块主色），导航/header/内容随模块平滑过渡
 * - 导航三态：导航栏 / 缩导航栏 / 隐藏（宽度过渡动画）；<600 全屏抽屉
 * - 挂载时若已登录但无用户信息（如刷新页面），拉取 /auth/me 填充昵称/头像
 */
import { onMounted, onUnmounted } from 'vue'
import { useThemeStore } from '@/store/theme'
import { useLayoutStore } from '@/store/layout'
import { useUserStore } from '@/store/user'
import { getAccessToken } from '@/utils/authToken'
import { getMe } from '@/api/auth'
import AppHeader from '@/views/configuration/AppHeader.vue'
import SideNav from '@/views/configuration/SideNav.vue'
import CommandPalette from '@/components/CommandPalette/CommandPalette.vue'

const themeStore = useThemeStore()
const layout = useLayoutStore()
const userStore = useUserStore()

/** 响应视口宽度以切换导航三态 */
function handleResize() {
  layout.handleResize(window.innerWidth)
}

/** 已登录但无用户信息时拉取（解决刷新后昵称/头像丢失） */
async function ensureUserInfo() {
  if (getAccessToken() && !userStore.userInfo) {
    try {
      const res = await getMe()
      userStore.setUserInfo(res.userInfo)
    } catch {
      // 401 已由请求层处理跳登录，其余静默
    }
  }
}

onMounted(() => {
  handleResize()
  window.addEventListener('resize', handleResize)
  ensureUserInfo()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="app-layout" :style="{ '--sk-mod': themeStore.module.accent }">
    <!-- 侧栏：常驻渲染，宽度由 store 控制（宽度过渡实现伸缩/隐藏动画） -->
    <SideNav />

    <!-- 全屏导航栏（仅页面宽度 <600 时，点 header 菜单按钮调起） -->
    <Transition name="drawer">
      <div v-if="layout.isAutoDrawer && layout.drawerOpen" class="app-layout__drawer">
        <div class="app-layout__mask" @click="layout.closeDrawer" />
        <div class="app-layout__panel">
          <SideNav :fullscreen="true" />
        </div>
      </div>
    </Transition>

    <!-- 右侧：header + 内容区（inner 与内容同宽，header 随内容区一起左右滚动） -->
    <section class="app-layout__side">
      <div class="app-layout__inner">
        <AppHeader />
        <main class="app-layout__main">
          <router-view />
        </main>
      </div>
    </section>

    <!-- 命令面板（Ctrl+K 快速跳模块/记一笔，Teleport 至 body） -->
    <CommandPalette />
  </div>
</template>

<style lang="scss">
@use './home';
</style>