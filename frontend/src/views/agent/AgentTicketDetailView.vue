<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound } from '@element-plus/icons-vue'
import { changeTicketStatus, getTicket } from '../../api/tickets'
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
const targetStatus = ref('')
const remark = ref('')
const transitionLabels = Object.freeze({
  PROCESSING: '开始处理',
  WAITING_USER: '等待用户反馈',
  RESOLVED: '标记为已解决',
})
const transitions = computed(() => {
  const map = { ASSIGNED: ['PROCESSING'], PROCESSING: ['WAITING_USER', 'RESOLVED'], WAITING_USER: ['PROCESSING'], RESOLVED: ['PROCESSING'] }
  return (map[ticket.value?.status] || []).map((value) => ({ value, label: transitionLabels[value] || labelOf(TICKET_STATUS_LABELS, value) }))
})

async function loadTicket() {
  loading.value = true
  try {
    ticket.value = await getTicket(route.params.id)
  } catch (error) {
    showApiError(error, '工单详情加载失败，或该工单已不在你的授权范围内')
  } finally {
    loading.value = false
  }
}

async function applyStatus() {
  if (!targetStatus.value || !ticket.value) return
  saving.value = true
  try {
    ticket.value = await changeTicketStatus(ticket.value.id, {
      status: targetStatus.value,
      version: ticket.value.version,
      remark: remark.value.trim() || null,
    })
    targetStatus.value = ''
    remark.value = ''
    ElMessage.success('工单状态已更新')
  } catch (error) {
    showApiError(error, '状态更新失败，工单可能已被其他人修改，请刷新后重试')
    await loadTicket()
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
      <div><span class="eyebrow">TICKET PROCESSING</span><h2>处理工单</h2><p v-if="ticket">{{ ticket.ticketNo }}</p></div>
      <el-button @click="router.push({ name: 'agent-home' })">返回客服工作台</el-button>
    </div>
    <template v-if="ticket">
      <el-card shadow="never" class="detail-card">
        <div class="detail-header">
          <div><h3>{{ ticket.title }}</h3><div class="detail-tags"><el-tag :type="statusTagType(ticket.status)">{{ labelOf(TICKET_STATUS_LABELS, ticket.status) }}</el-tag><el-tag effect="plain">{{ labelOf(TICKET_PRIORITY_LABELS, ticket.priority) }}</el-tag><el-tag effect="plain">{{ labelOf(AI_STATUS_LABELS, ticket.aiStatus) }}</el-tag></div></div>
          <span class="detail-version">版本 {{ ticket.version }}</span>
        </div>
        <div class="detail-grid"><div><span>分类</span><strong>{{ labelOf(TICKET_CATEGORY_LABELS, ticket.category) }}</strong></div><div><span>创建人</span><strong>#{{ ticket.creatorId }}</strong></div><div><span>创建时间</span><strong>{{ formatDate(ticket.createdAt) }}</strong></div><div><span>更新时间</span><strong>{{ formatDate(ticket.updatedAt) }}</strong></div></div>
        <div class="description-block"><span>问题描述</span><p>{{ ticket.description }}</p></div>
        <el-alert v-if="ticket.categoryReason" title="AI 分类理由" :description="ticket.categoryReason" type="info" :closable="false" show-icon />
      </el-card>

      <el-card shadow="never" class="form-card">
        <template #header><div class="card-header"><strong>状态流转</strong><span>后端会校验处理人、合法状态和版本号</span></div></template>
        <template v-if="transitions.length">
          <el-form label-position="top" @submit.prevent="applyStatus">
            <el-form-item label="下一状态"><el-select v-model="targetStatus" placeholder="请选择处理动作"><el-option v-for="item in transitions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
            <el-form-item label="处理说明"><el-input v-model="remark" type="textarea" :rows="4" maxlength="500" show-word-limit placeholder="可选，记录本次状态变更的原因" /></el-form-item>
            <div class="form-actions"><el-button type="primary" :disabled="!targetStatus" :loading="saving" native-type="submit">提交状态变更</el-button></div>
          </el-form>
        </template>
        <el-empty v-else description="当前状态没有客服可执行的下一步操作" />
      </el-card>
      <el-card shadow="never" class="notice-card"><el-icon><ChatDotRound /></el-icon><span>公开回复和内部备注将在工单消息接口接入后开放。</span></el-card>
    </template>
  </div>
</template>
