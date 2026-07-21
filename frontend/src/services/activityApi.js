import http from './http'

export function normalizeActivity(activity) {
  if (!activity) return null
  return {
    ...activity,
    id: String(activity.id),
    organizerId: activity.organizerId,
    startTime: activity.startTime,
    endTime: activity.endTime
  }
}

export async function listActivities(params = {}) {
  const data = await http.get('/activities', { params })
  return {
    ...data,
    content: (data.content || []).map(normalizeActivity)
  }
}

export async function getAllActivities(params = {}) {
  const { sort, matchWeight, ...rest } = params
  const query = { page: 0, size: 200, ...rest }
  if (sort) {
    query.sort = sort
  }
  if (matchWeight != null && sort === 'composite') {
    query.matchWeight = matchWeight
  }
  const result = await listActivities(query)
  return result.content
}

export async function getRecommended(limit = 6) {
  const data = await http.get('/activities/recommended', { params: { limit } })
  return data.map(normalizeActivity)
}

export async function getActivityById(id) {
  return normalizeActivity(await http.get(`/activities/${id}`))
}

export async function getMyActivities() {
  const data = await http.get('/activities/mine')
  return data.map(normalizeActivity)
}

export async function createActivity(payload) {
  const data = await http.post('/activities', payload)
  return normalizeActivity(data)
}

export async function updateActivity(id, payload) {
  const data = await http.put(`/activities/${id}`, payload)
  return normalizeActivity(data)
}

export async function deleteActivity(id) {
  return http.delete(`/activities/${id}`)
}
