import { describe, it, expect, beforeEach, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { AppProvider, useApp } from '../../src/context/AppContext'

vi.mock('../../src/services/authApi', () => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn()
}))

vi.mock('../../src/services/userApi', () => ({
  getMe: vi.fn(),
  updateProfile: vi.fn()
}))

vi.mock('../../src/services/activityApi', () => ({
  createActivity: vi.fn(),
  updateActivity: vi.fn(),
  getRecommended: vi.fn()
}))

vi.mock('../../src/services/registrationApi', () => ({
  signup: vi.fn(),
  reviewRegistration: vi.fn()
}))

vi.mock('../../src/services/favoriteApi', () => ({
  toggleFavorite: vi.fn()
}))

vi.mock('../../src/services/feedbackApi', () => ({
  submitFeedback: vi.fn()
}))

vi.mock('../../src/services/recordApi', () => ({
  publishRecord: vi.fn()
}))

vi.mock('../../src/services/mock/mockApi', () => ({
  checkIn: vi.fn(),
  getCheckIns: vi.fn().mockResolvedValue([])
}))

import * as authApi from '../../src/services/authApi'
import * as userApi from '../../src/services/userApi'
import * as activityApi from '../../src/services/activityApi'
import * as registrationApi from '../../src/services/registrationApi'
import * as favoriteApi from '../../src/services/favoriteApi'
import * as feedbackApi from '../../src/services/feedbackApi'
import * as mockApi from '../../src/services/mock/mockApi'
import { setToken, setStoredUser } from '../../src/services/http'

const mockUser = {
  id: '524030910001',
  name: '张三',
  role: 'student',
  college: '软件学院',
  grade: '2024级',
  interests: ['AI'],
  availableTime: ['weekend']
}

function useAppHook() {
  return renderHook(() => useApp(), {
    wrapper: ({ children }) => <AppProvider>{children}</AppProvider>
  })
}

describe('AppContext 业务逻辑', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    userApi.getMe.mockRejectedValue(new Error('no token'))
    mockApi.getCheckIns.mockResolvedValue([])
  })

  describe('login / logout', () => {
    it('正确凭据应登录成功', async () => {
      authApi.login.mockResolvedValue({ token: 't', user: mockUser })
      const { result } = useAppHook()

      let loginResult
      await act(async () => {
        loginResult = await result.current.login('524030910001', '123456')
      })

      expect(loginResult).toEqual({ success: true })
      expect(result.current.currentUser).toMatchObject({ id: '524030910001', name: '张三' })
    })

    it('错误密码应登录失败', async () => {
      authApi.login.mockRejectedValue(new Error('学号/工号或密码错误'))
      const { result } = useAppHook()

      let loginResult
      await act(async () => {
        loginResult = await result.current.login('524030910001', 'wrong')
      })

      expect(loginResult.success).toBe(false)
      expect(result.current.currentUser).toBeNull()
    })

    it('logout 应清除当前用户', async () => {
      setStoredUser(mockUser)
      setToken('token')
      userApi.getMe.mockResolvedValue(mockUser)
      const { result } = useAppHook()

      await waitFor(() => expect(result.current.initializing).toBe(false))

      act(() => {
        result.current.logout()
      })

      expect(result.current.currentUser).toBeNull()
    })
  })

  describe('register', () => {
    it('新用户应注册成功', async () => {
      authApi.register.mockResolvedValue({
        token: 't',
        user: { ...mockUser, id: '524030910099' }
      })
      const { result } = useAppHook()

      let registerResult
      await act(async () => {
        registerResult = await result.current.register({
          id: '524030910099',
          password: '654321',
          name: '测试用户',
          college: '软件学院',
          grade: '2025级'
        })
      })

      expect(registerResult).toEqual({ success: true })
      expect(result.current.currentUser?.id).toBe('524030910099')
    })
  })

  describe('signupActivity', () => {
    it('未登录时报名应失败', async () => {
      const { result } = useAppHook()
      let signupResult
      await act(async () => {
        signupResult = await result.current.signupActivity('1')
      })
      expect(signupResult).toEqual({ success: false, message: '请先登录' })
    })

    it('登录后应能成功报名', async () => {
      authApi.login.mockResolvedValue({ token: 't', user: mockUser })
      registrationApi.signup.mockResolvedValue({ id: '1', status: 'pending' })
      const { result } = useAppHook()

      await act(async () => {
        await result.current.login('524030910001', '123456')
      })

      let signupResult
      await act(async () => {
        signupResult = await result.current.signupActivity('1')
      })

      expect(signupResult.success).toBe(true)
    })
  })

  describe('toggleFavorite', () => {
    it('登录后应能切换收藏状态', async () => {
      authApi.login.mockResolvedValue({ token: 't', user: mockUser })
      favoriteApi.toggleFavorite.mockResolvedValue({ favorited: true })
      const { result } = useAppHook()

      await act(async () => {
        await result.current.login('524030910001', '123456')
      })

      let favResult
      await act(async () => {
        favResult = await result.current.toggleFavorite('1')
      })

      expect(favResult).toEqual({ success: true, favorited: true })
    })
  })

  describe('checkIn', () => {
    it('mock 签到应成功', async () => {
      authApi.login.mockResolvedValue({ token: 't', user: mockUser })
      mockApi.checkIn.mockResolvedValue({ success: true, message: '签到成功' })
      const { result } = useAppHook()

      await act(async () => {
        await result.current.login('524030910001', '123456')
      })

      let checkInResult
      await act(async () => {
        checkInResult = await result.current.checkIn('1', 'qrcode')
      })

      expect(checkInResult).toEqual({ success: true, message: '签到成功' })
    })
  })

  describe('getRecommendedActivities', () => {
    it('应调用推荐 API', async () => {
      activityApi.getRecommended.mockResolvedValue([
        { id: '1', status: 'published', recommendScore: 50 }
      ])
      const { result } = useAppHook()

      let recommended
      await act(async () => {
        recommended = await result.current.getRecommendedActivities()
      })

      expect(recommended).toHaveLength(1)
      expect(activityApi.getRecommended).toHaveBeenCalled()
    })
  })

  describe('updateProfile', () => {
    it('应更新个人档案', async () => {
      authApi.login.mockResolvedValue({ token: 't', user: mockUser })
      userApi.updateProfile.mockResolvedValue({ ...mockUser, name: '张三（已更新）' })
      const { result } = useAppHook()

      await act(async () => {
        await result.current.login('524030910001', '123456')
      })

      await act(async () => {
        await result.current.updateProfile({ name: '张三（已更新）' })
      })

      expect(result.current.currentUser?.name).toBe('张三（已更新）')
    })
  })

  describe('submitFeedback', () => {
    it('应提交评价', async () => {
      authApi.login.mockResolvedValue({ token: 't', user: mockUser })
      feedbackApi.submitFeedback.mockResolvedValue({ id: '1' })
      const { result } = useAppHook()

      await act(async () => {
        await result.current.login('524030910001', '123456')
      })

      let feedbackResult
      await act(async () => {
        feedbackResult = await result.current.submitFeedback({
          activityId: '6',
          rating: 4,
          content: '活动体验不错'
        })
      })

      expect(feedbackResult).toEqual({ success: true, message: '评价提交成功' })
    })
  })
})
