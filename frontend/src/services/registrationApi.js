import http from './http'

export function normalizeRegistration(item) {
  return {
    id: String(item.id),
    activityId: String(item.activityId),
    userId: item.userId,
    status: item.status,
    createdAt: item.createdAt,
    activityTitle: item.activityTitle,
    userName: item.userName,
    college: item.college,
    location: item.location,
    startTime: item.startTime
  }
}

export async function signup(activityId) {
  const data = await http.post('/registrations', { activityId: Number(activityId) })
  return normalizeRegistration(data)
}

export async function getMyRegistrations() {
  const data = await http.get('/registrations/mine')
  return data.map(normalizeRegistration)
}

export async function listRegistrations(activityId) {
  const params = activityId ? { activityId: Number(activityId) } : {}
  const data = await http.get('/registrations', { params })
  return data.map(normalizeRegistration)
}

export async function reviewRegistration(id, approved) {
  const data = await http.put(`/registrations/${id}/review`, { approved })
  return normalizeRegistration(data)
}

export async function getSignupStatus(activityId) {
  return http.get('/registrations/status', { params: { activityId: Number(activityId) } })
}
