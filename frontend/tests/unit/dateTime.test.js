import { describe, it, expect } from 'vitest'
import dayjs from 'dayjs'
import { toLocalDateTimeString } from '../../src/utils/dateTime'

describe('dateTime helpers', () => {
  it('应保留选择的本地时间而不是转成 UTC', () => {
    const value = dayjs('2026-07-24T10:00:00')

    expect(toLocalDateTimeString(value)).toBe('2026-07-24T10:00:00')
  })
})
