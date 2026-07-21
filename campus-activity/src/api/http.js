const authenticationInvalidListeners = new Set()

const SAFE_STATUS_MESSAGES = {
  400: '请求格式无效',
  401: '请先登录',
  403: '无权执行此操作',
  404: '请求的资源不存在',
  409: '请求与当前状态冲突',
  500: '系统暂时无法处理请求',
  503: '服务暂时不可用'
}

export class ApiError extends Error {
  constructor({ status = 0, code = 'NETWORK_ERROR', message, details = {}, cause }) {
    super(message || '网络连接失败', { cause })
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details && typeof details === 'object' ? details : {}
  }
}

export function subscribeAuthenticationInvalid(listener) {
  authenticationInvalidListeners.add(listener)
  return () => authenticationInvalidListeners.delete(listener)
}

function notifyAuthenticationInvalid() {
  authenticationInvalidListeners.forEach(listener => listener())
}

function isJsonResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  return contentType.toLowerCase().includes('json')
}

async function readResponseBody(response) {
  if (response.status === 204) return null

  const text = await response.text()
  if (!text) return null
  if (!isJsonResponse(response)) return undefined

  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}

function toHttpError(response, payload) {
  const structured = payload && typeof payload === 'object'
  const code = structured && typeof payload.code === 'string'
    ? payload.code
    : 'HTTP_ERROR'
  const message = structured && typeof payload.message === 'string'
    ? payload.message
    : (SAFE_STATUS_MESSAGES[response.status] || '请求未能完成')
  const details = structured ? payload.details : {}

  return new ApiError({
    status: response.status,
    code,
    message,
    details
  })
}

export async function request(path, {
  method = 'GET',
  json,
  headers: headerValues,
  signal
} = {}) {
  const headers = new Headers(headerValues)
  if (!headers.has('Accept')) headers.set('Accept', 'application/json')

  let body
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(json)
  }

  let response
  try {
    response = await fetch(path, {
      method,
      credentials: 'include',
      headers,
      body,
      signal
    })
  } catch (error) {
    if (error?.name === 'AbortError') throw error
    throw new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      message: '网络连接失败，请检查连接后重试',
      cause: error
    })
  }

  const data = await readResponseBody(response)
  if (!response.ok) {
    const error = toHttpError(response, data)
    if (error.code === 'AUTHENTICATION_REQUIRED') {
      notifyAuthenticationInvalid()
    }
    throw error
  }

  if (data === undefined) {
    throw new ApiError({
      status: response.status,
      code: 'INVALID_RESPONSE',
      message: '服务器返回了无法识别的响应'
    })
  }

  return {
    data,
    status: response.status,
    headers: response.headers,
    location: response.headers.get('Location')
  }
}

