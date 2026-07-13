import http from './http'

export async function getActivityMetrics(activityId) {
  return http.get(`/analytics/activity/${activityId}/metrics`)
}

export async function getFullAnalysis(activityId) {
  return http.get(`/analytics/activity/${activityId}`)
}

export async function triggerAnalysis(activityId) {
  return http.post(`/analytics/trigger/${activityId}`)
}
