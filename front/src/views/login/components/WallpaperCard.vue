<script setup lang="ts">
/**
 * WallpaperCard 壁纸卡
 * 纯展示组件：背景图 + 暗化蒙层 + 品牌文字
 */
// 壁纸图（本地静态资源）
import authBg from '@/assets/wallpaper/auth-bg.jpg'
</script>

<template>
  <section class="login-card login-card--wallpaper" aria-label="壁纸展示">
    <!-- 壁纸底图 -->
    <img class="login-card__bg" :src="authBg" alt="" aria-hidden="true" />
    <!-- 暗化蒙层：保证登录/注册内容与壁纸的对比度 -->
    <div class="login-card__overlay" aria-hidden="true"></div>
    <!-- 壁纸卡上的品牌文字（氛围层） -->
    <div class="login-card__brand">
      <p class="login-card__brand-title">个人记录</p>
      <p class="login-card__brand-sub">记录每一天的生活</p>
    </div>
  </section>
</template>
