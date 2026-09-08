<script setup lang="ts">
/**
 * 开发端登录页（/admin/login）
 * 调 /api/auth/admin/login（admin_user 表，userType=2）
 * 成功：存 accessToken（内存）→ 跳首页（带 redirect 回跳）
 */
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElNotification } from 'element-plus'
import FloatingInput from '@/components/FloatingInput/FloatingInput.vue'
import BaseButton from '@/components/BaseButton/BaseButton.vue'
import AuthTitle from '@/views/login/components/AuthTitle.vue'
import { validateUsername, validatePassword } from '@/utils/validators'
import { adminLogin } from '@/api/auth'
import { useUserStore } from '@/store/user'
import { setAccessToken } from '@/utils/authToken'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const form = ref({ username: '', password: '' })
const loading = ref(false)

const usernameError = computed(() => validateUsername(form.value.username))
const passwordError = computed(() => validatePassword(form.value.password))

async function handleSubmit() {
  if (usernameError.value || passwordError.value) return
  loading.value = true
  try {
    const res = await adminLogin({
      username: form.value.username,
      password: form.value.password
    })
    setAccessToken(res.accessToken)
    userStore.setUserInfo(res.userInfo)
    ElNotification({
      title: '登录成功',
      message: `欢迎回来，${res.userInfo.nickname || res.userInfo.username}`,
      type: 'success',
      duration: 2000
    })
    const redirect = route.query.redirect as string | undefined
    router.push(redirect || '/')
  } catch (e) {
    const err = e as Error
    ElNotification({
      title: '登录失败',
      message: err.message || '登录失败，请重试',
      type: 'error',
      duration: 3000
    })
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="adl-login">
    <section class="adl-login__card" aria-label="开发账号登录">
      <div class="adl-login__auth-content">
        <AuthTitle :chars="['登', '录']" />

        <form class="adl-login__auth-form" @submit.prevent="handleSubmit">
          <div class="adl-login__auth-field">
            <FloatingInput v-model="form.username" label="用户名" autocomplete="off" :error="usernameError" />
          </div>
          <div class="adl-login__auth-field">
            <FloatingInput v-model="form.password" label="密码" type="password" autocomplete="new-password" :error="passwordError" />
          </div>

          <BaseButton type="submit" class="adl-login__submit" text="登录" :loading="loading" />
        </form>
      </div>
    </section>
  </div>
</template>

<style lang="scss">
@use './adminLogin';
</style>
