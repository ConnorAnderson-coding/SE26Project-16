import http, { setToken, setStoredUser } from './http'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api/v1'

export async function login(userId, password) {
  const data = await http.post('/auth/login', { userId, password })
  setToken(data.token)
  setStoredUser(data.user)
  setAuthProvider('local')
  return data
}

export async function register(payload) {
  const data = await http.post('/auth/register', payload)
  setToken(data.token)
  setStoredUser(data.user)
  setAuthProvider('local')
  return data
}

export function logout() {
  setToken(null)
  setStoredUser(null)
  localStorage.removeItem('campus-activity-auth-provider')
}

export function getAuthProvider() {
  return localStorage.getItem('campus-activity-auth-provider') || 'local'
}

export function setAuthProvider(provider) {
  if (provider) {
    localStorage.setItem('campus-activity-auth-provider', provider)
  } else {
    localStorage.removeItem('campus-activity-auth-provider')
  }
}

export function startJAccountLogin() {
  window.location.href = `${API_BASE_URL}/auth/jaccount/login`
}

export function startJAccountLogout() {
  window.location.href = `${API_BASE_URL}/auth/jaccount/logout`
}
