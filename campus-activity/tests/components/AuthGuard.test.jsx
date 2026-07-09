import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import AuthGuard from '../../src/components/AuthGuard'
import { renderWithApp } from '../helpers/renderWithApp'
import { setStoredUser, setToken } from '../../src/services/http'

vi.mock('../../src/services/userApi', () => ({
  getMe: vi.fn()
}))

import * as userApi from '../../src/services/userApi'

function ProtectedPage() {
  return <div>受保护页面</div>
}

describe('AuthGuard 组件', () => {
  beforeEach(() => {
    localStorage.clear()
    userApi.getMe.mockRejectedValue(new Error('no token'))
  })

  it('未登录时应重定向到首页', async () => {
    renderWithApp(
      <Routes>
        <Route
          path="/protected"
          element={
            <AuthGuard>
              <ProtectedPage />
            </AuthGuard>
          }
        />
        <Route path="/" element={<div>登录页</div>} />
      </Routes>,
      { route: '/protected' }
    )

    await waitFor(() => {
      expect(screen.queryByText('受保护页面')).not.toBeInTheDocument()
      expect(screen.getByText('登录页')).toBeInTheDocument()
    })
  })

  it('已登录时应渲染子组件', async () => {
    setToken('test-token')
    setStoredUser({
      id: '524030910001',
      name: '张三',
      role: 'student',
      college: '软件学院'
    })
    userApi.getMe.mockResolvedValue({
      id: '524030910001',
      name: '张三',
      role: 'student',
      college: '软件学院'
    })

    renderWithApp(
      <Routes>
        <Route
          path="/protected"
          element={
            <AuthGuard>
              <ProtectedPage />
            </AuthGuard>
          }
        />
      </Routes>,
      { route: '/protected' }
    )

    await waitFor(() => {
      expect(screen.getByText('受保护页面')).toBeInTheDocument()
    })
  })
})
