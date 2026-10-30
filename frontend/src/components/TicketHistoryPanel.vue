<script setup>
import {
  TICKET_PRIORITY_LABELS,
  TICKET_STATUS_LABELS,
  formatDate,
  labelOf,
} from '../utils/ticket'

defineProps({
  records: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 10 },
})

const emit = defineEmits(['page-change'])

const OPERATION_LABELS = Object.freeze({
  CREATE: '创建工单',
  UPDATE: '修改工单',
  STATUS_CHANGE: '状态变更',
  ASSIGN: '分配处理人',
  PRIORITY_CHANGE: '调整优先级',
  PUBLIC_REPLY: '公开回复',
  INTERNAL_NOTE: '内部备注',
  AI_CLASSIFY: 'AI 分类',
  AI_CLASSIFY_FAILED: 'AI 分类失败',
})

function operationLabel(value) {
  return OPERATION_LABELS[value] || value || '操作'
}

function detailOf(item) {
  if (item.operationType === 'STATUS_CHANGE') {
    return `${labelOf(TICKET_STATUS_LABELS, item.fromStatus)} → ${labelOf(TICKET_STATUS_LABELS, item.toStatus)}`
  }
  if (item.operationType === 'ASSIGN') {
    return `${item.fromAssigneeId ? `#${item.fromAssigneeId}` : '未分配'} → ${item.toAssigneeId ? `#${item.toAssigneeId}` : '未分配'}`
  }
  if (item.operationType === 'PRIORITY_CHANGE') {
    return `${labelOf(TICKET_PRIORITY_LABELS, item.fromPriority)} → ${labelOf(TICKET_PRIORITY_LABELS, item.toPriority)}`
  }
  return ''
}

function pageChange(value) {
  emit('page-change', value)
}
</script>

<template>
  <el-card shadow="never" class="history-card">
    <template #header><div class="card-header"><strong>操作历史</strong><span>记录工单状态、分配、回复和分类变化</span></div></template>
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="!records.length" description="暂无操作历史" />
    <el-timeline v-else class="history-timeline">
      <el-timeline-item v-for="item in records" :key="item.id" :timestamp="formatDate(item.createdAt)" placement="top">
        <div class="history-item-title"><strong>{{ operationLabel(item.operationType) }}</strong><span>操作人 {{ item.operatorId ? `#${item.operatorId}` : '系统' }}</span></div>
        <div v-if="detailOf(item)" class="history-item-detail">{{ detailOf(item) }}</div>
        <p v-if="item.remark" class="history-item-remark">{{ item.remark }}</p>
      </el-timeline-item>
    </el-timeline>
    <div v-if="total > size" class="history-pagination"><el-pagination :current-page="page" :page-size="size" layout="prev, pager, next" :total="total" @current-change="pageChange" /></div>
  </el-card>
</template>
