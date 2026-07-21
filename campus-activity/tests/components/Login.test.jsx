import { describe, expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import AuthGuard from '../../src/components/AuthGuard'
import Login from '../../src/pages/Login'
import { renderWithApp } from '../helpers/renderWithApp'

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

const authRequired = () => jsonResponse({
  code: 'AUTHENTICATION_REQUIRED', message: '请先登录', details: {}
}, 401)
const csrf = () => jsonResponse({
  token: 'csrf-value', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf'
})
const student = {
  id: 'student-1', name: '学生', role: 'student', interests: [], availableTime: []
}
const me = () => jsonResponse({ success: true, data: student })

function renderLogin(route = '/') {
  return renderWithApp(
    <Routes>
      <Route path="/" element={<Login />} />
      <Route path="/home" element={<div>首页内容</div>} />
      <Route
        path="/protected"
        element={<AuthGuard><div>原目标页面</div></AuthGuard>}
      />
    </Routes>,
    { route }
  )
}

async function waitForLoginForm() {
  return screen.findByPlaceholderText('学号/工号')
}

describe('真实登录与注册页面', () => {
  it('登录使用正确 autocomplete 并防止浏览器误存密码', async () => {
    fetch.mockResolvedValueOnce(authRequired())
    renderLogin()

    const idInput = await waitForLoginForm()
    const passwordInput = screen.getByPlaceholderText('密码')

    expect(idInput).toHaveAttribute('autocomplete', 'username')
    expect(passwordInput).toHaveAttribute('autocomplete', 'current-password')
  })

  it('登录成功后再次 /me 并进入首页', async () => {
    fetch
      .mockResolvedValueOnce(authRequired())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(jsonResponse({ success: true, data: student }))
      .mockResolvedValueOnce(me())
    const user = userEvent.setup()
    renderLogin()

    await user.type(await waitForLoginForm(), student.id)
    await user.type(screen.getByPlaceholderText('密码'), 'password-123')
    await user.click(screen.getByRole('button', { name: /^登\s*录$/ }))

    expect(await screen.findByText('首页内容')).toBeInTheDocument()
    expect(fetch.mock.calls.map(call => call[0])).toEqual([
      '/api/auth/me', '/api/auth/csrf', '/api/auth/login', '/api/auth/me'
    ])
  })

  it('从受保护路由登录后返回原目标', async () => {
    fetch
      .mockResolvedValueOnce(authRequired())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(jsonResponse({ success: true, data: student }))
      .mockResolvedValueOnce(me())
    const user = userEvent.setup()
    renderLogin('/protected')

    await user.type(await waitForLoginForm(), student.id)
    await user.type(screen.getByPlaceholderText('密码'), 'password-123')
    await user.click(screen.getByRole('button', { name: /^登\s*录$/ }))

    expect(await screen.findByText('原目标页面')).toBeInTheDocument()
  })

  it('INVALID_CREDENTIALS 显示固定内联错误且不重试密码 POST', async () => {
    fetch
      .mockResolvedValueOnce(authRequired())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(jsonResponse({
        code: 'INVALID_CREDENTIALS', message: 'internal detail', details: {}
      }, 401))
    const user = userEvent.setup()
    renderLogin()

    await user.type(await waitForLoginForm(), 'wrong')
    await user.type(screen.getByPlaceholderText('密码'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /^登\s*录$/ }))

    expect(await screen.findByText('账号或密码错误')).toBeInTheDocument()
    expect(screen.queryByText('internal detail')).not.toBeInTheDocument()
    expect(fetch).toHaveBeenCalledTimes(3)
  })

  it('提交期间按钮禁用，阻止重复登录', async () => {
    let resolveLogin
    fetch
      .mockResolvedValueOnce(authRequired())
      .mockResolvedValueOnce(csrf())
      .mockImplementationOnce(() => new Promise(resolve => { resolveLogin = resolve }))
    const user = userEvent.setup()
    renderLogin()

    await user.type(await waitForLoginForm(), student.id)
    await user.type(screen.getByPlaceholderText('密码'), 'password-123')
    const button = screen.getByRole('button', { name: /^登\s*录$/ })
    await user.click(button)

    await waitFor(() => expect(button).toBeDisabled())
    await user.click(button)
    expect(fetch).toHaveBeenCalledTimes(3)
    resolveLogin(jsonResponse({ code: 'INVALID_CREDENTIALS', message: '错误', details: {} }, 401))
  })

  it('注册表单没有角色选择，成功后回到登录且不伪造 Session', async () => {
    fetch
      .mockResolvedValueOnce(authRequired())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(jsonResponse({ success: true, data: student }, 201))
    const user = userEvent.setup()
    renderLogin()

    await waitForLoginForm()
    await user.click(screen.getByRole('tab', { name: '注册' }))
    expect(screen.queryByLabelText('身份')).not.toBeInTheDocument()
    const registerPanel = screen.getByRole('tabpanel', { name: '注册' })
    await user.type(within(registerPanel).getByLabelText('学号/工号'), 'student-2')
    await user.type(within(registerPanel).getByLabelText('密码'), 'password-123')
    await user.type(within(registerPanel).getByLabelText('姓名'), '新用户')
    await user.click(within(registerPanel).getByRole('button', { name: /注\s*册/ }))

    expect(await screen.findByText('注册成功，请使用新账号登录')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '登录' })).toHaveAttribute('aria-selected', 'true')
    expect(JSON.parse(fetch.mock.calls[2][1].body)).not.toHaveProperty('role')
  })
})
