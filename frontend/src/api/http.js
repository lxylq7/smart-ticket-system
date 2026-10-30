import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearAuth, getToken } from '../utils/storage'

export class ApiError extends Error {
  constructor(message, { code = 'NETWORK_ERROR', status = 0, data = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.data = data
  }
}

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: Number(import.meta.env.VITE_API_TIMEOUT || 15000),
  headers: { 'Content-Type': 'application/json' },
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (!payload || payload.code !== 'SUCCESS') {
      throw new ApiError(payload?.message || '请求未成功', {
        code: payload?.code || 'INVALID_RESPONSE',
        status: response.status,
        data: payload?.data ?? null,
      })
    }
    return payload.data
  },
  async (error) => {
    const response = error.response
    const payload = response?.data
    const apiError = new ApiError(payload?.message || error.message || '网络请求失败', {
      code: payload?.code || (response ? `HTTP_${response.status}` : 'NETWORK_ERROR'),
      status: response?.status || 0,
      data: payload?.data ?? null,
    })
    if (apiError.status === 401) {
      clearAuth()
      if (router.currentRoute.value.name !== 'login') await router.push({ name: 'login' })
    }
    return Promise.reject(apiError)
  },
)

export function showApiError(error, fallback = '操作失败') {
  const message = error instanceof ApiError ? error.message : fallback
  ElMessage.error(message || fallback)
}

export default http
