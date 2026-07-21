import { describe, it, expect } from 'vitest'
import {
  getActivityTimeSlot,
  matchesInterestTags,
  matchesAvailableTime,
  matchesActivityStatus,
  matchesTimeSlots,
  matchesTimeRange
} from '../../src/utils/activityFilters'

function mockDay(isoDate) {
  const d = new Date(isoDate)
  const dayStart = new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const dayEnd = new Date(d.getFullYear(), d.getMonth(), d.getDate(), 23, 59, 59, 999).getTime()
  return {
    startOf: () => ({ valueOf: () => dayStart }),
    endOf: () => ({ valueOf: () => dayEnd })
  }
}

describe('activityFilters', () => {
  const activity = {
    startTime: '2026-07-15T14:00:00',
    tags: ['AI', '编程'],
    status: 'published'
  }

  it('getActivityTimeSlot 应映射工作日下午', () => {
    expect(getActivityTimeSlot('2026-07-15T14:00:00')).toBe('weekday_afternoon')
  })

  it('getActivityTimeSlot 应映射周末', () => {
    expect(getActivityTimeSlot('2026-07-18T10:00:00')).toBe('weekend')
  })

  it('getActivityTimeSlot 应映射工作日晚间', () => {
    expect(getActivityTimeSlot('2026-07-15T19:00:00')).toBe('weekday_evening')
  })

  it('matchesInterestTags 标签有交集时返回 true', () => {
    expect(matchesInterestTags(activity, ['AI'])).toBe(true)
    expect(matchesInterestTags(activity, ['音乐'])).toBe(false)
  })

  it('matchesInterestTags 未选标签时返回 true', () => {
    expect(matchesInterestTags(activity, [])).toBe(true)
    expect(matchesInterestTags(activity, null)).toBe(true)
  })

  it('matchesAvailableTime 应匹配用户可参与时段', () => {
    expect(matchesAvailableTime(activity, ['weekday_afternoon'])).toBe(true)
    expect(matchesAvailableTime(activity, ['weekend'])).toBe(false)
  })

  it('matchesActivityStatus 应按状态过滤', () => {
    expect(matchesActivityStatus(activity, 'published')).toBe(true)
    expect(matchesActivityStatus(activity, 'ended')).toBe(false)
    expect(matchesActivityStatus(activity, null)).toBe(true)
  })

  it('matchesTimeSlots 应按活动时段过滤', () => {
    expect(matchesTimeSlots(activity, ['weekday_afternoon'])).toBe(true)
    expect(matchesTimeSlots(activity, ['weekend'])).toBe(false)
  })

  it('matchesTimeRange 应按日期范围过滤', () => {
    const inRange = [mockDay('2026-07-15'), mockDay('2026-07-20')]
    const outOfRange = [mockDay('2026-08-01'), mockDay('2026-08-10')]

    expect(matchesTimeRange(activity, inRange)).toBe(true)
    expect(matchesTimeRange(activity, outOfRange)).toBe(false)
    expect(matchesTimeRange(activity, null)).toBe(true)
  })
})
