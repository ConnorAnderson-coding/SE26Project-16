import { ApiError, request } from './http'

let cachedToken = null
let pendingTokenRequest = null

function validateToken(data) {
  if (!data || typeof data.token !== 'string' || typeof data.headerName !== 'string') {
    throw new ApiError({
      status: 200,
      code: 'INVALID_RESPONSE',
      message: '服务器返回了无效的安全校验信息'
    })
  }
  return data
}

export function clearCsrfToken() {
  cachedToken = null
  pendingTokenRequest = null
}

export function getCsrfToken({ signal } = {}) {
  if (cachedToken) return Promise.resolve(cachedToken)
  if (pendingTokenRequest) return pendingTokenRequest

  pendingTokenRequest = request('/api/auth/csrf', { signal })
    .then(({ data }) => {
      cachedToken = validateToken(data)
      return cachedToken
    })
    .finally(() => {
      pendingTokenRequest = null
    })

  return pendingTokenRequest
}

export async function csrfPost(path, { json, signal } = {}) {
  const csrf = await getCsrfToken({ signal })
  try {
    return await request(path, {
      method: 'POST',
      json,
      signal,
      headers: { [csrf.headerName]: csrf.token }
    })
  } catch (error) {
    if (error instanceof ApiError && error.code === 'CSRF_TOKEN_INVALID') {
      clearCsrfToken()
    }
    throw error
  }
}

