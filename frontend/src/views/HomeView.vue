<script setup>
import { computed } from 'vue'
import { getUser } from '../utils/storage'

const user = computed(() => getUser())
</script>

<template>
  <div class="page-intro"><div><span class="eyebrow">OVERVIEW</span><h2>你好，{{ user?.username }}</h2><p>基础前端已经连接到后端认证服务，业务页面将在后续阶段按模块加入。</p></div><div class="status-pill"><span></span>系统运行中</div></div>
  <div class="empty-grid"><article><span class="card-number">01</span><h3>工单中心</h3><p>工单创建、查询与处理页面将在下一阶段接入真实接口。</p></article><article><span class="card-number">02</span><h3>智能分类</h3><p>AI 分类结果会通过后端工单接口呈现，不在前端写死业务数据。</p></article><article><span class="card-number">03</span><h3>协作与权限</h3><p>页面入口按角色展示，最终权限始终由后端校验。</p></article></div>
</template>
