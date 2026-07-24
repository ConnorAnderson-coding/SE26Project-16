import http, { setStoredUser } from './http'

export async function getMe() {
  const user = await http.get('/users/me')
  setStoredUser(user)
  return user
}

export async function updateProfile(payload) {
  const user = await http.put('/users/me', payload)
  setStoredUser(user)
  return user
}
