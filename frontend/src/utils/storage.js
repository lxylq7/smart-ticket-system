const TOKEN_KEY = 'smart-ticket.access-token'
const USER_KEY = 'smart-ticket.user'

// 多角色账号使用管理员 > 客服 > 普通用户的默认工作区顺序。
export const ROLE_HOME = Object.freeze({
  ADMIN: 'admin-home',
  AGENT: 'agent-home',
  USER: 'user-home',
})

const ROLE_PRIORITY = ['ADMIN', 'AGENT', 'USER']

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function getUser() {
  const value = localStorage.getItem(USER_KEY)
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch {
    clearAuth()
    return null
  }
}

export function saveAuth(loginData) {
  localStorage.setItem(TOKEN_KEY, loginData.accessToken)
  localStorage.setItem(USER_KEY, JSON.stringify(loginData.user))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function hasRole(role) {
  return getUser()?.roles?.includes(role) === true
}

export function hasAnyRole(roles = []) {
  return roles.length === 0 || roles.some((role) => hasRole(role))
}

export function getRoleHomeRoute(user = getUser()) {
  const roles = Array.isArray(user?.roles) ? user.roles : []
  const role = ROLE_PRIORITY.find((candidate) => roles.includes(candidate))
  return role ? { name: ROLE_HOME[role] } : { name: 'forbidden' }
}
