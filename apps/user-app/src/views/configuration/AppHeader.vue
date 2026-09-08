<script setup lang="ts">
/**
 * 公共布局 · 顶部 header
 * 左：小屏菜单按钮 + 当前模块名/说明；右：头像（从 Pinia userStore 取）+ 昵称
 * 点击头像区域弹出下拉：个人中心 / 退出登录
 * 头像来源：单一可信源 userStore.avatarUrl（Profile 保存时 setUserInfo 即同步响应式更新）
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

/** 头像下拉命令 */
async function onCommand(cmd: string) {
  if (cmd === 'profile') {
    router.push('/profile')
  } else if (cmd === 'logout') {
    // 先撤销服务端会话 + 清 Cookie，再清理前端登录态（失败也继续本地登出）
    try {
      await logoutApi()
    } catch {
      /* 忽略：仍执行本地登出 */
    }
    userStore.clearLocalAuth()
    ElMessage.success('已退出登录')
    router.replace('/login')
  }
}

/** 头像加载失败：隐藏 <img>，让外层 fallback icon 顶上去 */
function onAvatarError(e: Event) {
  const t = e.target as HTMLImageElement | null
  if (t) t.style.display = 'none'
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

    <!-- 右侧：头像/昵称下拉 -->
    <div class="app-header__right">
      <el-dropdown trigger="click" placement="bottom-end" popper-class="app-header__dd" @command="onCommand">
        <div class="app-header__user">
          <!-- 头像：有上传图用图；无图/加载失败兜底 UserFilled icon -->
          <span class="app-header__avatar">
            <img
              v-if="userStore.avatarUrl"
              :src="userStore.avatarUrl"
              alt="头像"
              class="app-header__avatar-img"
              @error="onAvatarError"
            />
            <el-icon v-else class="app-header__avatar-icon"><UserFilled /></el-icon>
          </span>
          <span class="app-header__nick">{{ userStore.userInfo?.nickname || '我' }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">个人中心</el-dropdown-item>
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