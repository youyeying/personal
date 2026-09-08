<script setup lang="ts">
/**
 * AuthTitle 认证标题（两字分开布局，第二字主色）
 */
defineProps<{
  /** 标题文字（两字），如 ['登','录'] / ['注','册'] */
  chars: [string, string]
}>()
</script>

<template>
  <h2 class="login-card__auth-title">
    <span class="login-card__auth-title-char">{{ chars[0] }}</span>
    <span class="login-card__auth-title-char login-card__auth-title-char--accent">{{ chars[1] }}</span>
  </h2>
</template>
