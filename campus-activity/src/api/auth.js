import { request } from './http'
import { clearCsrfToken, csrfPost, getCsrfToken } from './csrf'

function unwrapApiResponse(result) {
  return result.data?.data ?? null
}

export const getCsrf = getCsrfToken

export async function login(credentials, { signal } = {}) {
  const result = await csrfPost('/api/auth/login', {
    json: {
      id: credentials.id,
      password: credentials.password
    },
    signal
  })
  clearCsrfToken()
  return unwrapApiResponse(result)
}

export async function register(registration, { signal } = {}) {
  const result = await csrfPost('/api/auth/register', {
    json: {
      id: registration.id,
      password: registration.password,
      name: registration.name,
      college: registration.college || null,
      grade: registration.grade || null,
      interests: registration.interests || [],
      availableTime: registration.availableTime || []
    },
    signal
  })
  return unwrapApiResponse(result)
}

export async function getCurrentUser({ signal } = {}) {
  const result = await request('/api/auth/me', { signal })
  return unwrapApiResponse(result)
}

export async function logout({ signal } = {}) {
  const result = await csrfPost('/api/auth/logout', { signal })
  clearCsrfToken()
  return unwrapApiResponse(result)
}

