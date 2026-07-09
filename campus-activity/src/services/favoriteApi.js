import http from './http'
import { normalizeActivity } from './activityApi'

export async function getMyFavorites() {
  const data = await http.get('/favorites')
  return data.map(normalizeActivity)
}

export async function toggleFavorite(activityId) {
  return http.post(`/favorites/${activityId}`)
}

export async function getFavoriteStatus(activityId) {
  return http.get(`/favorites/${activityId}/status`)
}
