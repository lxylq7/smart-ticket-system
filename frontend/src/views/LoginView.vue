<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { showApiError } from '../api/http'
import { getRoleHomeRoute, hasAnyRole, saveAuth } from '../utils/storage'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const loginData = await login(form)
    saveAuth(loginData)
    ElMessage.success('登录成功')
    router.replace(resolvePostLoginRoute(loginData.user))
  } catch (error) {
    showApiError(error, '登录失败，请检查账号和密码')
  } finally {
    loading.value = false
  }
}

function resolvePostLoginRoute(user) {
  const redirect = route.query.redirect
  if (typeof redirect !== 'string' || !redirect.startsWith('/') || redirect.startsWith('//')) {
    return getRoleHomeRoute(user)
  }

  const resolved = router.resolve(redirect)
  if (resolved.name === 'login' || resolved.name === 'forbidden') return getRoleHomeRoute(user)
  if (resolved.meta.roles && !hasAnyRole(resolved.meta.roles)) return getRoleHomeRoute(user)
  return { path: resolved.fullPath }
}
</script>

<template>
  <main class="login-page">
    <div class="login-decoration"><span class="orb orb-one"></span><span class="orb orb-two"></span><div class="decoration-copy"><span class="eyebrow light">SMART TICKET SYSTEM</span><h1>让每一张工单<br /><em>都有清晰的答案。</em></h1><p>统一管理问题、进度与协作，让服务响应更快一步。</p></div></div>
    <section class="login-card-wrap"><div class="login-card"><div class="mobile-brand"><span>ST</span> 智能工单</div><span class="eyebrow">WELCOME BACK</span><h2>欢迎回来</h2><p class="login-subtitle">登录你的工单工作台</p><el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit"><el-form-item label="用户名" prop="username"><el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username" /></el-form-item><el-form-item label="密码" prop="password"><el-input v-model="form.password" size="large" type="password" show-password placeholder="请输入密码" autocomplete="current-password" @keyup.enter="submit" /></el-form-item><el-button class="login-button" type="primary" size="large" :loading="loading" native-type="submit">进入工作台 <span>→</span></el-button></el-form><p class="login-hint">账号权限由后端认证决定 · USER / AGENT / ADMIN</p></div></section>
  </main>
</template>
