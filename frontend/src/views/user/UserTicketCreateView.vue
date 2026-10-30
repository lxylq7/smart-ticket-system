<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { createTicket } from '../../api/tickets'
import { showApiError } from '../../api/http'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ title: '', description: '' })
const rules = {
  title: [{ required: true, message: '请输入工单标题', trigger: 'blur' }, { max: 200, message: '标题不能超过 200 个字符', trigger: 'blur' }],
  description: [{ required: true, message: '请输入问题描述', trigger: 'blur' }, { max: 20000, message: '问题描述不能超过 20000 个字符', trigger: 'blur' }],
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    const ticket = await createTicket({ title: form.title.trim(), description: form.description.trim() })
    ElMessage.success(`工单 ${ticket.ticketNo} 已创建`)
    router.replace({ name: 'user-ticket-detail', params: { id: ticket.id } })
  } catch (error) {
    showApiError(error, '工单创建失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="ticket-page narrow-page">
    <div class="page-heading">
      <div><span class="eyebrow">NEW TICKET</span><h2>创建工单</h2><p>请尽量完整地描述问题，便于客服快速处理。</p></div>
      <el-button @click="router.back()">返回</el-button>
    </div>
    <el-card shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="工单标题" prop="title"><el-input v-model="form.title" maxlength="200" show-word-limit placeholder="例如：支付后订单状态没有更新" /></el-form-item>
        <el-form-item label="问题描述" prop="description"><el-input v-model="form.description" type="textarea" :rows="9" maxlength="20000" show-word-limit placeholder="请描述发生了什么、你已经尝试过什么，以及期望的结果" /></el-form-item>
        <div class="form-actions"><el-button @click="router.back()">取消</el-button><el-button type="primary" :loading="loading" native-type="submit">提交工单</el-button></div>
      </el-form>
    </el-card>
  </div>
</template>
