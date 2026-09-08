<script setup lang="ts">
/**
 * 开发端顶部 header（与用户端同结构，去掉头像/个人中心）：
 * 左：小屏菜单按钮 + 当前模块名/说明；右：开发账号名 + 退出登录下拉
 */
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Menu, UserFilled } from '@element-plus/icons-vue'
import { useThemeStore } from '@/store/theme'
import { useUserStore } from '@/store/user'
import { useLayoutStore } from '@/store/layout'
import { logout as logoutApi } from '@/api/auth'

const router = useRouter()
const themeStore = useThemeStore()
const userStore = useUserStore()
const layout = useLayoutStore()

/** 头像下拉命令（开发端仅退出） */
async function onCommand(cmd: string) {
  if (cmd === 'logout') {
    // 先撤销服务端会话 + 清 Cookie，再清理前端登录态（失败也继续本地登出）
    try {
      await logoutApi()
    } catch {
      /* 忽略：仍执行本地登出 */
    }
    userStore.clearLocalAuth()
    ElMessage.success('已退出登录')
    router.replace('/admin/login')
  }
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__info">
      <!-- 左上角菜单按钮：隐藏导航栏→恢复导航栏；页面<600→打开全屏导航栏 -->
      <button
        v-if="layout.showHeaderMenu"
        class="app-header__menu-btn"
        type="button"
        :aria-label="layout.isAutoDrawer ? '打开全屏导航' : '恢复导航栏'"
        @click="layout.onHeaderMenu"
      >
        <el-icon><Menu /></el-icon>
      </button>
      <h1 class="app-header__title">{{ themeStore.module.name }}</h1>
      <p class="app-header__desc">{{ themeStore.module.desc }}</p>
    </div>

    <!-- 右侧：开发账号名 + 退出登录下拉 -->
    <div class="app-header__right">
      <el-dropdown trigger="click" placement="bottom-end" popper-class="app-header__dd" @command="onCommand">
        <div class="app-header__user">
          <span class="app-header__avatar">
            <el-icon class="app-header__avatar-icon"><UserFilled /></el-icon>
          </span>
          <span class="app-header__nick">{{ userStore.userInfo?.nickname || userStore.userInfo?.username || '开发' }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style lang="scss" scoped>
@use './appheader';
</style>
