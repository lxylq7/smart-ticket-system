<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { formatDate } from '../utils/ticket'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  loading: Boolean,
  sending: Boolean,
  allowInternal: Boolean,
  disabled: Boolean,
})
const emit = defineEmits(['send'])
const content = ref('')
const visibility = ref('PUBLIC')
const visibilityLabels = Object.freeze({ PUBLIC: '公开回复', INTERNAL: '内部备注' })
const composerHint = computed(() => props.allowInternal && visibility.value === 'INTERNAL'
  ? '只有客服和管理员可以看到内部备注'
  : '用户和客服都可以看到公开回复')

watch(() => props.allowInternal, (allowInternal) => {
  if (!allowInternal) visibility.value = 'PUBLIC'
})

function clear() {
  content.value = ''
  visibility.value = 'PUBLIC'
}

function submit() {
  const text = content.value.trim()
  if (!text) {
    ElMessage.warning('请输入消息内容')
    return
  }
  emit('send', { visibility: props.allowInternal ? visibility.value : 'PUBLIC', content: text })
}

defineExpose({ clear })
</script>

<template>
  <el-card shadow="never" class="messages-card">
    <template #header>
      <div class="card-header"><strong>沟通记录</strong><span>{{ messages.length }} 条消息</span></div>
    </template>
    <div v-loading="loading" class="message-list">
      <el-empty v-if="!loading && messages.length === 0" description="暂无消息" />
      <article v-for="message in messages" :key="message.id" class="message-item" :class="{ 'message-internal': message.visibility === 'INTERNAL' }">
        <div class="message-meta"><el-tag size="small" :type="message.visibility === 'INTERNAL' ? 'warning' : 'success'" effect="plain">{{ visibilityLabels[message.visibility] || message.visibility }}</el-tag><span>发送人 #{{ message.senderId }}</span><time>{{ formatDate(message.createdAt) }}</time></div>
        <p>{{ message.content }}</p>
      </article>
    </div>
    <div v-if="!disabled" class="message-composer">
      <el-radio-group v-if="allowInternal" v-model="visibility" size="small" class="message-visibility">
        <el-radio-button label="PUBLIC">公开回复</el-radio-button>
        <el-radio-button label="INTERNAL">内部备注</el-radio-button>
      </el-radio-group>
      <el-input v-model="content" type="textarea" :rows="4" maxlength="10000" show-word-limit :placeholder="visibility === 'INTERNAL' ? '记录仅供客服和管理员查看的处理备注' : '输入要发送给对方的回复内容'" @keydown.ctrl.enter="submit" />
      <div class="message-composer-footer"><span>{{ composerHint }} · Ctrl + Enter 发送</span><el-button type="primary" :loading="sending" @click="submit">发送</el-button></div>
    </div>
    <el-alert v-else title="已关闭工单不能继续发送消息" type="info" :closable="false" class="message-disabled" />
  </el-card>
</template>
