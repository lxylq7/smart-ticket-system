export const TICKET_STATUS_LABELS = Object.freeze({
  PENDING: '待分配',
  ASSIGNED: '已分配',
  PROCESSING: '处理中',
  WAITING_USER: '等待反馈',
  RESOLVED: '已解决',
  CLOSED: '已关闭',
})

export const TICKET_PRIORITY_LABELS = Object.freeze({
  LOW: '低',
  MEDIUM: '中',
  HIGH: '高',
  URGENT: '紧急',
})

export const TICKET_CATEGORY_LABELS = Object.freeze({
  ORDER: '订单问题',
  ACCOUNT: '账户问题',
  TECHNICAL: '技术问题',
  OTHER: '其他问题',
})

export const AI_STATUS_LABELS = Object.freeze({
  PENDING: '待分类',
  SUCCESS: '已分类',
  FAILED: '分类失败',
})

export function labelOf(labels, value) {
  return value ? labels[value] || value : '未设置'
}

export function formatDate(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('zh-CN', { hour12: false })
}
