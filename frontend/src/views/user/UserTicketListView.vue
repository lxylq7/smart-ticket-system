<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { listMyTickets } from '../../api/tickets'
import { showApiError } from '../../api/http'
import {
  AI_STATUS_LABELS,
  TICKET_CATEGORY_LABELS,
  TICKET_PRIORITY_LABELS,
  TICKET_STATUS_LABELS,
  formatDate,
  labelOf,
} from '../../utils/ticket'

const router = useRouter()
const loading = ref(false)
const query = reactive({ page: 1, size: 10, keyword: '', status: '', priority: '' })
const pageData = reactive({ records: [], total: 0, pages: 0 })

async function loadTickets() {
  loading.value = true
  try {
    const data = await listMyTickets({
      page: query.page,
      size: query.size,
      ...(query.keyword.trim() ? { keyword: query.keyword.trim() } : {}),
      ...(query.status ? { status: query.status } : {}),
      ...(query.priority ? { priority: query.priority } : {}),
    })
    pageData.records = data.records || []
    pageData.total = data.total || 0
    pageData.pages = data.pages || 0
  } catch (error) {
    showApiError(error, '工单列表加载失败')
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  loadTickets()
}

function resetFilters() {
  query.keyword = ''
  query.status = ''
  query.priority = ''
  search()
}

function openTicket(ticket) {
  router.push({ name: 'user-ticket-detail', params: { id: ticket.id } })
}

function createTicket() {
  router.push({ name: 'user-ticket-create' })
}

function statusTagType(status) {
  return { PENDING: 'info', ASSIGNED: '', PROCESSING: 'warning', WAITING_USER: 'warning', RESOLVED: 'success', CLOSED: 'success' }[status] || 'info'
}

function aiTagType(status) {
  return { SUCCESS: 'success', FAILED: 'danger', PENDING: 'info' }[status] || 'info'
}

onMounted(loadTickets)
</script>

<template>
  <div class="ticket-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">MY TICKETS</span>
        <h2>我的工单</h2>
        <p>查看你提交的问题、处理进度和分类结果。</p>
      </div>
      <el-button type="primary" size="large" @click="createTicket">+ 创建工单</el-button>
    </div>

    <el-card class="filter-card" shadow="never">
      <el-form inline @submit.prevent="search">
        <el-form-item label="关键词">
          <el-input v-model="query.keyword" clearable placeholder="标题或工单编号" @keyup.enter="search" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" @change="search">
            <el-option v-for="(label, value) in TICKET_STATUS_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="query.priority" clearable placeholder="全部优先级" @change="search">
            <el-option v-for="(label, value) in TICKET_PRIORITY_LABELS" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="ticket-table-card" shadow="never">
      <el-table v-loading="loading" :data="pageData.records" row-key="id" empty-text="暂无工单，先提交一个问题吧" @row-click="openTicket">
        <el-table-column prop="ticketNo" label="工单编号" min-width="190" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分类" width="120">
          <template #default="{ row }">{{ labelOf(TICKET_CATEGORY_LABELS, row.category) }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="100">
          <template #default="{ row }">{{ labelOf(TICKET_PRIORITY_LABELS, row.priority) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="115">
          <template #default="{ row }"><el-tag :type="statusTagType(row.status)" effect="light">{{ labelOf(TICKET_STATUS_LABELS, row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="AI 分类" width="105">
          <template #default="{ row }"><el-tag :type="aiTagType(row.aiStatus)" effect="plain">{{ labelOf(AI_STATUS_LABELS, row.aiStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click.stop="openTicket(row)">查看</el-button></template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <span>共 {{ pageData.total }} 条</span>
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next" :total="pageData.total" @current-change="loadTickets" @size-change="search" />
      </div>
    </el-card>
  </div>
</template>
