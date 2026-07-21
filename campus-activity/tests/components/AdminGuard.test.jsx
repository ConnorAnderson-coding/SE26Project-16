import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import AdminGuard from '../../src/components/AdminGuard'
import AuthGuard from '../../src/components/AuthGuard'
import { renderWithApp } from '../helpers/renderWithApp'

function authenticated(user) {
  return new Response(JSON.stringify({ success: true, data: user }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function renderAdminRoute() {
  return renderWithApp(
    <Routes>
      <Route
        path="/admin"
        element={<AuthGuard><AdminGuard><div>管理后台内容</div></AdminGuard></AuthGuard>}
      />
    </Routes>,
    { route: '/admin' }
  )
}

describe('AdminGuard 组件', () => {
  it.each(['student', 'teacher'])('%s 不能进入管理员路由', async (role) => {
    fetch.mockResolvedValueOnce(authenticated({ id: `${role}-1`, name: role, role }))
    renderAdminRoute()

    expect(await screen.findByText('你没有访问管理员功能的权限')).toBeInTheDocument()
    expect(screen.queryByText('管理后台内容')).not.toBeInTheDocument()
  })

  it('管理员角色来自 /auth/me 并可渲染子组件', async () => {
    fetch.mockResolvedValueOnce(authenticated({
      id: 'server-admin', name: '管理员', role: 'admin'
    }))
    renderAdminRoute()

    expect(await screen.findByText('管理后台内容')).toBeInTheDocument()
  })
})
