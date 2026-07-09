import http, { setToken, setStoredUser } from './http'

export async function login(userId, password) {
  const data = await http.post('/auth/login', { userId, password })
  setToken(data.token)
  setStoredUser(data.user)
  return data
}

export async function register(payload) {
  const data = await http.post('/auth/register', payload)
  setToken(data.token)
  setStoredUser(data.user)
  return data
}

export function logout() {
  setToken(null)
  setStoredUser(null)
}
