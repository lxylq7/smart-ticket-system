import { beforeEach, describe, expect, it } from 'vitest'
import { clearAuth, getRoleHomeRoute, getUser, hasAnyRole, saveAuth } from './storage'

describe('auth storage', () => {
  beforeEach(() => {
    const values = new Map()
    globalThis.localStorage = {
      getItem: (key) => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, String(value)),
      removeItem: (key) => values.delete(key),
      clear: () => values.clear(),
      get length() { return values.size },
    }
    localStorage.clear()
  })

  it('saves and reads token and user without exposing the password', () => {
    saveAuth({ accessToken: 'token', user: { id: 1, username: 'alice', roles: ['USER'] } })
    expect(getUser()).toEqual({ id: 1, username: 'alice', roles: ['USER'] })
    expect(localStorage.getItem('smart-ticket.access-token')).toBe('token')
    expect(localStorage.getItem('smart-ticket.user')).not.toContain('password')
  })

  it('chooses the highest-priority workspace for multi-role users', () => {
    saveAuth({ accessToken: 'token', user: { roles: ['USER', 'ADMIN'] } })
    expect(getRoleHomeRoute()).toEqual({ name: 'admin-home' })
    expect(hasAnyRole(['ADMIN'])).toBe(true)
    expect(hasAnyRole(['AGENT'])).toBe(false)
  })

  it('clears invalid local auth state', () => {
    localStorage.setItem('smart-ticket.user', '{invalid')
    expect(getUser()).toBeNull()
    expect(localStorage.getItem('smart-ticket.user')).toBeNull()
    clearAuth()
    expect(localStorage.length).toBe(0)
  })
})
