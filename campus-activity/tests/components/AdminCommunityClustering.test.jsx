import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import AdminCommunityClustering from '../../src/pages/AdminCommunityClustering'
import { clearCsrfToken } from '../../src/api/csrf'
import { renderWithApp } from '../helpers/renderWithApp'

function jsonResponse(data, { status = 200, headers = {} } = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers }
  })
}

const admin = { id: 'admin-from-server', name: '管理员', role: 'admin' }
const me = (role = 'admin') => jsonResponse({
  success: true,
  data: role === 'admin' ? admin : { id: `${role}-1`, name: role, role }
})
const csrf = () => jsonResponse({
  token: 'csrf-value', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf'
})
const accepted = (location = '/api/v1/admin/community-clustering/runs/run-1') => jsonResponse({
  runId: 'run-1',
  version: 'cc-20260721-0001',
  algorithm: 'KMEANS',
  clusterCount: 2,
  randomState: 42,
  status: 'PENDING',
  createdAt: '2026-07-21T04:00:00Z'
}, { status: 202, headers: location ? { Location: location } : {} })

function runDetail(status, overrides = {}) {
  return {
    runId: 'run-1',
    version: 'cc-20260721-0001',
    algorithm: 'KMEANS',
    clusterCount: 2,
    randomState: 42,
    status,
    sampleCount: status === 'SUCCESS' ? 18 : null,
    featureSchemaVersion: 'community-features-v1',
    metrics: status === 'SUCCESS'
      ? { inertia: 31.48, pcaExplainedVarianceRatio: [0.34, 0.21] }
      : null,
    failure: status === 'FAILED'
      ? { code: 'PYTHON_SERVICE_UNAVAILABLE', message: '聚类服务不可用' }
      : null,
    createdAt: '2026-07-21T04:00:00Z',
    startedAt: status === 'PENDING' ? null : '2026-07-21T04:00:01Z',
    finishedAt: ['SUCCESS', 'FAILED'].includes(status) ? '2026-07-21T04:00:03Z' : null,
    createdBy: 'admin-from-server',
    ...overrides
  }
}

function renderPage() {
  return renderWithApp(
    <Routes>
      <Route path="/admin/community-clustering" element={<AdminCommunityClustering />} />
      <Route path="/" element={<div>登录页面</div>} />
      <Route path="/community" element={<div>最新社区结果页面</div>} />
    </Routes>,
    { route: '/admin/community-clustering' }
  )
}

async function waitForForm() {
  return screen.findByRole('button', { name: /提交聚类任务/ })
}

async function submitDefault(pageUser = userEvent.setup()) {
  const button = await waitForForm()
  await pageUser.click(button)
  return button
}

afterEach(() => {
  clearCsrfToken()
  vi.useRealTimers()
})

describe('管理员异步聚类页面', () => {
  it.each(['student', 'teacher'])('%s 无法进入管理员页面', async (role) => {
    fetch.mockResolvedValueOnce(me(role))
    renderPage()

    expect(await screen.findByText('你没有访问管理员功能的权限')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /提交聚类任务/ })).not.toBeInTheDocument()
  })

  it('clusterCount 默认值为 2', async () => {
    fetch.mockResolvedValueOnce(me())
    renderPage()

    await waitForForm()
    expect(screen.getByRole('spinbutton', { name: '社区数量' })).toHaveValue('2')
  })

  it.each(['1', '2.5'])('前端阻止非法 clusterCount=%s', async (value) => {
    fetch.mockResolvedValueOnce(me())
    const pageUser = userEvent.setup()
    renderPage()
    await waitForForm()
    const input = screen.getByRole('spinbutton', { name: '社区数量' })
    await pageUser.clear(input)
    await pageUser.type(input, value)
    await pageUser.click(screen.getByRole('button', { name: /提交聚类任务/ }))

    expect(await screen.findByText('社区数量必须是至少为 2 的整数')).toBeInTheDocument()
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('POST 只发送 clusterCount、携带 CSRF，并立即显示 PENDING 接受态', async () => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(accepted())
      .mockImplementationOnce(() => new Promise(() => {}))
    const pageUser = userEvent.setup()
    renderPage()

    const button = await submitDefault(pageUser)

    expect(await screen.findByText('聚类任务已接受')).toBeInTheDocument()
    expect(screen.getByText(/PENDING（等待执行）/)).toBeInTheDocument()
    expect(screen.getByText(/不表示聚类已经完成/)).toBeInTheDocument()
    expect(button).not.toBeDisabled()
    const [, postOptions] = fetch.mock.calls[2]
    expect(JSON.parse(postOptions.body)).toEqual({ clusterCount: 2 })
    expect(postOptions.headers.get('X-CSRF-TOKEN')).toBe('csrf-value')
  })

  it('外部 Location 被拒绝并使用安全 fallback 路径', async () => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(accepted('https://evil.example/run-1'))
      .mockResolvedValueOnce(jsonResponse(runDetail('SUCCESS')))
    renderPage()
    await submitDefault()

    expect(await screen.findByText('响应地址未通过校验')).toBeInTheDocument()
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(4))
    expect(fetch.mock.calls[3][0]).toBe('/api/v1/admin/community-clustering/runs/run-1')
  })

  it('缺失 Location 使用 runId 安全路径并记录协议提示', async () => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(accepted(null))
      .mockResolvedValueOnce(jsonResponse(runDetail('FAILED')))
    renderPage()
    await submitDefault()

    expect(await screen.findByText('响应地址未通过校验')).toBeInTheDocument()
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(4))
    expect(fetch.mock.calls[3][0]).toBe('/api/v1/admin/community-clustering/runs/run-1')
  })

  it('用串行 setTimeout 轮询 PENDING、RUNNING，SUCCESS 后停止', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const pageUser = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const details = [runDetail('PENDING'), runDetail('RUNNING'), runDetail('SUCCESS')]
    fetch.mockImplementation((url) => {
      if (url === '/api/auth/me') return Promise.resolve(me())
      if (url === '/api/auth/csrf') return Promise.resolve(csrf())
      if (url === '/api/v1/admin/community-clustering/runs') return Promise.resolve(accepted())
      return Promise.resolve(jsonResponse(details.shift()))
    })
    renderPage()
    await act(async () => { await vi.runOnlyPendingTimersAsync() })

    await submitDefault(pageUser)
    expect(screen.getByText(/PENDING（等待执行）/)).toBeInTheDocument()

    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(screen.getByText(/PENDING（等待执行）/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    expect(screen.getByText(/RUNNING（执行中）/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })

    expect(screen.getByText(/SUCCESS（已成功）/)).toBeInTheDocument()
    expect(screen.getByText('31.48')).toBeInTheDocument()
    expect(screen.getByText('0.34、0.21')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '查看最新社区结果' })).toHaveAttribute('href', '/community')
    const getCount = fetch.mock.calls.filter(call => call[0].endsWith('/run-1')).length
    await act(async () => { await vi.advanceTimersByTimeAsync(10000) })
    expect(fetch.mock.calls.filter(call => call[0].endsWith('/run-1'))).toHaveLength(getCount)
  })

  it('FAILED 后停止轮询并只展示结构化安全失败', async () => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(accepted())
      .mockResolvedValueOnce(jsonResponse(runDetail('FAILED', {
        errorMessage: 'internal stack trace',
        inputs: [{ college: '秘密学院', grade: '秘密年级' }]
      })))
    const { container } = renderPage()
    await submitDefault()

    expect(await screen.findByText('失败代码：PYTHON_SERVICE_UNAVAILABLE')).toBeInTheDocument()
    expect(screen.getByText('聚类服务不可用')).toBeInTheDocument()
    expect(container.textContent).not.toContain('internal stack trace')
    expect(container.textContent).not.toContain('秘密学院')
    expect(container.textContent).not.toContain('秘密年级')
    expect(screen.getByRole('button', { name: /提交聚类任务/ })).not.toBeDisabled()
  })

  it.each([
    [409, 'RUN_CONFLICT', '已存在正在处理的聚类任务'],
    [503, 'CLUSTERING_SERVICE_UNAVAILABLE', '聚类执行功能当前关闭']
  ])('%s 提交错误不自动重试 POST', async (status, code, expected) => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(jsonResponse({ code, message: 'internal', details: {} }, { status }))
    renderPage()
    await submitDefault()

    expect(await screen.findByText(new RegExp(expected))).toBeInTheDocument()
    expect(fetch.mock.calls.filter(call => call[0] === '/api/v1/admin/community-clustering/runs'))
      .toHaveLength(1)
  })

  it('CSRF 错误不重放 POST', async () => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(jsonResponse({
        code: 'CSRF_TOKEN_INVALID', message: 'internal', details: {}
      }, { status: 403 }))
    renderPage()
    await submitDefault()

    expect(await screen.findByText(/系统不会自动重放本次请求/)).toBeInTheDocument()
    expect(fetch.mock.calls.filter(call => call[0] === '/api/v1/admin/community-clustering/runs'))
      .toHaveLength(1)
  })

  it('GET 网络失败不会重新 POST，并提供手动重试查询', async () => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(accepted())
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(jsonResponse(runDetail('SUCCESS')))
    const pageUser = userEvent.setup()
    renderPage()
    await submitDefault(pageUser)

    const retry = await screen.findByRole('button', { name: /手动重试查询/ })
    expect(fetch.mock.calls.filter(call => call[0] === '/api/v1/admin/community-clustering/runs'))
      .toHaveLength(1)
    await pageUser.click(retry)
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(5))
    expect(await screen.findByText(/SUCCESS（已成功）/)).toBeInTheDocument()
  })

  it('ACCESS_DENIED 中止轮询并保留管理员页面身份', async () => {
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(accepted())
      .mockResolvedValueOnce(jsonResponse({
        code: 'ACCESS_DENIED', message: 'internal', details: {}
      }, { status: 403 }))
    renderPage()
    await submitDefault()

    expect(await screen.findByText('你没有查询该聚类任务的权限。')).toBeInTheDocument()
    expect(screen.getAllByText('管理员').length).toBeGreaterThanOrEqual(1)
    expect(screen.queryByText('登录页面')).not.toBeInTheDocument()
  })

  it('组件卸载取消正在进行的状态查询', async () => {
    let querySignal
    fetch
      .mockResolvedValueOnce(me())
      .mockResolvedValueOnce(csrf())
      .mockResolvedValueOnce(accepted())
      .mockImplementationOnce((url, options) => {
        querySignal = options.signal
        return new Promise(() => {})
      })
    const view = renderPage()
    await submitDefault()
    await waitFor(() => expect(querySignal).toBeDefined())

    view.unmount()

    expect(querySignal.aborted).toBe(true)
  })
})
