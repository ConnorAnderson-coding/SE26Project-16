import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { Route, Routes } from 'react-router-dom'
import AuthGuard from '../../src/components/AuthGuard'
import { renderWithApp } from '../helpers/renderWithApp'

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

function ProtectedPage() {
  return <div>受保护页面</div>
}

describe('AuthGuard 组件', () => {
  it('初始化期间不闪现受保护内容', () => {
    fetch.mockImplementation(() => new Promise(() => {}))

    renderWithApp(
      <Routes>
        <Route path="/protected" element={<AuthGuard><ProtectedPage /></AuthGuard>} />
      </Routes>,
      { route: '/protected' }
    )

    expect(screen.queryByText('受保护页面')).not.toBeInTheDocument()
    expect(screen.getByText('正在确认登录状态')).toBeInTheDocument()
  })

  it('未登录时重定向并保存返回路径', async () => {
    fetch.mockResolvedValueOnce(jsonResponse({
      code: 'AUTHENTICATION_REQUIRED', message: '请先登录', details: {}
    }, 401))

    renderWithApp(
      <Routes>
        <Route path="/protected" element={<AuthGuard><ProtectedPage /></AuthGuard>} />
        <Route path="/" element={<div>登录页</div>} />
      </Routes>,
      { route: '/protected?from=test' }
    )

    expect(await screen.findByText('登录页')).toBeInTheDocument()
    expect(screen.queryByText('受保护页面')).not.toBeInTheDocument()
  })

  it('已登录时渲染子组件', async () => {
    fetch.mockResolvedValueOnce(jsonResponse({
      success: true,
      data: { id: 'student-1', name: '学生', role: 'student' }
    }))

    renderWithApp(
      <Routes>
        <Route path="/protected" element={<AuthGuard><ProtectedPage /></AuthGuard>} />
      </Routes>,
      { route: '/protected' }
    )

    expect(await screen.findByText('受保护页面')).toBeInTheDocument()
  })

  it('网络错误显示重试而不是跳转登录', async () => {
    fetch.mockRejectedValueOnce(new TypeError('offline'))

    renderWithApp(
      <Routes>
        <Route path="/protected" element={<AuthGuard><ProtectedPage /></AuthGuard>} />
      </Routes>,
      { route: '/protected' }
    )

    await waitFor(() => expect(screen.getByText('无法确认登录状态')).toBeInTheDocument())
    expect(screen.getByRole('button', { name: /重\s*试/ })).toBeInTheDocument()
  })
})
