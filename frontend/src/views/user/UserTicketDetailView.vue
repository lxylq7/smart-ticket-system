<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, InfoFilled } from '@element-plus/icons-vue'
import { getTicket, updateTicket } from '../../api/tickets'
import { showApiError } from '../../api/http'
import {
  AI_STATUS_LABELS,
  TICKET_CATEGORY_LABELS,
  TICKET_PRIORITY_LABELS,
  TICKET_STATUS_LABELS,
  formatDate,
  labelOf,
} from '../../utils/ticket'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const ticket = ref(null)
const formRef = ref()
const form = reactive({ title: '', description: '' })
const rules = {
  title: [{ required: true, message: '请输入工单标题', trigger: 'blur' }, { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }],
  description: [{ required: true, message: '请输入问题描述', trigger: 'blur' }, { max: 20000, message: '问题描述不能超过 20000 个字符', trigger: 'blur' }],
}
const editable = computed(() => ticket.value?.status === 'PENDING' && !ticket.value?.assigneeId)

async function loadTicket() {
  loading.value = true
  try {
    ticket.value = await getTicket(route.params.id)
    form.title = ticket.value.title
    form.description = ticket.value.description
  } catch (error) {
    showApiError(error, '工单详情加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid || !ticket.value) return
  saving.value = true
  try {
    ticket.value = await updateTicket(ticket.value.id, { title: form.title.trim(), description: form.description.trim(), version: ticket.value.version })
    form.title = ticket.value.title
    form.description = ticket.value.description
    ElMessage.success('工单已更新')
  } catch (error) {
    showApiError(error, '工单更新失败，请刷新后重试')
  } finally {
    saving.value = false
  }
}

function statusTagType(status) {
  return { PENDING: 'info', ASSIGNED: '', PROCESSING: 'warning', WAITING_USER: 'warning', RESOLVED: 'success', CLOSED: 'success' }[status] || 'info'
}

onMounted(loadTicket)
</script>

<template>
  <div v-loading="loading" class="ticket-page narrow-page">
    <div class="page-heading">
      <div><span class="eyebrow">TICKET DETAIL</span><h2>工单详情</h2><p v-if="ticket">{{ ticket.ticketNo }}</p></div>
      <el-button @click="router.push({ name: 'user-home' })">返回我的工单</el-button>
    </div>
    <template v-if="ticket">
      <el-card shadow="never" class="detail-card">
        <div class="detail-header">
          <div><h3>{{ ticket.title }}</h3><div class="detail-tags"><el-tag :type="statusTagType(ticket.status)">{{ labelOf(TICKET_STATUS_LABELS, ticket.status) }}</el-tag><el-tag effect="plain">{{ labelOf(TICKET_PRIORITY_LABELS, ticket.priority) }}</el-tag><el-tag effect="plain">{{ labelOf(AI_STATUS_LABELS, ticket.aiStatus) }}</el-tag></div></div>
          <span class="detail-version">版本 {{ ticket.version }}</span>
        </div>
        <div class="detail-grid"><div><span>分类</span><strong>{{ labelOf(TICKET_CATEGORY_LABELS, ticket.category) }}</strong></div><div><span>创建时间</span><strong>{{ formatDate(ticket.createdAt) }}</strong></div><div><span>更新时间</span><strong>{{ formatDate(ticket.updatedAt) }}</strong></div><div><span>处理人</span><strong>{{ ticket.assigneeId || '待分配' }}</strong></div></div>
        <el-alert v-if="ticket.categoryReason" title="AI 分类理由" :description="ticket.categoryReason" type="info" :closable="false" show-icon />
      </el-card>

      <el-card v-if="editable" shadow="never" class="form-card">
        <template #header><div class="card-header"><strong>修改工单内容</strong><span>仅待分配工单允许修改</span></div></template>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="save">
          <el-form-item label="工单标题" prop="title"><el-input v-model="form.title" maxlength="200" show-word-limit /></el-form-item>
          <el-form-item label="问题描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="7" maxlength="20000" show-word-limit /></el-form-item>
          <div class="form-actions"><el-button type="primary" :loading="saving" native-type="submit">保存修改</el-button></div>
        </el-form>
      </el-card>
      <el-card v-else shadow="never" class="notice-card"><el-icon><InfoFilled /></el-icon><span>工单已进入处理流程，标题和描述暂不可修改。</span></el-card>
      <el-card shadow="never" class="notice-card"><el-icon><ChatDotRound /></el-icon><span>公开回复功能将在工单消息接口接入后开放。</span></el-card>
    </template>
  </div>
</template>
