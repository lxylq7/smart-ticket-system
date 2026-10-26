const TOKEN_KEY = 'smart-ticket.access-token'
const USER_KEY = 'smart-ticket.user'

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
