import { describe, expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import CommunityClusters from '../../src/pages/CommunityClusters'
import { renderWithApp } from '../helpers/renderWithApp'

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' }
  })
}

const user = {
  id: 'student-1', name: '测试学生', role: 'student', interests: [], availableTime: []
}
const meUser = () => jsonResponse({ success: true, data: user })
const noRun = () => jsonResponse({
  code: 'NO_SUCCESSFUL_RUN', message: '当前还没有可用的社区聚类结果', details: {}
}, 404)

const latest = {
  run: {
    runId: 'run-1',
    version: 'cc-20260721-0001',
    algorithm: 'KMEANS',
    clusterCount: 2,
    sampleCount: 3,
    finishedAt: '2026-07-21T04:00:00Z'
  },
  communities: [
    {
      communityId: 'community-0',
      clusterNo: 0,
      name: '社区 1',
      description: '主要兴趣：AI、编程',
      memberCount: 2,
      topInterests: ['AI', '编程'],
      color: '#1677FF',
      points: [
        { pointId: 'point-current', x: 18.2, y: 74.6, currentUser: true },
        { pointId: 'point-other', x: 29.4, y: 81, currentUser: false }
      ]
    },
    {
      communityId: 'community-1',
      clusterNo: 1,
      name: '社区 2',
      description: null,
      memberCount: 1,
      topInterests: ['羽毛球'],
      color: 'url(javascript:alert(1))',
      points: [{ pointId: 'point-3', x: 91, y: 12.5, currentUser: false }]
    }
  ]
}

const membership = {
  runId: 'run-1',
  version: 'cc-20260721-0001',
  membership: {
    communityId: 'community-0',
    clusterNo: 0,
    communityName: '社区 1',
    color: '#1677FF',
    pointId: 'point-current',
    x: 18.2,
    y: 74.6,
    distanceToCenter: 0.83
  }
}

function renderPage() {
  return renderWithApp(
    <Routes>
      <Route path="/community" element={<CommunityClusters />} />
      <Route path="/" element={<div>登录页面</div>} />
    </Routes>,
    { route: '/community' }
  )
}

describe('社区聚类查询页面', () => {
  it('并行加载 latest/me，显示匿名散点、社区摘要和个人归属', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockResolvedValueOnce(jsonResponse(latest))
      .mockResolvedValueOnce(jsonResponse(membership))

    const { container } = renderPage()

    expect(await screen.findByText('cc-20260721-0001')).toBeInTheDocument()
    expect(screen.getByText(/你属于/)).toBeInTheDocument()
    expect(screen.getByText('AI')).toBeInTheDocument()
    expect(screen.getByText('编程')).toBeInTheDocument()
    expect(screen.getByText('羽毛球')).toBeInTheDocument()
    expect(screen.getByRole('img', { name: /匿名散点图/ })).toBeInTheDocument()

    const currentPoint = container.querySelector('circle[data-current-user="true"]')
    const otherPoint = container.querySelector('circle[data-current-user="false"]')
    expect(currentPoint).toHaveAttribute('r', '2.3')
    expect(currentPoint).toHaveAttribute('stroke', '#111827')
    expect(otherPoint).toHaveAttribute('r', '1.45')

    const calls = fetch.mock.calls.slice(1)
    expect(calls.map(call => call[0])).toEqual([
      '/api/v1/community-clustering/latest',
      '/api/v1/community-clustering/me'
    ])
    calls.forEach(([, options]) => {
      expect(options.credentials).toBe('include')
      expect(options.body).toBeUndefined()
    })
  })

  it('非法社区颜色使用固定 fallback', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockResolvedValueOnce(jsonResponse(latest))
      .mockResolvedValueOnce(jsonResponse(membership))
    const { container } = renderPage()
    await screen.findByText('cc-20260721-0001')

    const fallbackPoint = [...container.querySelectorAll('circle')]
      .find(point => point.querySelector('title')?.textContent.includes('91'))
    expect(fallbackPoint).toHaveAttribute('fill', '#1677ff')
    expect(container.innerHTML).not.toContain('javascript:')
  })

  it('页面不显示身份字段、内部运行字段或其他用户资料', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockResolvedValueOnce(jsonResponse(latest))
      .mockResolvedValueOnce(jsonResponse(membership))
    const { container } = renderPage()
    await screen.findByText('cc-20260721-0001')

    const text = container.textContent
    expect(text).not.toContain('student-1')
    expect(text).not.toContain('userId')
    expect(text).not.toContain('college')
    expect(text).not.toContain('grade')
    expect(text).not.toContain('createdBy')
    expect(text).not.toContain('metrics')
    expect(text).not.toContain('failure')
    expect(text).not.toContain('point-other')
  })

  it('NO_SUCCESSFUL_RUN 显示友好空状态而不是系统错误', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockResolvedValueOnce(noRun())
      .mockResolvedValueOnce(noRun())
    renderPage()

    expect((await screen.findAllByText(/当前还没有可用的社区聚类结果/)).length)
      .toBeGreaterThanOrEqual(1)
    expect(screen.queryByText('社区结果加载失败')).not.toBeInTheDocument()
  })

  it('membership=null 与没有成功运行使用不同说明', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockResolvedValueOnce(jsonResponse(latest))
      .mockResolvedValueOnce(jsonResponse({ ...membership, membership: null }))
    renderPage()

    expect(await screen.findByText('当前用户未进入本次聚类样本')).toBeInTheDocument()
    expect(screen.getByText('cc-20260721-0001')).toBeInTheDocument()
  })

  it('网络错误与无数据状态区分，并保留另一请求成功内容', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockRejectedValueOnce(new TypeError('offline'))
      .mockResolvedValueOnce(jsonResponse(membership))
    renderPage()

    expect(await screen.findByText('社区结果加载失败')).toBeInTheDocument()
    expect(screen.getByText(/你属于/)).toBeInTheDocument()
    expect(screen.queryByText(/管理员运行聚类后可查看/)).not.toBeInTheDocument()
  })

  it('刷新取消旧请求且按钮禁用避免请求堆积', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockResolvedValueOnce(jsonResponse(latest))
      .mockResolvedValueOnce(jsonResponse(membership))
      .mockImplementation((url, options) => new Promise((_resolve, reject) => {
        options.signal.addEventListener('abort', () => {
          reject(new DOMException('aborted', 'AbortError'))
        })
      }))
    const pageUser = userEvent.setup()
    renderPage()
    await screen.findByText('cc-20260721-0001')

    const refresh = screen.getByRole('button', { name: /刷\s*新/ })
    await pageUser.click(refresh)
    expect(refresh).toBeDisabled()
    expect(fetch).toHaveBeenCalledTimes(5)
  })

  it('组件卸载会取消 latest 与 me 请求', async () => {
    const signals = []
    fetch
      .mockResolvedValueOnce(meUser())
      .mockImplementation((url, options) => {
        signals.push(options.signal)
        return new Promise(() => {})
      })
    const view = renderPage()
    await waitFor(() => expect(signals).toHaveLength(2))

    view.unmount()

    expect(signals.every(signal => signal.aborted)).toBe(true)
  })

  it('Session 过期通过全局机制返回登录页', async () => {
    fetch
      .mockResolvedValueOnce(meUser())
      .mockResolvedValueOnce(jsonResponse({
        code: 'AUTHENTICATION_REQUIRED', message: '请先登录', details: {}
      }, 401))
      .mockResolvedValueOnce(jsonResponse(membership))
    renderPage()

    expect(await screen.findByText('登录页面')).toBeInTheDocument()
  })

  it('生产页面不使用 dangerouslySetInnerHTML', () => {
    expect(CommunityClusters.toString()).not.toContain('dangerouslySetInnerHTML')
  })
})
