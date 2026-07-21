import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  request,
  subscribeAuthenticationInvalid
} from '../../src/api/http'

function jsonResponse(data, { status = 200, headers = {} } = {}) {
  return new Response(data === null ? null : JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers }
  })
}

describe('统一 HTTP client', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  it('GET 默认携带凭据和 Accept，且不发送 JSON Content-Type', async () => {
    fetch.mockResolvedValue(jsonResponse({ ok: true }))

    await request('/api/example')

    const [, options] = fetch.mock.calls[0]
    expect(options.credentials).toBe('include')
    expect(options.method).toBe('GET')
    expect(options.headers.get('Accept')).toBe('application/json')
    expect(options.headers.has('Content-Type')).toBe(false)
    expect(options.body).toBeUndefined()
  })

  it('POST JSON 设置 Content-Type 并原样传递 AbortSignal', async () => {
    fetch.mockResolvedValue(jsonResponse({ accepted: true }, { status: 202 }))
    const controller = new AbortController()

    await request('/api/example', {
      method: 'POST',
      json: { clusterCount: 2 },
      signal: controller.signal
    })

    const [, options] = fetch.mock.calls[0]
    expect(options.headers.get('Content-Type')).toBe('application/json')
    expect(options.body).toBe('{"clusterCount":2}')
    expect(options.signal).toBe(controller.signal)
  })

  it('读取 JSON、状态、Header 和 Location', async () => {
    fetch.mockResolvedValue(jsonResponse(
      { runId: 'run-1' },
      { status: 202, headers: { Location: '/api/runs/run-1', 'X-Test': 'yes' } }
    ))

    const result = await request('/api/example')

    expect(result.data).toEqual({ runId: 'run-1' })
    expect(result.status).toBe(202)
    expect(result.location).toBe('/api/runs/run-1')
    expect(result.headers.get('X-Test')).toBe('yes')
  })

  it('安全处理 204 和空 body', async () => {
    fetch
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(new Response('', { status: 200 }))

    await expect(request('/api/empty')).resolves.toMatchObject({ data: null })
    await expect(request('/api/blank')).resolves.toMatchObject({ data: null })
  })

  it('解析固定业务错误体为 ApiError', async () => {
    fetch.mockResolvedValue(jsonResponse({
      code: 'RUN_CONFLICT',
      message: '已有任务',
      details: { ignored: true }
    }, { status: 409 }))

    await expect(request('/api/example')).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'RUN_CONFLICT',
      message: '已有任务',
      details: { ignored: true }
    })
  })

  it('非 JSON 500 不暴露原始 HTML', async () => {
    fetch.mockResolvedValue(new Response('<h1>stack trace</h1>', {
      status: 500,
      headers: { 'Content-Type': 'text/html' }
    }))

    const error = await request('/api/example').catch(value => value)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.code).toBe('HTTP_ERROR')
    expect(error.message).toBe('系统暂时无法处理请求')
    expect(error.message).not.toContain('stack trace')
  })

  it('成功响应不是 JSON 时按协议错误处理', async () => {
    fetch.mockResolvedValue(new Response('unexpected', {
      status: 200,
      headers: { 'Content-Type': 'text/plain' }
    }))

    await expect(request('/api/example')).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
      status: 200
    })
  })

  it('网络错误与 HTTP 错误区分', async () => {
    fetch.mockRejectedValue(new TypeError('offline'))

    await expect(request('/api/example')).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR'
    })
  })

  it('AbortError 保持取消语义', async () => {
    const abortError = new DOMException('aborted', 'AbortError')
    fetch.mockRejectedValue(abortError)

    await expect(request('/api/example')).rejects.toBe(abortError)
  })

  it('仅 AUTHENTICATION_REQUIRED 通知认证失效', async () => {
    const listener = vi.fn()
    const unsubscribe = subscribeAuthenticationInvalid(listener)
    fetch
      .mockResolvedValueOnce(jsonResponse({ code: 'INVALID_CREDENTIALS', message: '错误' }, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse({ code: 'ACCESS_DENIED', message: '无权' }, { status: 403 }))
      .mockResolvedValueOnce(jsonResponse({ code: 'AUTHENTICATION_REQUIRED', message: '登录' }, { status: 401 }))

    await request('/api/one').catch(() => {})
    await request('/api/two').catch(() => {})
    expect(listener).not.toHaveBeenCalled()
    await request('/api/three').catch(() => {})
    expect(listener).toHaveBeenCalledTimes(1)
    unsubscribe()
  })
})

