import { describe, expect, it } from 'vitest'
import { formatHotness, getHotnessValue } from '../../src/utils/hotness'

describe('hotness helpers', () => {
  it('formats invalid, large, medium, and small values', () => {
    expect(formatHotness('invalid')).toBe('0')
    expect(formatHotness(0)).toBe('0')
    expect(formatHotness(120.4)).toBe('120')
    expect(formatHotness(12.34)).toBe('12.3')
    expect(formatHotness(1.234)).toBe('1.23')
  })

  it('normalizes missing and invalid activity scores', () => {
    expect(getHotnessValue(null)).toBe(0)
    expect(getHotnessValue({ hotnessScore: 'bad' })).toBe(0)
    expect(getHotnessValue({ hotnessScore: '4.5' })).toBe(4.5)
  })
})
