import http from './http'

export function getAdminStats() {
  return http.get('/admin/stats')
}

export function listAdminUsers(params) {
  return http.get('/admin/users', { params })
}

export function createAdminUser(payload) {
  return http.post('/admin/users', payload)
}

export function updateAdminUserStatus(id, status) {
  return http.put(`/admin/users/${id}/status`, { status })
}
