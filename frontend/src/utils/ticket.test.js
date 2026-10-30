import { describe, expect, it } from 'vitest'
import { AI_STATUS_LABELS, TICKET_STATUS_LABELS, formatDate, labelOf } from './ticket'

describe('ticket presentation helpers', () => {
  it('maps known values and gives a safe fallback for unknown values', () => {
    expect(labelOf(TICKET_STATUS_LABELS, 'PROCESSING')).toBe('处理中')
    expect(labelOf(AI_STATUS_LABELS, 'UNKNOWN')).toBe('UNKNOWN')
    expect(labelOf(TICKET_STATUS_LABELS, null)).toBe('未设置')
  })

  it('formats missing timestamps without throwing', () => {
    expect(formatDate(null)).toBe('—')
    expect(formatDate('2026-01-02T03:04:05Z')).toContain('2026')
  })
})
