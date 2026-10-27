<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { assignTicket, changeTicketPriority, listTickets } from '../../api/tickets'
import { createAdminUser, getAdminStats, listAdminUsers, updateAdminUserStatus } from '../../api/admin'
import { showApiError } from '../../api/http'
import {
  TICKET_CATEGORY_LABELS,
  TICKET_PRIORITY_LABELS,
  TICKET_STATUS_LABELS,
  formatDate,
  labelOf,
} from '../../utils/ticket'
import { getUser } from '../../utils/storage'

const loading = ref(false)
const ticketLoading = ref(false)
const userLoading = ref(false)
const createDialogVisible = ref(false)
const createLoading = ref(false)
const stats = ref(null)
const tickets = reactive({ records: [], total: 0 })
const users = reactive({ records: [], total: 0 })
const agentUsers = ref([])
const currentUser = computed(() => getUser())

const ticketQuery = reactive({ page: 1, size: 10, keyword: '', status: '', priority: '', category: '' })
const userQuery = reactive({ page: 1, size: 10, keyword: '', role: '', status: '' })
const createForm = reactive({ username: '', password: '', displayName: '', email: '', phone: '', role: 'AGENT' })

const statCards = computed(() => {
  const value = stats.value || {}
  return [
    { label: '工单总数', value: value.totalTickets ?? 0, tone: 'blue' },
    { label: '待分配', value: value.pendingTickets ?? 0, tone: 'orange' },
    { label: '处理中', value: value.processingTickets ?? 0, tone: 'purple' },
    { label: '已解决', value: value.resolvedTickets ?? 0, tone: 'green' },
    { label: '用户总数', value: value.totalUsers ?? 0, tone: 'teal' },
    { label: '活跃客服', value: value.activeAgents ?? 0, tone: 'red' },
  ]
})

function statusTagType(status) {
  return { PENDING: 'info', ASSIGNED: '', PROCESSING: 'warning', WAITING_USER: 'warning', RESOLVED: 'success', CLOSED: 'success' }[status] || 'info'
}

function userStatusType(status) {
  return status === 'ACTIVE' ? 'success' : 'info'
}

function roleLabel(role) {
  return { USER: '普通用户', AGENT: '客服', ADMIN: '管理员' }[role] || role
}

function resetCreateForm() {
  Object.assign(createForm, { username: '', password: '', displayName: '', email: '', phone: '', role: 'AGENT' })
}

async function loadStats() {
  stats.value = await getAdminStats()
}

async function loadTickets() {
  ticketLoading.value = true
  try {
    const data = await listTickets({
      page: ticketQuery.page,
      size: ticketQuery.size,
      ...(ticketQuery.keyword.trim() ? { keyword: ticketQuery.keyword.trim() } : {}),
      ...(ticketQuery.status ? { status: ticketQuery.status } : {}),
      ...(ticketQuery.priority ? { priority: ticketQuery.priority } : {}),
      ...(ticketQuery.category ? { category: ticketQuery.category } : {}),
    })
    tickets.records = data.records || []
    tickets.total = data.total || 0
  } catch (error) {
    showApiError(error, '工单列表加载失败')
  } finally {
    ticketLoading.value = false
  }
}

async function loadUsers() {
  userLoading.value = true
  try {
    const data = await listAdminUsers({
      page: userQuery.page,
      size: userQuery.size,
      ...(userQuery.keyword.trim() ? { keyword: userQuery.keyword.trim() } : {}),
      ...(userQuery.role ? { role: userQuery.role } : {}),
      ...(userQuery.status ? { status: userQuery.status } : {}),
    })
    users.records = data.records || []
    users.total = data.total || 0
    if (userQuery.role === 'AGENT' || !userQuery.role) await loadAgents()
  } catch (error) {
    showApiError(error, '账号列表加载失败')
  } finally {
    userLoading.value = false
  }
}

async function loadAgents() {
  try {
    const data = await listAdminUsers({ page: 1, size: 100, role: 'AGENT', status: 'ACTIVE' })
    agentUsers.value = data.records || []
  } catch (error) {
    showApiError(error, '客服列表加载失败')
  }
}

async function refreshAll() {
  loading.value = true
  try {
    await Promise.all([loadStats(), loadTickets(), loadUsers()])
  } catch (error) {
    showApiError(error, '管理员数据加载失败')
  } finally {
    loading.value = false
  }
}

async function assign(row, assigneeId) {
  if (!assigneeId || Number(assigneeId) === Number(row.assigneeId)) return
  try {
    await assignTicket(row.id, { assigneeId: Number(assigneeId), version: row.version })
    ElMessage.success('工单已分配')
    await Promise.all([loadStats(), loadTickets()])
  } catch (error) {
    showApiError(error, '分配失败，工单可能已被其他人修改')
    await loadTickets()
  }
}

async function changePriority(row, priority) {
  if (!priority || priority === row.priority) return
  try {
    await changeTicketPriority(row.id, { priority, version: row.version, remark: '管理员调整工单优先级' })
    ElMessage.success('工单优先级已更新')
    await Promise.all([loadStats(), loadTickets()])
  } catch (error) {
    showApiError(error, '优先级更新失败，工单可能已被其他人修改')
    await loadTickets()
  }
}

async function toggleUserStatus(row) {
  const nextStatus = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  try {
    await ElMessageBox.confirm(`确认${nextStatus === 'ACTIVE' ? '启用' : '禁用'}账号“${row.username}”？`, '账号状态', { type: 'warning' })
    await updateAdminUserStatus(row.id, nextStatus)
    ElMessage.success(nextStatus === 'ACTIVE' ? '账号已启用' : '账号已禁用')
    await Promise.all([loadStats(), loadUsers(), loadAgents()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') showApiError(error, '账号状态更新失败')
  }
}

async function submitCreateUser() {
  createLoading.value = true
  try {
    await createAdminUser({
      username: createForm.username.trim(),
      password: createForm.password,
      displayName: createForm.displayName.trim(),
      email: createForm.email.trim() || null,
      phone: createForm.phone.trim() || null,
      role: createForm.role,
    })
    ElMessage.success('账号创建成功')
    createDialogVisible.value = false
    resetCreateForm()
    await Promise.all([loadStats(), loadUsers(), loadAgents()])
  } catch (error) {
    showApiError(error, '账号创建失败，请检查用户名和输入内容')
  } finally {
    createLoading.value = false
  }
}

function searchTickets() { ticketQuery.page = 1; loadTickets() }
function searchUsers() { userQuery.page = 1; loadUsers() }
function resetTicketFilters() { Object.assign(ticketQuery, { page: 1, keyword: '', status: '', priority: '', category: '' }); loadTickets() }
function resetUserFilters() { Object.assign(userQuery, { page: 1, keyword: '', role: '', status: '' }); loadUsers() }

onMounted(refreshAll)
</script>

<template>
  <div v-loading="loading" class="admin-page">
    <div class="page-heading admin-heading">
      <div><span class="eyebrow">ADMIN CONSOLE</span><h2>管理员工作台</h2><p>掌握工单运行情况、分配处理人并管理系统账号。</p></div>
      <el-button type="primary" @click="refreshAll">刷新数据</el-button>
    </div>

    <section class="admin-stat-grid">
      <article v-for="card in statCards" :key="card.label" class="admin-stat-card" :class="`tone-${card.tone}`">
        <span>{{ card.label }}</span><strong>{{ card.value }}</strong>
      </article>
    </section>

    <el-card class="filter-card admin-card" shadow="never">
      <template #header><div class="card-header"><strong>全部工单</strong><span>管理员可查看并分配所有工单</span></div></template>
      <el-form inline @submit.prevent="searchTickets">
        <el-form-item label="关键词"><el-input v-model="ticketQuery.keyword" clearable placeholder="标题或工单编号" @keyup.enter="searchTickets" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="ticketQuery.status" clearable placeholder="全部状态"><el-option v-for="(label, value) in TICKET_STATUS_LABELS" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="分类"><el-select v-model="ticketQuery.category" clearable placeholder="全部分类"><el-option v-for="(label, value) in TICKET_CATEGORY_LABELS" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="优先级"><el-select v-model="ticketQuery.priority" clearable placeholder="全部优先级"><el-option v-for="(label, value) in TICKET_PRIORITY_LABELS" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="searchTickets">查询</el-button><el-button @click="resetTicketFilters">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="ticketLoading" :data="tickets.records" row-key="id" empty-text="暂无工单">
        <el-table-column prop="ticketNo" label="工单编号" min-width="180" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" width="110"><template #default="{ row }">{{ labelOf(TICKET_CATEGORY_LABELS, row.category) }}</template></el-table-column>
        <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ labelOf(TICKET_STATUS_LABELS, row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="优先级" width="125"><template #default="{ row }"><el-select v-if="row.status !== 'CLOSED'" :model-value="row.priority" @change="value => changePriority(row, value)"><el-option v-for="(label, value) in TICKET_PRIORITY_LABELS" :key="value" :label="label" :value="value" /></el-select><span v-else>{{ labelOf(TICKET_PRIORITY_LABELS, row.priority) }}</span></template></el-table-column>
        <el-table-column label="处理人" min-width="180">
          <template #default="{ row }">
            <el-select v-if="row.status !== 'CLOSED' && row.status !== 'RESOLVED'" :model-value="row.assigneeId || ''" clearable placeholder="选择客服" @change="value => assign(row, value)">
              <el-option v-for="agent in agentUsers" :key="agent.id" :label="`${agent.displayName || agent.username}（${agent.username}）`" :value="agent.id" />
            </el-select>
            <span v-else>{{ row.assigneeId ? `#${row.assigneeId}` : '未分配' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="170"><template #default="{ row }">{{ formatDate(row.updatedAt) }}</template></el-table-column>
      </el-table>
      <div class="table-footer"><span>共 {{ tickets.total }} 条</span><el-pagination v-model:current-page="ticketQuery.page" v-model:page-size="ticketQuery.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="tickets.total" @current-change="loadTickets" @size-change="searchTickets" /></div>
    </el-card>

    <el-card class="filter-card admin-card" shadow="never">
      <template #header><div class="card-header"><strong>账号管理</strong><el-button type="primary" @click="createDialogVisible = true">+ 创建账号</el-button></div></template>
      <el-form inline @submit.prevent="searchUsers">
        <el-form-item label="关键词"><el-input v-model="userQuery.keyword" clearable placeholder="用户名或显示名称" @keyup.enter="searchUsers" /></el-form-item>
        <el-form-item label="角色"><el-select v-model="userQuery.role" clearable placeholder="全部角色"><el-option label="普通用户" value="USER" /><el-option label="客服" value="AGENT" /><el-option label="管理员" value="ADMIN" /></el-select></el-form-item>
        <el-form-item label="状态"><el-select v-model="userQuery.status" clearable placeholder="全部状态"><el-option label="启用" value="ACTIVE" /><el-option label="禁用" value="DISABLED" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="searchUsers">查询</el-button><el-button @click="resetUserFilters">重置</el-button></el-form-item>
      </el-form>
      <el-table v-loading="userLoading" :data="users.records" row-key="id" empty-text="暂无账号">
        <el-table-column prop="username" label="用户名" min-width="150" />
        <el-table-column prop="displayName" label="显示名称" min-width="140" />
        <el-table-column label="角色" width="130"><template #default="{ row }">{{ (row.roles || []).map(roleLabel).join('、') }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="userStatusType(row.status)">{{ row.status === 'ACTIVE' ? '启用' : '禁用' }}</el-tag></template></el-table-column>
        <el-table-column label="最后登录" width="170"><template #default="{ row }">{{ formatDate(row.lastLoginAt) }}</template></el-table-column>
        <el-table-column label="操作" width="110" fixed="right"><template #default="{ row }"><el-button v-if="row.id !== currentUser?.id" link type="primary" @click="toggleUserStatus(row)">{{ row.status === 'ACTIVE' ? '禁用' : '启用' }}</el-button><span v-else class="muted-text">当前账号</span></template></el-table-column>
      </el-table>
      <div class="table-footer"><span>共 {{ users.total }} 个账号</span><el-pagination v-model:current-page="userQuery.page" v-model:page-size="userQuery.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="users.total" @current-change="loadUsers" @size-change="searchUsers" /></div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="创建账号" width="520px" destroy-on-close @closed="resetCreateForm">
      <el-form :model="createForm" label-position="top" @submit.prevent="submitCreateUser">
        <el-form-item label="账号" required><el-input v-model="createForm.username" maxlength="64" autocomplete="off" /></el-form-item>
        <el-form-item label="初始密码" required><el-input v-model="createForm.password" type="password" show-password maxlength="72" autocomplete="new-password" /></el-form-item>
        <el-form-item label="显示名称" required><el-input v-model="createForm.displayName" maxlength="64" /></el-form-item>
        <el-form-item label="角色" required><el-radio-group v-model="createForm.role"><el-radio value="AGENT">客服</el-radio><el-radio value="USER">普通用户</el-radio></el-radio-group></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="createForm.email" maxlength="255" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="createForm.phone" maxlength="32" /></el-form-item>
        <div class="form-actions"><el-button @click="createDialogVisible = false">取消</el-button><el-button type="primary" :loading="createLoading" @click="submitCreateUser">创建</el-button></div>
      </el-form>
    </el-dialog>
  </div>
</template>
