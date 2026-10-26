<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearAuth, getUser, hasAnyRole } from '../utils/storage'

const route = useRoute()
const router = useRouter()
const user = computed(() => getUser())
const navigation = computed(() => [
  { label: '工作台', name: 'home', icon: '⌂' },
  ...(hasAnyRole(['USER']) ? [{ label: '我的工单', name: 'user-home', icon: '▣' }] : []),
  ...(hasAnyRole(['AGENT']) ? [{ label: '客服工作台', name: 'agent-home', icon: '◈' }] : []),
  ...(hasAnyRole(['ADMIN']) ? [{ label: '管理工作台', name: 'admin-home', icon: '◆' }] : []),
])

function logout() {
  clearAuth()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}
</script>

<template>
  <div class="app-shell">
    <aside class="side-panel">
      <div class="brand-mark"><span>ST</span><div><strong>智能工单</strong><small>SMART TICKET</small></div></div>
      <nav class="side-nav" aria-label="主导航">
        <RouterLink v-for="item in navigation" :key="item.name" :to="{ name: item.name }" class="nav-item" :class="{ active: route.name === item.name }">
          <span class="nav-icon">{{ item.icon }}</span>{{ item.label }}
        </RouterLink>
      </nav>
      <div class="side-foot">AI 分类已就绪<br /><span>安全 · 清晰 · 可追踪</span></div>
    </aside>
    <main class="main-panel">
      <header class="top-bar">
        <div><span class="eyebrow">TICKET OPERATIONS</span><h1>工作台</h1></div>
        <div class="user-menu"><div class="avatar">{{ user?.username?.slice(0, 1)?.toUpperCase() }}</div><div><strong>{{ user?.username }}</strong><small>{{ user?.roles?.join(' · ') }}</small></div><el-button text @click="logout">退出</el-button></div>
      </header>
      <section class="content-area"><RouterView /></section>
    </main>
  </div>
</template>
