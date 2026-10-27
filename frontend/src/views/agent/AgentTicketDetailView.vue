<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { changeTicketPriority, changeTicketStatus, createTicketMessage, getTicket, listTicketHistory, listTicketMessages, retryTicketClassification } from '../../api/tickets'
import { showApiError } from '../../api/http'
import TicketMessagePanel from '../../components/TicketMessagePanel.vue'
import TicketHistoryPanel from '../../components/TicketHistoryPanel.vue'
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
const messages = ref([])
const messagesLoading = ref(false)
const messageSending = ref(false)
const messagePanelRef = ref()
const targetStatus = ref('')
const remark = ref('')
const targetPriority = ref('')
const priorityRemark = ref('')
const prioritySaving = ref(false)
const history = reactive({ records: [], total: 0, page: 1, size: 10 })
const historyLoading = ref(false)
const retrying = ref(false)
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
    await Promise.all([loadMessages(), loadHistory()])
  } catch (error) {
    showApiError(error, '工单详情加载失败，或该工单已不在你的授权范围内')
  } finally {
    loading.value = false
  }
}

async function loadHistory() {
  if (!ticket.value) return
  historyLoading.value = true
  try {
    const data = await listTicketHistory(ticket.value.id, { page: history.page, size: history.size })
    history.records = data.records || []
    history.total = data.total || 0
  } catch (error) {
    showApiError(error, '操作历史加载失败')
  } finally {
    historyLoading.value = false
  }
}

function changeHistoryPage(page) {
  history.page = page
  loadHistory()
}

async function loadMessages() {
  if (!ticket.value) return
  messagesLoading.value = true
  try {
    messages.value = await listTicketMessages(ticket.value.id)
  } catch (error) {
    showApiError(error, '消息加载失败')
  } finally {
    messagesLoading.value = false
  }
}

async function sendMessage(payload) {
  if (!ticket.value) return
  messageSending.value = true
  try {
    await createTicketMessage(ticket.value.id, payload)
    messagePanelRef.value?.clear()
    await loadMessages()
    await loadHistory()
    ElMessage.success(payload.visibility === 'INTERNAL' ? '内部备注已保存' : '回复已发送')
  } catch (error) {
    showApiError(error, '消息发送失败，请稍后重试')
  } finally {
    messageSending.value = false
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
    await loadHistory()
    ElMessage.success('工单状态已更新')
  } catch (error) {
    showApiError(error, '状态更新失败，工单可能已被其他人修改，请刷新后重试')
    await loadTicket()
  } finally {
    saving.value = false
  }
}

async function applyPriority() {
  if (!targetPriority.value || !ticket.value) return
  prioritySaving.value = true
  try {
    ticket.value = await changeTicketPriority(ticket.value.id, {
      priority: targetPriority.value,
      version: ticket.value.version,
      remark: priorityRemark.value.trim() || null,
    })
    targetPriority.value = ''
    priorityRemark.value = ''
    await loadHistory()
    ElMessage.success('工单优先级已更新')
  } catch (error) {
    showApiError(error, '优先级更新失败，工单可能已被其他人修改，请刷新后重试')
    await loadTicket()
  } finally {
    prioritySaving.value = false
  }
}

async function retryClassification() {
  if (!ticket.value) return
  retrying.value = true
  try {
    ticket.value = await retryTicketClassification(ticket.value.id)
    await loadHistory()
    ElMessage.success('已提交重新分类请求')
  } catch (error) {
    showApiError(error, '重新分类失败，请稍后重试')
  } finally {
    retrying.value = false
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
        <div v-if="ticket.aiStatus === 'FAILED'" class="detail-actions"><el-button type="warning" plain :loading="retrying" @click="retryClassification">重新分类</el-button></div>
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
      <el-card v-if="ticket.status !== 'CLOSED'" shadow="never" class="form-card">
        <template #header><div class="card-header"><strong>调整优先级</strong><span>只有管理员或当前处理客服可以修改</span></div></template>
        <el-form label-position="top" @submit.prevent="applyPriority">
          <el-form-item label="优先级"><el-select v-model="targetPriority" placeholder="请选择优先级"><el-option v-for="(label, value) in TICKET_PRIORITY_LABELS" :key="value" :label="label" :value="value" /></el-select></el-form-item>
          <el-form-item label="调整说明"><el-input v-model="priorityRemark" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="可选，记录调整优先级的原因" /></el-form-item>
          <div class="form-actions"><el-button type="primary" :disabled="!targetPriority || targetPriority === ticket.priority" :loading="prioritySaving" native-type="submit">保存优先级</el-button></div>
        </el-form>
      </el-card>
      <TicketMessagePanel ref="messagePanelRef" :messages="messages" :loading="messagesLoading" :sending="messageSending" allow-internal :disabled="ticket.status === 'CLOSED'" @send="sendMessage" />
      <TicketHistoryPanel :records="history.records" :loading="historyLoading" :total="history.total" :page="history.page" :size="history.size" @page-change="changeHistoryPage" />
    </template>
  </div>
</template>
