import { describe, it, expect, beforeEach, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import AdminGuard from '../../src/components/AdminGuard'
import { renderWithApp } from '../helpers/renderWithApp'
import { setStoredUser, setToken } from '../../src/services/http'

vi.mock('../../src/services/userApi', () => ({
  getMe: vi.fn()
}))

import * as userApi from '../../src/services/userApi'

function ProtectedPage() {
  return <div>管理后台内容</div>
}

describe('AdminGuard 组件', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('非管理员应重定向到首页', async () => {
    const student = {
      id: '524030910001',
      name: '张三',
      role: 'student',
      college: '软件学院'
    }
    setToken('token')
    setStoredUser(student)
    userApi.getMe.mockResolvedValue(student)

    renderWithApp(
      <Routes>
        <Route
          path="/admin"
          element={
            <AdminGuard>
              <ProtectedPage />
            </AdminGuard>
          }
        />
        <Route path="/home" element={<div>首页</div>} />
      </Routes>,
      { route: '/admin' }
    )

    await waitFor(() => {
      expect(screen.queryByText('管理后台内容')).not.toBeInTheDocument()
      expect(screen.getByText('首页')).toBeInTheDocument()
    })
  })

  it('管理员应渲染子组件', async () => {
    const admin = {
      id: 'admin001',
      name: '系统管理员',
      role: 'admin',
      college: '软件学院'
    }
    setToken('token')
    setStoredUser(admin)
    userApi.getMe.mockResolvedValue(admin)

    renderWithApp(
      <Routes>
        <Route
          path="/admin"
          element={
            <AdminGuard>
              <ProtectedPage />
            </AdminGuard>
          }
        />
      </Routes>,
      { route: '/admin' }
    )

    await waitFor(() => {
      expect(screen.getByText('管理后台内容')).toBeInTheDocument()
    })
  })
})
