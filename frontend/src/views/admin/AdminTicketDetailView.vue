<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { assignTicket, changeTicketPriority, changeTicketStatus, createTicketMessage, getTicket, listTicketHistory, listTicketMessages } from '../../api/tickets'
import { listAdminUsers } from '../../api/admin'
import { showApiError } from '../../api/http'
import TicketHistoryPanel from '../../components/TicketHistoryPanel.vue'
import TicketMessagePanel from '../../components/TicketMessagePanel.vue'
import { AI_STATUS_LABELS, TICKET_CATEGORY_LABELS, TICKET_PRIORITY_LABELS, TICKET_STATUS_LABELS, formatDate, labelOf } from '../../utils/ticket'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const prioritySaving = ref(false)
const assignmentSaving = ref(false)
const messagesLoading = ref(false)
const messageSending = ref(false)
const ticket = ref(null)
const agents = ref([])
const messages = ref([])
const targetStatus = ref('')
const statusRemark = ref('')
const targetPriority = ref('')
const priorityRemark = ref('')
const selectedAssignee = ref('')
const assignmentRemark = ref('')
const messagePanelRef = ref()
const history = ref({ records: [], total: 0, page: 1, size: 10 })
const historyLoading = ref(false)

const transitionLabels = Object.freeze({ PROCESSING: '开始处理', WAITING_USER: '等待用户反馈', RESOLVED: '标记为已解决', CLOSED: '关闭工单' })
const transitions = computed(() => {
  const map = { ASSIGNED: ['PROCESSING'], PROCESSING: ['WAITING_USER', 'RESOLVED'], WAITING_USER: ['PROCESSING'], RESOLVED: ['PROCESSING', 'CLOSED'] }
  return (map[ticket.value?.status] || []).map((value) => ({ value, label: transitionLabels[value] || labelOf(TICKET_STATUS_LABELS, value) }))
})

function statusTagType(status) {
  return { PENDING: 'info', ASSIGNED: '', PROCESSING: 'warning', WAITING_USER: 'warning', RESOLVED: 'success', CLOSED: 'success' }[status] || 'info'
}

async function loadAgents() {
  try {
    const data = await listAdminUsers({ page: 1, size: 100, role: 'AGENT', status: 'ACTIVE' })
    agents.value = data.records || []
  } catch (error) {
    showApiError(error, '客服列表加载失败')
  }
}

async function loadMessages() {
  if (!ticket.value) return
  messagesLoading.value = true
  try { messages.value = await listTicketMessages(ticket.value.id) } catch (error) { showApiError(error, '消息加载失败') } finally { messagesLoading.value = false }
}

async function loadHistory() {
  if (!ticket.value) return
  historyLoading.value = true
  try {
    const data = await listTicketHistory(ticket.value.id, { page: history.value.page, size: history.value.size })
    history.value = { ...history.value, records: data.records || [], total: data.total || 0 }
  } catch (error) { showApiError(error, '操作历史加载失败') } finally { historyLoading.value = false }
}

async function loadTicket() {
  loading.value = true
  try {
    ticket.value = await getTicket(route.params.id)
    selectedAssignee.value = ticket.value.assigneeId || ''
    await Promise.all([loadAgents(), loadMessages(), loadHistory()])
  } catch (error) { showApiError(error, '工单详情加载失败') } finally { loading.value = false }
}

async function applyAssignment() {
  if (!ticket.value || !selectedAssignee.value) return
  assignmentSaving.value = true
  try {
    ticket.value = await assignTicket(ticket.value.id, { assigneeId: Number(selectedAssignee.value), version: ticket.value.version, remark: assignmentRemark.value.trim() || null })
    assignmentRemark.value = ''
    await loadHistory()
    ElMessage.success('工单已分配')
  } catch (error) { showApiError(error, '工单分配失败，可能已被其他人修改'); await loadTicket() } finally { assignmentSaving.value = false }
}

async function applyStatus() {
  if (!ticket.value || !targetStatus.value) return
  saving.value = true
  try {
    ticket.value = await changeTicketStatus(ticket.value.id, { status: targetStatus.value, version: ticket.value.version, remark: statusRemark.value.trim() || null })
    targetStatus.value = ''; statusRemark.value = ''
    await loadHistory(); ElMessage.success('工单状态已更新')
  } catch (error) { showApiError(error, '状态更新失败，工单可能已被其他人修改'); await loadTicket() } finally { saving.value = false }
}

async function applyPriority() {
  if (!ticket.value || !targetPriority.value) return
  prioritySaving.value = true
  try {
    ticket.value = await changeTicketPriority(ticket.value.id, { priority: targetPriority.value, version: ticket.value.version, remark: priorityRemark.value.trim() || null })
    targetPriority.value = ''; priorityRemark.value = ''
    await loadHistory(); ElMessage.success('工单优先级已更新')
  } catch (error) { showApiError(error, '优先级更新失败，工单可能已被其他人修改'); await loadTicket() } finally { prioritySaving.value = false }
}

async function sendMessage(payload) {
  if (!ticket.value) return
  messageSending.value = true
  try {
    await createTicketMessage(ticket.value.id, payload)
    messagePanelRef.value?.clear()
    await Promise.all([loadMessages(), loadHistory()])
    ElMessage.success(payload.visibility === 'INTERNAL' ? '内部备注已保存' : '回复已发送')
  } catch (error) { showApiError(error, '消息发送失败') } finally { messageSending.value = false }
}

function changeHistoryPage(page) { history.value = { ...history.value, page }; loadHistory() }
onMounted(loadTicket)
</script>

<template>
  <div v-loading="loading" class="ticket-page narrow-page">
    <div class="page-heading"><div><span class="eyebrow">ADMIN TICKET DETAIL</span><h2>工单详情</h2><p v-if="ticket">{{ ticket.ticketNo }}</p></div><el-button @click="router.push({ name: 'admin-home' })">返回管理员工作台</el-button></div>
    <template v-if="ticket">
      <el-card shadow="never" class="detail-card">
        <div class="detail-header"><div><h3>{{ ticket.title }}</h3><div class="detail-tags"><el-tag :type="statusTagType(ticket.status)">{{ labelOf(TICKET_STATUS_LABELS, ticket.status) }}</el-tag><el-tag effect="plain">{{ labelOf(TICKET_PRIORITY_LABELS, ticket.priority) }}</el-tag><el-tag effect="plain">{{ labelOf(AI_STATUS_LABELS, ticket.aiStatus) }}</el-tag></div></div><span class="detail-version">版本 {{ ticket.version }}</span></div>
        <div class="detail-grid"><div><span>分类</span><strong>{{ labelOf(TICKET_CATEGORY_LABELS, ticket.category) }}</strong></div><div><span>创建人</span><strong>#{{ ticket.creatorId }}</strong></div><div><span>处理人</span><strong>{{ ticket.assigneeId ? `#${ticket.assigneeId}` : '待分配' }}</strong></div><div><span>更新时间</span><strong>{{ formatDate(ticket.updatedAt) }}</strong></div></div>
        <div class="description-block"><span>问题描述</span><p>{{ ticket.description }}</p></div><el-alert v-if="ticket.categoryReason" title="AI 分类理由" :description="ticket.categoryReason" type="info" :closable="false" show-icon />
      </el-card>
      <el-card shadow="never" class="form-card"><template #header><div class="card-header"><strong>分配处理人</strong><span>仅启用客服账号可被分配</span></div></template><el-form label-position="top" @submit.prevent="applyAssignment"><el-form-item label="客服"><el-select v-model="selectedAssignee" placeholder="选择客服" :disabled="ticket.status === 'CLOSED' || ticket.status === 'RESOLVED'"><el-option v-for="agent in agents" :key="agent.id" :label="`${agent.displayName || agent.username}（${agent.username}）`" :value="agent.id" /></el-select></el-form-item><el-form-item label="分配说明"><el-input v-model="assignmentRemark" maxlength="500" placeholder="可选" /></el-form-item><div class="form-actions"><el-button type="primary" :disabled="!selectedAssignee || Number(selectedAssignee) === Number(ticket.assigneeId)" :loading="assignmentSaving" native-type="submit">保存分配</el-button></div></el-form></el-card>
      <el-card v-if="transitions.length" shadow="never" class="form-card"><template #header><div class="card-header"><strong>状态流转</strong><span>管理员可执行完整状态流程</span></div></template><el-form label-position="top" @submit.prevent="applyStatus"><el-form-item label="下一状态"><el-select v-model="targetStatus" placeholder="请选择处理动作"><el-option v-for="item in transitions" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item><el-form-item label="处理说明"><el-input v-model="statusRemark" type="textarea" :rows="3" maxlength="500" /></el-form-item><div class="form-actions"><el-button type="primary" :disabled="!targetStatus" :loading="saving" native-type="submit">提交状态变更</el-button></div></el-form></el-card>
      <el-card v-if="ticket.status !== 'CLOSED'" shadow="never" class="form-card"><template #header><div class="card-header"><strong>调整优先级</strong><span>记录在操作历史中</span></div></template><el-form label-position="top" @submit.prevent="applyPriority"><el-form-item label="优先级"><el-select v-model="targetPriority" placeholder="请选择优先级"><el-option v-for="(label, value) in TICKET_PRIORITY_LABELS" :key="value" :label="label" :value="value" /></el-select></el-form-item><el-form-item label="调整说明"><el-input v-model="priorityRemark" type="textarea" :rows="3" maxlength="500" /></el-form-item><div class="form-actions"><el-button type="primary" :disabled="!targetPriority || targetPriority === ticket.priority" :loading="prioritySaving" native-type="submit">保存优先级</el-button></div></el-form></el-card>
      <TicketMessagePanel ref="messagePanelRef" :messages="messages" :loading="messagesLoading" :sending="messageSending" allow-internal :disabled="ticket.status === 'CLOSED'" @send="sendMessage" />
      <TicketHistoryPanel :records="history.records" :loading="historyLoading" :total="history.total" :page="history.page" :size="history.size" @page-change="changeHistoryPage" />
    </template>
  </div>
</template>
