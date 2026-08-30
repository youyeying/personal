<script setup lang="ts">
/**
 * RegisterForm 注册表单
 * 职责：注册数据 + 校验 + 提交请求 + 成功/失败通知
 * 成功时 emit register-success（携带注册的用户名/密码，父级预填登录表单）
 */
import { computed, ref } from 'vue'
import { ElNotification } from 'element-plus'
import FloatingInput from '@/components/FloatingInput/FloatingInput.vue'
import BaseButton from '@/components/BaseButton/BaseButton.vue'
import AuthTitle from './AuthTitle.vue'
// 公共表单校验
import { validateUsername, validatePassword, validatePhone } from '@/utils/validators'
// 认证接口
import { register as registerApi } from '@/api/auth'

const emit = defineEmits<{
  (e: 'register-success', payload: { username: string; password: string }): void
  (e: 'switch-login'): void
}>()

/** 注册表单数据 */
const form = ref({ username: '', phone: '', password: '' })

/** 格式错误（前端即时校验，字段下方显示） */
const usernameError = computed(() => validateUsername(form.value.username))
const phoneError = computed(() => validatePhone(form.value.phone))
const passwordError = computed(() => validatePassword(form.value.password))

/** 提交 loading */
const loading = ref(false)

/** 提交注册 */
async function handleSubmit() {
  if (usernameError.value || phoneError.value || passwordError.value) return
  loading.value = true
  try {
    await registerApi({
      username: form.value.username,
      phone: form.value.phone,
      password: form.value.password
    })
    // 成功：通知 + 交事件给父级（父级预填登录）
    ElNotification({
      title: '注册成功',
      message: '账号已创建，请登录',
      type: 'success',
      duration: 2000
    })
    emit('register-success', { username: form.value.username, password: form.value.password })
  } catch (e) {
    const err = e as Error
    ElNotification({
      title: '注册失败',
      message: err.message || '注册失败，请重试',
      type: 'error',
      duration: 3000
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login-card login-card--register" aria-label="注册区域">
    <div class="login-card__auth-content">
      <AuthTitle :chars="['注', '册']" />

      <form class="login-card__auth-form" @submit.prevent="handleSubmit">
        <div class="login-card__auth-field">
          <FloatingInput v-model="form.username" label="用户名" :error="usernameError" />
        </div>
        <div class="login-card__auth-field">
          <FloatingInput v-model="form.phone" label="手机号" :error="phoneError" />
        </div>
        <div class="login-card__auth-field">
          <FloatingInput v-model="form.password" label="密码" type="password" :error="passwordError" />
        </div>

        <BaseButton type="submit" class="login-card__submit" text="注册" :loading="loading">
          <template #icon>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
              <circle cx="9" cy="7" r="4" />
              <path d="M19 8v6M22 11h-6" />
            </svg>
          </template>
        </BaseButton>
      </form>

      <button class="login-card__switch-link" type="button" @click="emit('switch-login')">
        已有账号？去登录
      </button>
    </div>
  </section>
</template>
