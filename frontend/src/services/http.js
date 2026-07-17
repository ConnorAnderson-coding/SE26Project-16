import axios from 'axios'

const TOKEN_KEY = 'campus-activity-token'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload.code === 'number') {
      if (payload.code === 0) {
        return payload.data
      }
      const error = new Error(payload.message || '请求失败')
      error.code = payload.code
      return Promise.reject(error)
    }
    return response.data
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem('campus-activity-user')
      if (window.location.pathname !== '/') {
        window.location.href = '/'
      }
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    const err = new Error(message)
    err.status = error.response?.status
    return Promise.reject(err)
  }
)

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredUser(user) {
  if (user) {
    localStorage.setItem('campus-activity-user', JSON.stringify(user))
  } else {
    localStorage.removeItem('campus-activity-user')
  }
}

export function getStoredUser() {
  try {
    const saved = localStorage.getItem('campus-activity-user')
    return saved ? JSON.parse(saved) : null
  } catch {
    return null
  }
}

export default http
