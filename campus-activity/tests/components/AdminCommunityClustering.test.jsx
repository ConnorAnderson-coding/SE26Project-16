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
const csrfResponse = {
  token: 'csrf-value', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf'
}

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

function runPage(items = [], overrides = {}) {
  return {
    items,
    page: 0,
    size: 20,
    totalElements: items.length,
    totalPages: items.length ? 1 : 0,
    ...overrides
  }
}

function latestResponse() {
  return {
    run: {
      runId: 'run-latest', version: 'latest-v1', algorithm: 'KMEANS',
      clusterCount: 2, sampleCount: 2, finishedAt: '2026-07-21T04:00:03Z'
    },
    communities: [
      {
        communityId: 'community-1', clusterNo: 0, name: '社区 1',
        memberCount: 2, color: '#1677FF', points: []
      },
      {
        communityId: 'community-2', clusterNo: 1, name: '社区 2',
        memberCount: 1, color: '#52C41A', points: []
      }
    ]
  }
}

function memberPage(communityId = 'community-1', page = 0, overrides = {}) {
  return {
    community: {
      communityId,
      runId: 'run-latest',
      clusterNo: communityId === 'community-1' ? 0 : 1,
      name: communityId === 'community-1' ? '社区 1' : '社区 2',
      color: '#1677FF',
      memberCount: 2
    },
    items: [{
      userId: `user-${page + 1}`,
      name: page === 0 ? '张同学' : '李同学',
      college: '软件学院',
      grade: '2026',
      pointId: `point-${page + 1}`,
      x: 12.345,
      y: 45.678,
      distanceToCenter: 0.489
    }],
    page,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    ...overrides
  }
}

function accepted(location = '/api/v1/admin/community-clustering/runs/run-new') {
  return jsonResponse({
    runId: 'run-new', version: 'new-version', algorithm: 'KMEANS',
    clusterCount: 2, randomState: 42, status: 'PENDING',
    createdAt: '2026-07-21T05:00:00Z'
  }, { status: 202, headers: location ? { Location: location } : {} })
}

function errorResponse(status, code, message = 'internal') {
  return jsonResponse({ code, message, details: {} }, { status })
}

function installApi({
  role = 'admin',
  pages = { 0: runPage() },
  latest = null,
  details = {},
  members = {},
  post = accepted()
} = {}) {
  const detailIndexes = {}
  fetch.mockImplementation((input, options = {}) => {
    const url = String(input)
    const method = options.method || 'GET'
    if (url === '/api/auth/me') {
      const user = role === 'admin' ? admin : { id: `${role}-1`, name: role, role }
      return Promise.resolve(jsonResponse({ success: true, data: user }))
    }
    if (url === '/api/auth/csrf') return Promise.resolve(jsonResponse(csrfResponse))
    if (url === '/api/v1/admin/community-clustering/runs' && method === 'POST') {
      return Promise.resolve(typeof post === 'function' ? post(options) : post)
    }
    if (url.startsWith('/api/v1/admin/community-clustering/runs?')) {
      const page = Number(new URL(url, 'http://localhost').searchParams.get('page'))
      const value = pages[page]
      if (typeof value === 'function') return Promise.resolve(value(options))
      if (value instanceof Response) return Promise.resolve(value)
      return Promise.resolve(jsonResponse(value || runPage([], { page })))
    }
    if (url === '/api/v1/community-clustering/latest') {
      return Promise.resolve(latest instanceof Response
        ? latest
        : latest
          ? jsonResponse(latest)
          : errorResponse(404, 'NO_SUCCESSFUL_RUN'))
    }
    if (url.includes('/communities/') && url.includes('/members?')) {
      const match = url.match(/communities\/([^/]+)\/members/)
      const communityId = decodeURIComponent(match[1])
      const page = Number(new URL(url, 'http://localhost').searchParams.get('page'))
      const value = members[communityId]
      if (typeof value === 'function') return value({ page, options })
      if (value instanceof Response) return Promise.resolve(value)
      return Promise.resolve(jsonResponse(value || memberPage(communityId, page)))
    }
    const detailMatch = url.match(/\/runs\/([^/?#]+)$/)
    if (detailMatch) {
      const runId = decodeURIComponent(detailMatch[1])
      const value = details[runId]
      if (typeof value === 'function') return value(options)
      const sequence = Array.isArray(value) ? value : [value || runDetail('SUCCESS', { runId })]
      const index = detailIndexes[runId] || 0
      detailIndexes[runId] = index + 1
      return Promise.resolve(jsonResponse(sequence[Math.min(index, sequence.length - 1)]))
    }
    throw new Error(`Unexpected fetch: ${method} ${url}`)
  })
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

afterEach(() => {
  clearCsrfToken()
  vi.useRealTimers()
})

describe('管理员社区聚类闭环', () => {
  it.each(['student', 'teacher'])('%s 无法进入且不会加载管理员数据', async (role) => {
    installApi({ role })
    renderPage()

    expect(await screen.findByText('你没有访问管理员功能的权限')).toBeInTheDocument()
    expect(fetch.mock.calls.some(call => String(call[0]).includes('/api/v1/admin/'))).toBe(false)
  })

  it('刷新页面加载第 0 页和 latest，但不重新 POST', async () => {
    installApi({ latest: latestResponse() })
    renderPage()

    await waitForForm()
    await screen.findByRole('button', { name: /查看社区成员 社区 1/ })
    expect(fetch.mock.calls.some(call => call[0] === '/api/v1/admin/community-clustering/runs?page=0&size=20')).toBe(true)
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(0)
  })

  it('clusterCount 默认 2 且阻止非法整数', async () => {
    installApi()
    const pageUser = userEvent.setup()
    renderPage()
    await waitForForm()
    const input = screen.getByRole('spinbutton', { name: '社区数量' })
    expect(input).toHaveValue('2')
    await pageUser.clear(input)
    await pageUser.type(input, '1')
    await pageUser.click(screen.getByRole('button', { name: /提交聚类任务/ }))
    expect(await screen.findByText('社区数量必须是至少为 2 的整数')).toBeInTheDocument()
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(0)
  })

  it.each(['PENDING', 'RUNNING'])('从历史 %s 自动恢复串行轮询并在 SUCCESS 后刷新', async (status) => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const summary = runDetail(status)
    installApi({
      pages: { 0: runPage([summary]) },
      details: { 'run-1': [runDetail(status), runDetail('SUCCESS')] }
    })
    renderPage()
    await act(async () => { await vi.runOnlyPendingTimersAsync() })

    expect(screen.getAllByText(new RegExp(`${status}（`)).length).toBeGreaterThanOrEqual(1)
    await act(async () => { await vi.advanceTimersByTimeAsync(2000) })
    expect(screen.getAllByText(/SUCCESS（已成功）/).length).toBeGreaterThanOrEqual(1)
    expect(fetch.mock.calls.filter(call => call[0] === '/api/v1/admin/community-clustering/runs?page=0&size=20').length)
      .toBeGreaterThanOrEqual(2)
  })

  it('无活动运行时选择最新终态且不会重复轮询', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    installApi({
      pages: { 0: runPage([runDetail('SUCCESS')]) },
      details: { 'run-1': runDetail('SUCCESS') }
    })
    renderPage()
    await act(async () => { await vi.runOnlyPendingTimersAsync() })
    const before = fetch.mock.calls.filter(call => call[0].endsWith('/run-1')).length
    await act(async () => { await vi.advanceTimersByTimeAsync(10000) })

    expect(screen.getAllByText(/SUCCESS（已成功）/).length).toBeGreaterThanOrEqual(1)
    expect(fetch.mock.calls.filter(call => call[0].endsWith('/run-1'))).toHaveLength(before)
  })

  it('运行历史可翻页并展示固定摘要字段', async () => {
    const page0Run = runDetail('SUCCESS')
    const page1Run = runDetail('FAILED', { runId: 'run-older', version: 'older-v', createdBy: 'older-admin' })
    installApi({
      pages: {
        0: runPage([page0Run], { totalElements: 21, totalPages: 2 }),
        1: runPage([page1Run], { page: 1, totalElements: 21, totalPages: 2 })
      },
      details: { 'run-1': page0Run, 'run-older': page1Run }
    })
    const pageUser = userEvent.setup()
    renderPage()
    await screen.findAllByText('cc-20260721-0001')
    await pageUser.click(screen.getAllByRole('button', { name: '下一页' })[0])

    expect(await screen.findByText('older-v')).toBeInTheDocument()
    expect(screen.getByText('older-admin')).toBeInTheDocument()
    expect(screen.getByText('第 2 / 2 页')).toBeInTheDocument()
  })

  it('旧运行详情请求不会覆盖新的手动选择', async () => {
    let resolveOld
    let oldSignal
    const oldPromise = new Promise(resolve => { resolveOld = resolve })
    const first = runDetail('SUCCESS', { runId: 'run-old', version: 'old-version' })
    const second = runDetail('FAILED', { runId: 'run-newer', version: 'newer-version' })
    installApi({
      pages: { 0: runPage([first, second]) },
      details: {
        'run-old': options => {
          oldSignal = options.signal
          return oldPromise
        },
        'run-newer': second
      }
    })
    const pageUser = userEvent.setup()
    renderPage()
    await screen.findByRole('button', { name: '选择运行 run-newer' })
    await waitFor(() => expect(oldSignal).toBeDefined())
    await pageUser.click(screen.getByRole('button', { name: '选择运行 run-newer' }))
    expect((await screen.findAllByText('newer-version')).length).toBeGreaterThanOrEqual(1)
    expect(oldSignal.aborted).toBe(true)

    resolveOld(jsonResponse(first))
    await act(async () => {})
    expect(screen.getAllByText('newer-version').length).toBeGreaterThanOrEqual(1)
  })

  it('新提交只 POST 一次，刷新历史并选择新运行', async () => {
    installApi({
      pages: { 0: runPage() },
      details: { 'run-new': runDetail('SUCCESS', { runId: 'run-new', version: 'new-version' }) }
    })
    const pageUser = userEvent.setup()
    renderPage()
    await waitForForm()
    await pageUser.click(screen.getByRole('button', { name: /提交聚类任务/ }))

    expect(await screen.findByText('聚类任务已接受')).toBeInTheDocument()
    expect(await screen.findByText('new-version')).toBeInTheDocument()
    const posts = fetch.mock.calls.filter(([, options]) => options?.method === 'POST')
    expect(posts).toHaveLength(1)
    expect(JSON.parse(posts[0][1].body)).toEqual({ clusterCount: 2 })
    expect(posts[0][1].headers.get('X-CSRF-TOKEN')).toBe('csrf-value')
  })

  it.each([
    [409, 'RUN_CONFLICT', '已存在正在处理的聚类任务'],
    [503, 'CLUSTERING_SERVICE_UNAVAILABLE', '聚类执行功能当前关闭']
  ])('%s 提交错误不自动重试', async (status, code, expected) => {
    installApi({ post: errorResponse(status, code) })
    const pageUser = userEvent.setup()
    renderPage()
    await waitForForm()
    await pageUser.click(screen.getByRole('button', { name: /提交聚类任务/ }))

    expect(await screen.findByText(new RegExp(expected))).toBeInTheDocument()
    expect(fetch.mock.calls.filter(([, options]) => options?.method === 'POST')).toHaveLength(1)
  })

  it('运行列表 401 进入登录页，403 保留认证并可重试', async () => {
    installApi({ pages: { 0: errorResponse(401, 'AUTHENTICATION_REQUIRED') } })
    renderPage()
    expect(await screen.findByText('登录页面')).toBeInTheDocument()

    installApi({ pages: { 0: errorResponse(403, 'ACCESS_DENIED') } })
    renderPage()
    expect(await screen.findByText('你没有查询运行历史的权限。')).toBeInTheDocument()
    expect(screen.getAllByText('管理员').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByRole('button', { name: '重试运行历史' })).toBeInTheDocument()
  })

  it('latest 社区点击后加载管理员成员并格式化数值', async () => {
    installApi({ latest: latestResponse() })
    const pageUser = userEvent.setup()
    const { container } = renderPage()
    await pageUser.click(await screen.findByRole('button', { name: /查看社区成员 社区 1/ }))

    expect(await screen.findByRole('table', { name: '管理员社区成员' })).toBeInTheDocument()
    expect(screen.getByText('张同学')).toBeInTheDocument()
    expect(screen.getByText('软件学院')).toBeInTheDocument()
    expect(screen.getByText('12.35')).toBeInTheDocument()
    expect(screen.getByText('45.68')).toBeInTheDocument()
    expect(screen.getByText('0.49')).toBeInTheDocument()
    expect(container.textContent).not.toMatch(/passwordHash|authorities|friends/)
  })

  it('成员可翻页且 pointId 不会被标成用户 ID', async () => {
    installApi({ latest: latestResponse() })
    const pageUser = userEvent.setup()
    renderPage()
    await pageUser.click(await screen.findByRole('button', { name: /查看社区成员 社区 1/ }))
    await screen.findByText('张同学')
    await pageUser.click(screen.getAllByRole('button', { name: '下一页' }).at(-1))

    expect(await screen.findByText('李同学')).toBeInTheDocument()
    expect(screen.getByText('user-2')).toBeInTheDocument()
    expect(screen.getByText('point-2')).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: '用户 ID' })).toBeInTheDocument()
    expect(screen.getByRole('columnheader', { name: '点标识' })).toBeInTheDocument()
  })

  it('社区切换取消旧成员请求且旧响应不覆盖', async () => {
    let resolveFirst
    let firstSignal
    const firstPromise = new Promise(resolve => { resolveFirst = resolve })
    installApi({
      latest: latestResponse(),
      members: {
        'community-1': ({ options }) => {
          firstSignal = options.signal
          return firstPromise
        },
        'community-2': memberPage('community-2', 0, {
          items: [{
            userId: 'new-user', name: '新社区成员', college: null, grade: null,
            pointId: 'new-point', x: 1, y: 2, distanceToCenter: 0.1
          }]
        })
      }
    })
    const pageUser = userEvent.setup()
    renderPage()
    await pageUser.click(await screen.findByRole('button', { name: /查看社区成员 社区 1/ }))
    await waitFor(() => expect(firstSignal).toBeDefined())
    await pageUser.click(screen.getByRole('button', { name: /查看社区成员 社区 2/ }))
    expect(await screen.findByText('新社区成员')).toBeInTheDocument()
    expect(firstSignal.aborted).toBe(true)

    resolveFirst(jsonResponse(memberPage('community-1')))
    await act(async () => {})
    expect(screen.getByText('新社区成员')).toBeInTheDocument()
    expect(screen.queryByText('张同学')).not.toBeInTheDocument()
  })

  it('缺失身份字段显示安全占位且社区 404 可重试', async () => {
    installApi({
      latest: latestResponse(),
      members: {
        'community-1': memberPage('community-1', 0, {
          items: [{
            userId: 'user-null', name: null, college: '', grade: null,
            pointId: 'point-null', x: 1, y: 2, distanceToCenter: 0.1
          }]
        }),
        'community-2': errorResponse(404, 'COMMUNITY_NOT_FOUND')
      }
    })
    const pageUser = userEvent.setup()
    renderPage()
    await pageUser.click(await screen.findByRole('button', { name: /查看社区成员 社区 1/ }))
    expect((await screen.findAllByText('未提供')).length).toBeGreaterThanOrEqual(3)
    await pageUser.click(screen.getByRole('button', { name: /查看社区成员 社区 2/ }))
    expect(await screen.findByText('指定的社区不存在。')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '重试社区成员' })).toBeInTheDocument()
  })

  it('卸载取消历史、latest、成员和运行请求并清理 timer', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    let detailSignal
    installApi({
      pages: { 0: runPage([runDetail('RUNNING')]) },
      details: {
        'run-1': options => {
          detailSignal = options.signal
          return new Promise(() => {})
        }
      }
    })
    const view = renderPage()
    await act(async () => { await vi.runOnlyPendingTimersAsync() })
    await waitFor(() => expect(detailSignal).toBeDefined())

    view.unmount()
    expect(detailSignal.aborted).toBe(true)
    expect(vi.getTimerCount()).toBe(0)
  })

  it('生产请求不访问 Python、不调用 run communities、不保存恢复状态', async () => {
    installApi({ latest: latestResponse() })
    renderPage()
    await screen.findByRole('button', { name: /查看社区成员 社区 1/ })

    const urls = fetch.mock.calls.map(call => String(call[0]))
    expect(urls.some(url => url.includes('8000') || url.includes('/internal/'))).toBe(false)
    expect(urls.some(url => /runs\/[^/]+\/communities/.test(url))).toBe(false)
    expect(sessionStorage.getItem('run')).toBeNull()
    expect(localStorage.setItem).not.toHaveBeenCalledWith(expect.stringContaining('run'), expect.anything())
  })
})
