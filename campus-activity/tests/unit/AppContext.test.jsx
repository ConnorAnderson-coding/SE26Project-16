import { beforeEach, describe, expect, it } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { AppProvider, useApp } from '../../src/context/AppContext'
import { clearCsrfToken } from '../../src/api/csrf'
import { request } from '../../src/api/http'

function jsonResponse(data, { status = 200 } = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

const student = {
  id: 'student-1',
  name: '测试学生',
  role: 'student',
  college: '软件学院',
  grade: '2024级',
  interests: ['AI'],
  availableTime: ['weekend']
}

const admin = { ...student, id: 'admin-from-server', name: '管理员', role: 'admin' }

const meResponse = (user) => jsonResponse({ success: true, message: 'success', data: user })
const authRequired = () => jsonResponse({
  code: 'AUTHENTICATION_REQUIRED',
  message: '请先登录',
  details: {}
}, { status: 401 })
const csrfResponse = () => jsonResponse({
  token: 'csrf-value',
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf'
})

function renderAppHook() {
  return renderHook(() => useApp(), {
    wrapper: ({ children }) => <AppProvider>{children}</AppProvider>
  })
}

async function renderAuthenticated(user = student) {
  fetch.mockResolvedValueOnce(meResponse(user))
  const hook = renderAppHook()
  await waitFor(() => expect(hook.result.current.authStatus).toBe('authenticated'))
  return hook
}

describe('AppContext 认证与业务状态', () => {
  beforeEach(() => {
    clearCsrfToken()
  })

  describe('Session 初始化', () => {
    it('启动时 /auth/me 成功应使用后端可信用户', async () => {
      const { result } = await renderAuthenticated(admin)

      expect(result.current.currentUser).toEqual(admin)
      expect(fetch.mock.calls[0][0]).toBe('/api/auth/me')
      expect(fetch.mock.calls[0][1].credentials).toBe('include')
    })

    it('启动时 AUTHENTICATION_REQUIRED 应进入正常匿名状态', async () => {
      fetch.mockResolvedValueOnce(authRequired())
      const { result } = renderAppHook()

      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))
      expect(result.current.currentUser).toBeNull()
      expect(result.current.authError).toBeNull()
    })

    it('启动时网络错误应进入 error 而非 anonymous', async () => {
      fetch.mockRejectedValueOnce(new TypeError('offline'))
      const { result } = renderAppHook()

      await waitFor(() => expect(result.current.authStatus).toBe('error'))
      expect(result.current.currentUser).toBeNull()
      expect(result.current.authError).toContain('网络连接失败')
    })

    it('刷新时不从 localStorage 恢复用户或角色', async () => {
      localStorage.setItem('campus-activity-state', JSON.stringify({
        currentUser: { id: 'forged', role: 'admin' },
        users: [{ id: 'forged', role: 'admin', password: 'secret' }]
      }))
      fetch.mockResolvedValueOnce(authRequired())

      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      expect(result.current.currentUser).toBeNull()
      const stored = JSON.parse(localStorage.getItem('campus-activity-state'))
      expect(stored).not.toHaveProperty('currentUser')
      expect(stored).not.toHaveProperty('users')
    })

    it('认证错误可显式重试并恢复 Session', async () => {
      fetch
        .mockRejectedValueOnce(new TypeError('offline'))
        .mockResolvedValueOnce(meResponse(student))
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('error'))

      await act(async () => {
        await result.current.refreshAuth()
      })

      expect(result.current.authStatus).toBe('authenticated')
      expect(result.current.currentUser).toEqual(student)
    })
  })

  describe('login / logout', () => {
    it('登录成功后必须再次 /auth/me，且密码不进入 Context', async () => {
      fetch
        .mockResolvedValueOnce(authRequired())
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(jsonResponse({ success: true, message: '登录成功', data: student }))
        .mockResolvedValueOnce(meResponse(student))
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      await act(async () => {
        await result.current.login({ id: student.id, password: 'transient-password' })
      })

      expect(result.current.currentUser).toEqual(student)
      expect(result.current.currentUser).not.toHaveProperty('password')
      expect(fetch.mock.calls.map(call => call[0])).toEqual([
        '/api/auth/me',
        '/api/auth/csrf',
        '/api/auth/login',
        '/api/auth/me'
      ])
    })

    it('INVALID_CREDENTIALS 不清除为 Session 过期事件', async () => {
      fetch
        .mockResolvedValueOnce(authRequired())
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(jsonResponse({
          code: 'INVALID_CREDENTIALS',
          message: '账号或密码错误',
          details: {}
        }, { status: 401 }))
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      await expect(act(async () => {
        await result.current.login({ id: 'wrong', password: 'wrong-password' })
      })).rejects.toMatchObject({ code: 'INVALID_CREDENTIALS' })

      expect(result.current.authStatus).toBe('anonymous')
      expect(fetch).toHaveBeenCalledTimes(3)
    })

    it('logout 携带 CSRF，成功后清除当前用户', async () => {
      fetch
        .mockResolvedValueOnce(meResponse(student))
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(jsonResponse({ success: true, message: '退出成功', data: null }))
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('authenticated'))
      expect(result.current.users).toContainEqual(student)

      await act(async () => {
        await result.current.logout()
      })

      expect(result.current.authStatus).toBe('anonymous')
      expect(result.current.currentUser).toBeNull()
      expect(result.current.users).not.toContainEqual(student)
      expect(fetch.mock.calls[2][1].headers.get('X-CSRF-TOKEN')).toBe('csrf-value')
    })

    it('logout 失败时不得伪装成功', async () => {
      fetch
        .mockResolvedValueOnce(meResponse(student))
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(jsonResponse({ code: 'INTERNAL_ERROR', message: '失败', details: {} }, { status: 500 }))
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('authenticated'))

      await expect(act(async () => {
        await result.current.logout()
      })).rejects.toMatchObject({ code: 'INTERNAL_ERROR' })

      expect(result.current.currentUser).toEqual(student)
      expect(result.current.authStatus).toBe('authenticated')
    })

    it('Session 过期事件全局清除用户', async () => {
      const { result } = await renderAuthenticated(student)
      fetch.mockResolvedValueOnce(jsonResponse({
        code: 'AUTHENTICATION_REQUIRED', message: '请先登录', details: {}
      }, { status: 401 }))

      await request('/api/v1/community-clustering/latest').catch(() => {})

      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))
      expect(result.current.currentUser).toBeNull()
      expect(result.current.users).not.toContainEqual(student)
    })

    it('ACCESS_DENIED 保留已登录用户', async () => {
      const { result } = await renderAuthenticated(student)
      fetch.mockResolvedValueOnce(jsonResponse({
        code: 'ACCESS_DENIED', message: '无权访问', details: {}
      }, { status: 403 }))

      await request('/api/v1/admin/community-clustering/runs/run-1').catch(() => {})

      expect(result.current.authStatus).toBe('authenticated')
      expect(result.current.currentUser).toEqual(student)
    })
  })

  describe('register', () => {
    it('注册成功不伪造登录状态，且请求不含 role', async () => {
      fetch
        .mockResolvedValueOnce(authRequired())
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(jsonResponse({ success: true, data: student }, { status: 201 }))
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      await act(async () => {
        await result.current.register({
          ...student,
          password: 'password-123',
          role: 'admin'
        })
      })

      expect(result.current.currentUser).toBeNull()
      expect(result.current.authStatus).toBe('anonymous')
      expect(JSON.parse(fetch.mock.calls[2][1].body)).not.toHaveProperty('role')
    })

    it('注册冲突保持匿名并返回固定错误码', async () => {
      fetch
        .mockResolvedValueOnce(authRequired())
        .mockResolvedValueOnce(csrfResponse())
        .mockResolvedValueOnce(jsonResponse({
          code: 'ACCOUNT_ALREADY_EXISTS',
          message: '该账号已存在',
          details: {}
        }, { status: 409 }))
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      await expect(act(async () => {
        await result.current.register({ ...student, password: 'password-123' })
      })).rejects.toMatchObject({ code: 'ACCOUNT_ALREADY_EXISTS' })
      expect(result.current.currentUser).toBeNull()
    })
  })

  describe('既有本地业务状态', () => {
    it('未登录时报名应失败', async () => {
      fetch.mockResolvedValueOnce(authRequired())
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      expect(result.current.signupActivity('1')).toEqual({ success: false, message: '请先登录' })
    })

    it('登录 Session 后应能报名新活动', async () => {
      const { result } = await renderAuthenticated({ ...student, id: '524030910002' })

      act(() => result.current.signupActivity('2'))

      expect(result.current.getSignupStatus('2')).toBe('pending')
    })

    it('重复报名同一活动应失败', async () => {
      const { result } = await renderAuthenticated({ ...student, id: '524030910001' })

      expect(result.current.signupActivity('1')).toEqual({
        success: false,
        message: '您已报名该活动'
      })
    })

    it('未登录时收藏应失败', async () => {
      fetch.mockResolvedValueOnce(authRequired())
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      expect(result.current.toggleFavorite('2')).toEqual({ success: false, message: '请先登录' })
    })

    it('登录后应能切换收藏状态', async () => {
      const { result } = await renderAuthenticated({ ...student, id: '524030910002' })

      act(() => result.current.toggleFavorite('1'))
      expect(result.current.isFavorited('1')).toBe(true)
      act(() => result.current.toggleFavorite('1'))
      expect(result.current.isFavorited('1')).toBe(false)
    })

    it('未通过审核的用户不能签到', async () => {
      const { result } = await renderAuthenticated({ ...student, id: '524030910001' })

      expect(result.current.checkIn('2', 'qrcode')).toEqual({
        success: false,
        message: '您尚未通过该活动的报名审核'
      })
    })

    it('已通过审核的用户应能签到', async () => {
      const { result } = await renderAuthenticated({ ...student, id: '524030910001' })

      expect(result.current.checkIn('1', 'qrcode')).toEqual({ success: true, message: '签到成功' })
    })

    it('登录用户应能提交评价', async () => {
      const { result } = await renderAuthenticated({ ...student, id: '524030910002' })
      const beforeCount = result.current.feedbacks.length

      act(() => result.current.submitFeedback({ activityId: '6', rating: 4, content: '体验不错' }))

      expect(result.current.feedbacks).toHaveLength(beforeCount + 1)
    })

    it('未登录时推荐最多四个已发布活动', async () => {
      fetch.mockResolvedValueOnce(authRequired())
      const { result } = renderAppHook()
      await waitFor(() => expect(result.current.authStatus).toBe('anonymous'))

      expect(result.current.getRecommendedActivities().length).toBeLessThanOrEqual(4)
    })

    it('登录后按兴趣生成推荐分数', async () => {
      const { result } = await renderAuthenticated(student)

      const recommended = result.current.getRecommendedActivities()
      expect(recommended.length).toBeLessThanOrEqual(6)
      expect(recommended[0]).toHaveProperty('recommendScore')
    })

    it('组织者应能审核报名', async () => {
      const { result } = await renderAuthenticated(student)

      act(() => result.current.reviewSignup('s2', true))
      expect(result.current.signups.find(item => item.id === 's2')?.status).toBe('approved')
    })

    it('登录用户应能创建活动', async () => {
      const { result } = await renderAuthenticated(admin)
      let activityId

      act(() => {
        activityId = result.current.createActivity({
          title: '测试活动', category: 'club', maxParticipants: 20
        })
      })

      expect(result.current.activities.find(item => item.id === activityId)).toMatchObject({
        title: '测试活动',
        organizerId: admin.id,
        status: 'published'
      })
    })

    it('应能更新活动海报', async () => {
      const { result } = await renderAuthenticated(student)

      act(() => result.current.updateActivity('3', { poster: 'https://example.com/new.jpg' }))

      expect(result.current.activities.find(item => item.id === '3')?.poster)
        .toBe('https://example.com/new.jpg')
    })

    it('个人档案本地编辑不允许注入 role', async () => {
      const { result } = await renderAuthenticated(student)

      act(() => result.current.updateProfile({ name: '已更新', role: 'admin' }))

      expect(result.current.currentUser.name).toBe('已更新')
      expect(result.current.currentUser.role).toBe('student')
    })
  })
})
