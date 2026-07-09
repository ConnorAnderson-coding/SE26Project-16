import { describe, it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import { Routes, Route } from 'react-router-dom'
import AuthGuard from '../../src/components/AuthGuard'
import { renderWithApp } from '../helpers/renderWithApp'

function ProtectedPage() {
  return <div>受保护页面</div>
}

describe('AuthGuard 组件', () => {
  it('未登录时应重定向到首页', () => {
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

    expect(screen.queryByText('受保护页面')).not.toBeInTheDocument()
    expect(screen.getByText('登录页')).toBeInTheDocument()
  })

  it('已登录时应渲染子组件', () => {
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

    expect(screen.getByText('受保护页面')).toBeInTheDocument()
  })
})
