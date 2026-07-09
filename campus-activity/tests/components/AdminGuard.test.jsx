import { describe, it, expect, beforeEach } from 'vitest'
import { screen } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import AdminGuard from '../../src/components/AdminGuard'
import { renderWithApp } from '../helpers/renderWithApp'

function ProtectedPage() {
  return <div>管理后台内容</div>
}

describe('AdminGuard 组件', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('非管理员应重定向到首页', () => {
    localStorage.setItem(
      'campus-activity-state',
      JSON.stringify({
        currentUser: {
          id: '524030910001',
          name: '张三',
          role: 'student',
          college: '软件学院'
        },
        users: [],
        activities: [],
        signups: [],
        favorites: [],
        feedbacks: [],
        checkIns: []
      })
    )

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

    expect(screen.queryByText('管理后台内容')).not.toBeInTheDocument()
    expect(screen.getByText('首页')).toBeInTheDocument()
  })

  it('管理员应渲染子组件', () => {
    localStorage.setItem(
      'campus-activity-state',
      JSON.stringify({
        currentUser: {
          id: 'admin001',
          name: '系统管理员',
          role: 'admin',
          college: '软件学院'
        },
        users: [],
        activities: [],
        signups: [],
        favorites: [],
        feedbacks: [],
        checkIns: []
      })
    )

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

    expect(screen.getByText('管理后台内容')).toBeInTheDocument()
  })
})
