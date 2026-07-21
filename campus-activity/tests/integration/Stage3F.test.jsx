import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, Route, Routes } from 'react-router-dom'
import Login from '../../src/pages/Login'
import CommunityClusters from '../../src/pages/CommunityClusters'
import AdminCommunityClustering from '../../src/pages/AdminCommunityClustering'
import { clearCsrfToken } from '../../src/api/csrf'
import { renderWithApp } from '../helpers/renderWithApp'

function jsonResponse(data, { status = 200, headers = {} } = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers }
  })
}

const csrf = () => jsonResponse({
  token: 'csrf-value', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf'
})
const student = {
  id: 'student-1', name: '测试学生', role: 'student', interests: [], availableTime: []
}
const admin = { ...student, id: 'admin-1', name: '测试管理员', role: 'admin' }
const me = user => jsonResponse({ success: true, data: user })
const authRequired = () => jsonResponse({
  code: 'AUTHENTICATION_REQUIRED', message: '请先登录', details: {}
}, { status: 401 })

const latest = {
  run: {
    runId: 'run-1', version: 'cc-integration-1', algorithm: 'KMEANS',
    clusterCount: 2, sampleCount: 2, finishedAt: '2026-07-21T04:00:03Z'
  },
  communities: [{
    communityId: 'community-1', clusterNo: 0, name: '社区 1',
    description: '主要兴趣：AI', memberCount: 2, topInterests: ['AI'],
    color: '#1677FF',
    points: [
      { pointId: 'point-current', x: 20, y: 30, currentUser: true },
      { pointId: 'point-other', x: 70, y: 80, currentUser: false }
    ]
  }]
}

const membership = {
  runId: 'run-1', version: 'cc-integration-1',
  membership: {
    communityId: 'community-1', clusterNo: 0, communityName: '社区 1',
    color: '#1677FF', pointId: 'point-current', x: 20, y: 30, distanceToCenter: 0.4
  }
}

afterEach(() => {
  clearCsrfToken()
  vi.useRealTimers()
})

describe('阶段 3F 集成流程', () => {
  it('匿名用户登录、查看社区、登出后重新受保护', async () => {
    let authMeCount = 0
    fetch.mockImplementation((url, options) => {
      if (url === '/api/auth/me') {
        authMeCount += 1
        return Promise.resolve(authMeCount === 1 ? authRequired() : me(student))
      }
      if (url === '/api/auth/csrf') return Promise.resolve(csrf())
      if (url === '/api/auth/login') {
        return Promise.resolve(jsonResponse({ success: true, data: student }))
      }
      if (url === '/api/v1/community-clustering/latest') {
        return Promise.resolve(jsonResponse(latest))
      }
      if (url === '/api/v1/community-clustering/me') {
        return Promise.resolve(jsonResponse(membership))
      }
      if (url === '/api/auth/logout') {
        expect(options.headers.get('X-CSRF-TOKEN')).toBe('csrf-value')
        return Promise.resolve(jsonResponse({ success: true, data: null }))
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    const pageUser = userEvent.setup()
    renderWithApp(
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/community" element={<CommunityClusters />} />
      </Routes>,
      { route: '/community' }
    )

    await pageUser.type(await screen.findByPlaceholderText('学号/工号'), student.id)
    await pageUser.type(screen.getByPlaceholderText('密码'), 'password-123')
    await pageUser.click(screen.getByRole('button', { name: /^登\s*录$/ }))

    expect(await screen.findByText('cc-integration-1')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /匿名散点图/ })).toBeInTheDocument()
    expect(document.querySelector('circle[data-current-user="true"]')).toBeInTheDocument()

    await pageUser.click(screen.getByText('测试学生'))
    await pageUser.click(await screen.findByText('退出登录'))

    expect(await screen.findByPlaceholderText('学号/工号')).toBeInTheDocument()
    expect(screen.queryByText('cc-integration-1')).not.toBeInTheDocument()
    expect(fetch.mock.calls.filter(call => call[0] === '/api/auth/logout')).toHaveLength(1)
  })

  it('管理员收到 202 后轮询到 SUCCESS 并进入最新社区结果入口', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const pageUser = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const accepted = {
      runId: 'run-admin-1', version: 'cc-admin-1', algorithm: 'KMEANS',
      clusterCount: 3, randomState: 42, status: 'PENDING', createdAt: '2026-07-21T04:00:00Z'
    }
    const success = {
      ...accepted,
      status: 'SUCCESS', sampleCount: 18, featureSchemaVersion: 'community-features-v1',
      metrics: { inertia: 20.1, pcaExplainedVarianceRatio: [0.5, 0.2] },
      failure: null, startedAt: '2026-07-21T04:00:01Z',
      finishedAt: '2026-07-21T04:00:03Z', createdBy: admin.id
    }
    const details = [accepted, { ...accepted, status: 'RUNNING' }, success]
    fetch.mockImplementation((url, options) => {
      if (url === '/api/auth/me') return Promise.resolve(me(admin))
      if (url === '/api/auth/csrf') return Promise.resolve(csrf())
      if (url === '/api/v1/admin/community-clustering/runs' && options.method === 'POST') {
        expect(JSON.parse(options.body)).toEqual({ clusterCount: 3 })
        return Promise.resolve(jsonResponse(accepted, {
          status: 202,
          headers: { Location: '/api/v1/admin/community-clustering/runs/run-admin-1' }
        }))
      }
      if (url === '/api/v1/admin/community-clustering/runs/run-admin-1') {
        return Promise.resolve(jsonResponse(details.shift()))
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    renderWithApp(
      <Routes>
        <Route path="/admin/community-clustering" element={<AdminCommunityClustering />} />
        <Route path="/community" element={<div>社区结果入口已打开</div>} />
      </Routes>,
      { route: '/admin/community-clustering' }
    )
    await act(async () => { await vi.runOnlyPendingTimersAsync() })

    await screen.findByRole('button', { name: /提交聚类任务/ })
    const form = screen.getByRole('spinbutton', { name: '社区数量' }).closest('form')
    const input = within(form).getByRole('spinbutton', { name: '社区数量' })
    await pageUser.clear(input)
    await pageUser.type(input, '3')
    await pageUser.click(within(form).getByRole('button', { name: /提交聚类任务/ }))

    expect(await screen.findByText('聚类任务已接受')).toBeInTheDocument()
    expect(screen.getByText(/PENDING（等待执行）/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(0) })
    expect(screen.getByText(/PENDING（等待执行）/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    expect(screen.getByText(/RUNNING（执行中）/)).toBeInTheDocument()
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    expect(screen.getByText(/SUCCESS（已成功）/)).toBeInTheDocument()
    await pageUser.click(screen.getByRole('link', { name: '查看最新社区结果' }))
    expect(await screen.findByText('社区结果入口已打开')).toBeInTheDocument()
  })

  it('后台最终失败时安全展示 failure，且管理员可以再次提交', async () => {
    let postCount = 0
    fetch.mockImplementation((url, options) => {
      if (url === '/api/auth/me') return Promise.resolve(me(admin))
      if (url === '/api/auth/csrf') return Promise.resolve(csrf())
      if (url === '/api/v1/admin/community-clustering/runs' && options.method === 'POST') {
        postCount += 1
        return Promise.resolve(jsonResponse({
          runId: `run-failed-${postCount}`,
          version: `cc-failed-${postCount}`,
          algorithm: 'KMEANS',
          clusterCount: 2,
          randomState: 42,
          status: 'PENDING',
          createdAt: '2026-07-21T04:00:00Z'
        }, {
          status: 202,
          headers: {
            Location: `/api/v1/admin/community-clustering/runs/run-failed-${postCount}`
          }
        }))
      }
      if (url.endsWith('/run-failed-1')) {
        return Promise.resolve(jsonResponse({
          runId: 'run-failed-1', version: 'cc-failed-1', algorithm: 'KMEANS',
          clusterCount: 2, randomState: 42, status: 'FAILED', sampleCount: 8,
          featureSchemaVersion: 'community-features-v1', metrics: null,
          failure: { code: 'CLUSTERING_EXECUTION_FAILED', message: '聚类计算失败' },
          errorMessage: 'internal stack trace',
          createdAt: '2026-07-21T04:00:00Z', startedAt: '2026-07-21T04:00:01Z',
          finishedAt: '2026-07-21T04:00:02Z', createdBy: admin.id
        }))
      }
      if (url.endsWith('/run-failed-2')) return new Promise(() => {})
      throw new Error(`Unexpected request: ${url}`)
    })
    const pageUser = userEvent.setup()
    const { container } = renderWithApp(
      <Routes>
        <Route path="/admin/community-clustering" element={<AdminCommunityClustering />} />
      </Routes>,
      { route: '/admin/community-clustering' }
    )

    const submit = await screen.findByRole('button', { name: /提交聚类任务/ })
    await pageUser.click(submit)
    expect(await screen.findByText('失败代码：CLUSTERING_EXECUTION_FAILED')).toBeInTheDocument()
    expect(screen.getByText('聚类计算失败')).toBeInTheDocument()
    expect(container.textContent).not.toContain('internal stack trace')

    await pageUser.click(screen.getByRole('button', { name: /提交聚类任务/ }))
    await waitFor(() => expect(postCount).toBe(2))
  })

  it('409 与 503 均不重放 POST，且历史社区查询仍可使用', async () => {
    let postCount = 0
    fetch.mockImplementation((url, options) => {
      if (url === '/api/auth/me') return Promise.resolve(me(admin))
      if (url === '/api/auth/csrf') return Promise.resolve(csrf())
      if (url === '/api/v1/admin/community-clustering/runs' && options.method === 'POST') {
        postCount += 1
        const error = postCount === 1
          ? { status: 409, code: 'RUN_CONFLICT', message: '已有任务' }
          : { status: 503, code: 'CLUSTERING_SERVICE_UNAVAILABLE', message: '执行关闭' }
        return Promise.resolve(jsonResponse({
          code: error.code, message: error.message, details: {}
        }, { status: error.status }))
      }
      if (url === '/api/v1/community-clustering/latest') {
        return Promise.resolve(jsonResponse(latest))
      }
      if (url === '/api/v1/community-clustering/me') {
        return Promise.resolve(jsonResponse(membership))
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    const pageUser = userEvent.setup()
    renderWithApp(
      <Routes>
        <Route path="/admin/community-clustering" element={(
          <>
            <AdminCommunityClustering />
            <Link to="/community">打开历史社区结果</Link>
          </>
        )} />
        <Route path="/community" element={<CommunityClusters />} />
      </Routes>,
      { route: '/admin/community-clustering' }
    )

    await pageUser.click(await screen.findByRole('button', { name: /提交聚类任务/ }))
    expect(await screen.findByText(/已存在正在处理的聚类任务/)).toBeInTheDocument()
    expect(postCount).toBe(1)

    await pageUser.click(screen.getByRole('button', { name: /提交聚类任务/ }))
    expect(await screen.findByText(/聚类执行功能当前关闭/)).toBeInTheDocument()
    expect(postCount).toBe(2)

    await pageUser.click(screen.getByRole('link', { name: '打开历史社区结果' }))
    expect(await screen.findByText('cc-integration-1')).toBeInTheDocument()
    expect(postCount).toBe(2)
  })

  it('受保护页面 Session 过期后清除用户并跳转登录', async () => {
    let latestCount = 0
    fetch.mockImplementation((url) => {
      if (url === '/api/auth/me') return Promise.resolve(me(student))
      if (url === '/api/v1/community-clustering/latest') {
        latestCount += 1
        return Promise.resolve(latestCount === 1 ? jsonResponse(latest) : authRequired())
      }
      if (url === '/api/v1/community-clustering/me') {
        return Promise.resolve(jsonResponse(membership))
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    const pageUser = userEvent.setup()
    renderWithApp(
      <Routes>
        <Route path="/" element={<Login />} />
        <Route path="/community" element={<CommunityClusters />} />
      </Routes>,
      { route: '/community' }
    )

    expect(await screen.findByText('cc-integration-1')).toBeInTheDocument()
    const refresh = screen.getByRole('button', { name: /刷新/ })
    await waitFor(() => expect(refresh).not.toBeDisabled())
    await pageUser.click(refresh)

    expect(await screen.findByPlaceholderText('学号/工号')).toBeInTheDocument()
    expect(screen.queryByText('cc-integration-1')).not.toBeInTheDocument()
  })
})
