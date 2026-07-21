import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getCsrfToken, clearCsrfToken, csrfPost } from '../../src/api/csrf'
import { getCurrentUser, login, logout, register } from '../../src/api/auth'
import {
  getClusteringRun,
  getLatestClustering,
  getMyClustering,
  resolveRunLocation,
  triggerClustering
} from '../../src/api/clustering'

function jsonResponse(data, { status = 200, headers = {} } = {}) {
  return new Response(data === null ? null : JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers }
  })
}

const csrfResponse = {
  token: 'csrf-secret',
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf'
}

describe('CSRF 与 API 服务模块', () => {
  beforeEach(() => {
    clearCsrfToken()
    vi.stubGlobal('fetch', vi.fn())
  })

  it('并发 CSRF 获取复用同一请求 Promise', async () => {
    fetch.mockResolvedValue(jsonResponse(csrfResponse))

    const [first, second] = await Promise.all([getCsrfToken(), getCsrfToken()])

    expect(first).toEqual(csrfResponse)
    expect(second).toBe(first)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('unsafe POST 先获取 CSRF 并使用后端 headerName', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(jsonResponse({ ok: true }))

    await csrfPost('/api/example', { json: { value: 1 } })

    expect(fetch.mock.calls[0][0]).toBe('/api/auth/csrf')
    const [, postOptions] = fetch.mock.calls[1]
    expect(postOptions.method).toBe('POST')
    expect(postOptions.credentials).toBe('include')
    expect(postOptions.headers.get('X-CSRF-TOKEN')).toBe('csrf-secret')
  })

  it('CSRF 错误清除缓存且不自动重试 POST', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(jsonResponse({
        code: 'CSRF_TOKEN_INVALID',
        message: '安全校验失败',
        details: {}
      }, { status: 403 }))
      .mockResolvedValueOnce(jsonResponse({ ...csrfResponse, token: 'new-token' }))

    await expect(csrfPost('/api/example', { json: {} })).rejects.toMatchObject({
      code: 'CSRF_TOKEN_INVALID'
    })
    expect(fetch).toHaveBeenCalledTimes(2)

    await getCsrfToken()
    expect(fetch).toHaveBeenCalledTimes(3)
  })

  it('登录请求只发送真实字段，成功后使旧 CSRF 失效', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(jsonResponse({ success: true, data: { id: 'u1' } }))
      .mockResolvedValueOnce(jsonResponse({ ...csrfResponse, token: 'rotated' }))

    await login({ id: 'u1', password: 'not-logged' })

    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      id: 'u1',
      password: 'not-logged'
    })
    await getCsrfToken()
    expect(fetch).toHaveBeenCalledTimes(3)
  })

  it('登出成功后使 CSRF 失效', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(jsonResponse({ success: true, data: null }))
      .mockResolvedValueOnce(jsonResponse({ ...csrfResponse, token: 'after-logout' }))

    await logout()
    await getCsrfToken()

    expect(fetch).toHaveBeenCalledTimes(3)
    expect(fetch.mock.calls[1][0]).toBe('/api/auth/logout')
  })

  it('注册请求显式排除 role', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(jsonResponse({ success: true, data: { id: 'u2' } }, { status: 201 }))

    await register({
      id: 'u2',
      password: 'password-123',
      name: '用户',
      role: 'admin',
      college: '软件学院',
      grade: '2026级'
    })

    const body = JSON.parse(fetch.mock.calls[1][1].body)
    expect(body).not.toHaveProperty('role')
    expect(body).toEqual({
      id: 'u2',
      password: 'password-123',
      name: '用户',
      college: '软件学院',
      grade: '2026级',
      interests: [],
      availableTime: []
    })
  })

  it('/auth/me 解包可信用户 DTO', async () => {
    fetch.mockResolvedValue(jsonResponse({
      success: true,
      message: 'success',
      data: { id: 'u1', name: '用户', role: 'student' }
    }))

    await expect(getCurrentUser()).resolves.toMatchObject({ id: 'u1', role: 'student' })
    expect(fetch.mock.calls[0][0]).toBe('/api/auth/me')
  })

  it('聚类 GET 使用固定 Spring 相对路径且不发送 userId', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse({ run: {}, communities: [] }))
      .mockResolvedValueOnce(jsonResponse({ membership: null }))

    await Promise.all([getLatestClustering(), getMyClustering()])

    expect(fetch.mock.calls.map(call => call[0])).toEqual([
      '/api/v1/community-clustering/latest',
      '/api/v1/community-clustering/me'
    ])
    fetch.mock.calls.forEach(([, options]) => {
      expect(options.credentials).toBe('include')
      expect(options.body).toBeUndefined()
    })
  })

  it('聚类触发只发送 clusterCount 并读取 202 Location', async () => {
    fetch
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(jsonResponse({
        runId: 'run-1',
        status: 'PENDING',
        clusterCount: 2
      }, {
        status: 202,
        headers: { Location: '/api/v1/admin/community-clustering/runs/run-1' }
      }))

    const result = await triggerClustering(2)

    expect(result).toMatchObject({
      runPath: '/api/v1/admin/community-clustering/runs/run-1',
      protocolWarning: false
    })
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ clusterCount: 2 })
  })

  it('外部或缺失 Location 使用安全已知路径', () => {
    expect(resolveRunLocation('https://evil.example/run-1', 'run-1')).toEqual({
      path: '/api/v1/admin/community-clustering/runs/run-1',
      protocolWarning: true
    })
    expect(resolveRunLocation(null, 'run-2')).toEqual({
      path: '/api/v1/admin/community-clustering/runs/run-2',
      protocolWarning: true
    })
  })

  it('运行详情拒绝未知路径且从不访问 Python URL', async () => {
    await expect(getClusteringRun('http://localhost:8000/internal/v1/clustering/run'))
      .rejects.toMatchObject({ code: 'INVALID_RUN_LOCATION' })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('API 服务不写 localStorage', async () => {
    fetch.mockResolvedValue(jsonResponse({ run: {}, communities: [] }))

    await getLatestClustering()

    expect(localStorage.setItem).not.toHaveBeenCalled()
  })
})

