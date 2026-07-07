import { describe, it, expect, beforeEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { AppProvider, useApp } from '../../src/context/AppContext'
import { initialUsers } from '../../src/data/mockData'

function useAppHook() {
  return renderHook(() => useApp(), {
    wrapper: ({ children }) => <AppProvider>{children}</AppProvider>
  })
}

describe('AppContext 业务逻辑', () => {
  describe('login / logout', () => {
    it('正确凭据应登录成功且不暴露密码', () => {
      const { result } = useAppHook()

      let loginResult
      act(() => {
        loginResult = result.current.login('524030910001', '123456')
      })

      expect(loginResult).toEqual({ success: true })
      expect(result.current.currentUser).toMatchObject({
        id: '524030910001',
        name: '张三',
        role: 'student'
      })
      expect(result.current.currentUser).not.toHaveProperty('password')
    })

    it('错误密码应登录失败', () => {
      const { result } = useAppHook()

      let loginResult
      act(() => {
        loginResult = result.current.login('524030910001', 'wrong')
      })

      expect(loginResult).toEqual({
        success: false,
        message: '学号/工号或密码错误'
      })
      expect(result.current.currentUser).toBeNull()
    })

    it('logout 应清除当前用户', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910001', '123456')
      })
      act(() => {
        result.current.logout()
      })

      expect(result.current.currentUser).toBeNull()
    })

    it('管理员账号应能登录', () => {
      const { result } = useAppHook()

      let loginResult
      act(() => {
        loginResult = result.current.login('admin001', '123456')
      })

      expect(loginResult).toEqual({ success: true })
      expect(result.current.currentUser).toMatchObject({
        id: 'admin001',
        role: 'admin'
      })
    })
  })

  describe('mergeSeedUsers', () => {
    beforeEach(() => {
      localStorage.clear()
    })

    it('localStorage 缺少种子用户时应自动合并 admin001', () => {
      const usersWithoutAdmin = initialUsers.filter(u => u.id !== 'admin001')
      localStorage.setItem(
        'campus-activity-state',
        JSON.stringify({
          currentUser: null,
          users: usersWithoutAdmin,
          activities: [],
          signups: [],
          favorites: [],
          feedbacks: [],
          checkIns: []
        })
      )

      const { result } = useAppHook()

      expect(result.current.users.some(u => u.id === 'admin001')).toBe(true)

      let loginResult
      act(() => {
        loginResult = result.current.login('admin001', '123456')
      })

      expect(loginResult.success).toBe(true)
      expect(result.current.currentUser?.role).toBe('admin')
    })
  })

  describe('register', () => {
    it('新用户应注册成功并自动登录', () => {
      const { result } = useAppHook()

      let registerResult
      act(() => {
        registerResult = result.current.register({
          id: '524030910099',
          password: '654321',
          name: '测试用户',
          role: 'student',
          college: '软件学院',
          grade: '2025级',
          interests: ['编程'],
          availableTime: ['weekend']
        })
      })

      expect(registerResult).toEqual({ success: true })
      expect(result.current.currentUser?.id).toBe('524030910099')
      expect(result.current.users.some(u => u.id === '524030910099')).toBe(true)
    })

    it('重复学号应注册失败', () => {
      const { result } = useAppHook()

      let registerResult
      act(() => {
        registerResult = result.current.register({
          id: '524030910001',
          password: '123456',
          name: '重复用户',
          college: '软件学院',
          grade: '2024级'
        })
      })

      expect(registerResult).toEqual({
        success: false,
        message: '该学号/工号已注册'
      })
    })
  })

  describe('signupActivity', () => {
    it('未登录时报名应失败', () => {
      const { result } = useAppHook()

      let signupResult
      act(() => {
        signupResult = result.current.signupActivity('1')
      })

      expect(signupResult).toEqual({
        success: false,
        message: '请先登录'
      })
    })

    it('登录后应能成功报名新活动', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910002', '123456')
      })

      let signupResult
      act(() => {
        signupResult = result.current.signupActivity('2')
      })

      expect(signupResult.success).toBe(true)
      expect(result.current.getSignupStatus('2')).toBe('pending')
    })

    it('重复报名同一活动应失败', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910001', '123456')
      })

      let signupResult
      act(() => {
        signupResult = result.current.signupActivity('1')
      })

      expect(signupResult).toEqual({
        success: false,
        message: '您已报名该活动'
      })
    })
  })

  describe('toggleFavorite', () => {
    it('未登录时收藏应失败', () => {
      const { result } = useAppHook()

      let favResult
      act(() => {
        favResult = result.current.toggleFavorite('2')
      })

      expect(favResult).toEqual({
        success: false,
        message: '请先登录'
      })
    })

    it('登录后应能切换收藏状态', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910002', '123456')
      })

      act(() => {
        result.current.toggleFavorite('1')
      })
      expect(result.current.isFavorited('1')).toBe(true)

      act(() => {
        result.current.toggleFavorite('1')
      })
      expect(result.current.isFavorited('1')).toBe(false)
    })
  })

  describe('checkIn', () => {
    it('未通过审核的用户不能签到', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910001', '123456')
      })

      let checkInResult
      act(() => {
        checkInResult = result.current.checkIn('2', 'qrcode')
      })

      expect(checkInResult).toEqual({
        success: false,
        message: '您尚未通过该活动的报名审核'
      })
    })

    it('已通过审核的用户应能签到', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910001', '123456')
      })

      let checkInResult
      act(() => {
        checkInResult = result.current.checkIn('1', 'qrcode')
      })

      expect(checkInResult).toEqual({
        success: true,
        message: '签到成功'
      })
    })
  })

  describe('submitFeedback', () => {
    it('登录用户应能提交评价', () => {
      const { result } = useAppHook()
      const beforeCount = result.current.feedbacks.length

      act(() => {
        result.current.login('524030910002', '123456')
      })

      let feedbackResult
      act(() => {
        feedbackResult = result.current.submitFeedback({
          activityId: '6',
          rating: 4,
          content: '活动体验不错'
        })
      })

      expect(feedbackResult).toEqual({
        success: true,
        message: '评价提交成功'
      })
      expect(result.current.feedbacks.length).toBe(beforeCount + 1)
    })
  })

  describe('getRecommendedActivities', () => {
    it('未登录时应返回最多 4 个已发布活动', () => {
      const { result } = useAppHook()

      const recommended = result.current.getRecommendedActivities()
      expect(recommended.length).toBeLessThanOrEqual(4)
      recommended.forEach(a => {
        expect(a.status).toBe('published')
      })
    })

    it('登录后应按兴趣标签推荐活动', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910001', '123456')
      })

      const recommended = result.current.getRecommendedActivities()
      expect(recommended.length).toBeLessThanOrEqual(6)
      expect(recommended.every(a => a.status === 'published')).toBe(true)
      expect(recommended[0]).toHaveProperty('recommendScore')
    })
  })

  describe('reviewSignup', () => {
    it('组织者应能审核报名', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.reviewSignup('s2', true)
      })

      const signup = result.current.signups.find(s => s.id === 's2')
      expect(signup?.status).toBe('approved')
    })
  })

  describe('createActivity / updateActivity', () => {
    it('登录用户应能创建活动', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('admin001', '123456')
      })

      let activityId
      act(() => {
        activityId = result.current.createActivity({
          title: '管理员测试活动',
          category: 'club',
          description: '测试描述',
          startTime: '2026-08-01T10:00:00',
          endTime: '2026-08-01T12:00:00',
          location: '测试地点',
          maxParticipants: 20,
          poster: 'https://example.com/poster.jpg',
          tags: ['测试']
        })
      })

      const created = result.current.activities.find(a => a.id === activityId)
      expect(created).toMatchObject({
        title: '管理员测试活动',
        organizerId: 'admin001',
        status: 'published'
      })
    })

    it('应能更新活动海报', () => {
      const { result } = useAppHook()
      const newPoster = 'https://example.com/new-poster.jpg'

      act(() => {
        result.current.updateActivity('3', { poster: newPoster })
      })

      const updated = result.current.activities.find(a => a.id === '3')
      expect(updated?.poster).toBe(newPoster)
    })
  })

  describe('updateProfile', () => {
    it('登录用户应能更新个人档案', () => {
      const { result } = useAppHook()

      act(() => {
        result.current.login('524030910001', '123456')
      })

      act(() => {
        result.current.updateProfile({
          name: '张三（已更新）',
          interests: ['AI', '摄影', '阅读']
        })
      })

      expect(result.current.currentUser?.name).toBe('张三（已更新）')
      expect(result.current.currentUser?.interests).toEqual(['AI', '摄影', '阅读'])
    })
  })
})
