import { describe, it, expect } from 'vitest'
import {
  ACTIVITY_CATEGORIES,
  getCategoryLabel,
  formatDateTime,
  formatDateRange,
  initialUsers,
  initialActivities,
  SHARE_CHANNEL_LABELS
} from '../../src/data/mockData'

describe('mockData 工具函数', () => {
  describe('getCategoryLabel', () => {
    it('应返回已知分类的中文标签', () => {
      expect(getCategoryLabel('academic')).toBe('学术讲座')
      expect(getCategoryLabel('sports')).toBe('体育运动')
      expect(getCategoryLabel('volunteer')).toBe('志愿服务')
    })

    it('未知分类应原样返回', () => {
      expect(getCategoryLabel('unknown')).toBe('unknown')
    })
  })

  describe('formatDateTime', () => {
    it('空值应返回 "-"', () => {
      expect(formatDateTime(null)).toBe('-')
      expect(formatDateTime(undefined)).toBe('-')
      expect(formatDateTime('')).toBe('-')
    })

    it('应格式化为中文日期时间', () => {
      const result = formatDateTime('2026-07-15T14:00:00')
      expect(result).toMatch(/2026/)
      expect(result).toMatch(/07/)
      expect(result).toMatch(/15/)
    })
  })

  describe('formatDateRange', () => {
    it('应拼接起止时间', () => {
      const range = formatDateRange('2026-07-15T14:00:00', '2026-07-15T16:00:00')
      expect(range).toContain('—')
      expect(range.split('—').length).toBe(2)
    })
  })
})

describe('mockData 初始数据', () => {
  it('初始用户应包含学生与教师', () => {
    const roles = initialUsers.map(u => u.role)
    expect(roles).toContain('student')
    expect(roles).toContain('teacher')
    expect(roles).not.toContain('admin')
  })

  it('生产示例用户不应包含密码或硬编码管理员', () => {
    initialUsers.forEach(user => expect(user).not.toHaveProperty('password'))
    expect(initialUsers.some(user => user.role === 'admin')).toBe(false)
  })

  it('初始活动应包含已发布与已结束状态', () => {
    const statuses = initialActivities.map(a => a.status)
    expect(statuses).toContain('published')
    expect(statuses).toContain('ended')
  })

  it('活动分类常量应完整', () => {
    expect(ACTIVITY_CATEGORIES.length).toBeGreaterThanOrEqual(6)
    ACTIVITY_CATEGORIES.forEach(c => {
      expect(c).toHaveProperty('label')
      expect(c).toHaveProperty('value')
    })
  })

  it('生产 mock 数据不再包含静态社区聚类结果', async () => {
    const module = await import('../../src/data/mockData')
    expect(module).not.toHaveProperty('initialCommunityClusters')
  })

  it('传播渠道标签应覆盖主要渠道', () => {
    expect(SHARE_CHANNEL_LABELS).toMatchObject({
      wechat: expect.any(String),
      poster: expect.any(String),
      friend: expect.any(String),
      list: expect.any(String)
    })
  })
})
